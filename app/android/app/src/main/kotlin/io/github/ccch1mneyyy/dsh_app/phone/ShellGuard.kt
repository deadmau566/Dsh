package io.github.ccch1mneyyy.dsh_app.phone

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * 终端权限的三档。默认 [ORIGINAL]：安装完不给终端。
 *
 * - [ORIGINAL]：没有 shell 工具，和原版一致。
 * - [SAFE]：只跑用户勾选的命令；危险命令永久拒绝；脚本每次确认。
 * - [FULL]：任意命令。
 */
enum class ShellMode(val key: String) {
    ORIGINAL("original"),
    SAFE("safe"),
    FULL("full"),
    ;

    companion object {
        fun of(key: String?): ShellMode = values().firstOrNull { it.key == key } ?: ORIGINAL
    }
}

/** 一次 shell 调用该不该放行。 */
sealed class ShellVerdict {
    object Allow : ShellVerdict()
    class Deny(val reason: String) : ShellVerdict()
    class Confirm(val reason: String, val script: String?) : ShellVerdict()
}

/**
 * 进阶模式的白名单判定。只做静态检查：挡得住手滑，挡不住蓄意。
 */
object ShellGuard {

    /** 进阶模式永久禁止：一旦执行就不可挽回。 */
    val LOCKED = setOf(
        "dd", "mkfs", "mke2fs", "mkntfs", "mkdosfs", "fdisk", "parted", "sgdisk", "gdisk",
        "fastboot", "setenforce", "insmod", "rmmod", "modprobe", "wipe", "chroot",
        "pivot_root", "reboot", "shutdown", "halt",
    )

    /** 脚本执行类：能跑任意代码，进阶模式下每次确认。 */
    val SCRIPTS = setOf(
        "sh", "bash", "zsh", "dash", "ash", "python", "python3", "perl", "ruby",
        "node", "deno", "eval", "exec", "xargs", "source", ".",
    )

    /** 命中的路径一律拒绝。 */
    private val FORBIDDEN = listOf(
        "/system/", "/vendor/", "/product/", "/apex/", "/odm/",
        "/data/adb/", "/data/data/", "/data/system/", "/data/misc/", "/data/app/", "/data/vendor/",
        // 相册恒禁。`/sdcard/Android/obb/` 不在这里：OBB 是游戏的资源包，没必要恒禁，
        // 交给「允许读写整个 /sdcard」那个开关——关着时只认 Download，等同不可碰；开着才可读写。
        "/sdcard/DCIM/", "/storage/emulated/0/DCIM/",
    )

    /** App 自己的私有目录：只在进阶模式下放行，且要躲开 [FORBIDDEN] 里的 /data/data/。 */
    private val APP_PRIVATE = listOf(
        "/data/user/0/io.github.ccch1mneyyy.dsh_app/",
        "/data/data/io.github.ccch1mneyyy.dsh_app/",
    )

    /**
     * 敏感 App：进阶模式下它们的私有数据（`/data/user/0/<包>/`、`/data/data/<包>/`）仍当禁区，
     * 普通 App 的私有数据才放开。只按包名精确匹配（setOf 等值判断），不做前缀匹配——
     * `me.ele` 是 `me.ele.napos` 的前缀、`com.unionpay` 是 `com.unionpay.tsmservice.mi` 的
     * 前缀，前缀匹配会连带把别的包误伤成敏感包。
     *
     * 名单 = t6 抓取的 104 条候选 − 8 条系统组件（B2 的 PackageManager 规则已覆盖，点名重复）
     *      − 用户要求删掉的 4 个云盘（阿里云盘、百度网盘、中国移动云盘、123 云盘）= 92 条。
     * 系统组件不在这里点名：小米/华为/三星的系统包各不相同，点数不完，统一由 [systemOrUnknown]
     * 按 FLAG_SYSTEM 兜住。**例外**：`com.miui.password` 在本机是用户安装版（`pm list packages -3`
     * 有它、dumpsys flags 无 SYSTEM），规则兜不住，所以留在名单里。
     * 不区分是否已装：没装的装上以后自动落在禁区里，不用再改代码。
     */
    private val SENSITIVE_APPS = setOf(
        // —— 银行 ——
        "cmb.pb", // 招商银行
        "cn.com.cmbc.newmbank", // 民生银行
        "cn.com.shbank.mper", // 上海银行
        "cn.com.spdb.mobilebank.per", // 浦发银行
        "com.android.bankabc", // 农行掌上银行
        "com.bankcomm.Bankcomm", // 交通银行
        "com.bankofbeijing.mobilebanking", // 北京银行
        "com.cebbank.mobile.cemb", // 光大银行
        "com.cgbchina.xpt", // 广发银行
        "com.chinamworld.bocmbci", // 中国银行
        "com.chinamworld.main", // 中国建设银行
        "com.cib.cibmb", // 兴业银行
        "com.ecitic.bank.mobile", // 中信银行
        "com.hxb.mobile.client", // 华夏银行
        "com.icbc", // 中国工商银行
        "com.pingan.paces.ccms", // 平安口袋银行
        "com.pingan.paces.hk", // 平安银行香港
        "com.psbc.youenterprise", // 邮储企业银行
        "com.sxnx.mbank.android", // 山西农商银行
        "com.yitong.mbank.psbc", // 邮储银行
        // —— 支付 ——
        "cn.gov.pbc.dcep", // 数字人民币
        "com.chinatelecom.bestpayclient", // 翼支付
        "com.chinaums.onlineservice", // 银联商务
        "com.eg.android.AlipayGphone", // 支付宝
        "com.jd.jrapp", // 京东金融
        "com.mipay.wallet", // 小米钱包
        "com.paic.zhifu.wallet.activity", // 壹钱包
        "com.tencent.soter.soterserver", // 腾讯 SOTER 支付认证
        "com.unionpay", // 云闪付
        "com.wj.wallet", // 微博钱包
        "org.ifaa.aidl.manager", // IFAA 生物认证
        // —— 证券保险 ——
        "com.cpic.sxbxxe", // 太平洋保险
        "com.eastmoney.android.berlin", // 东方财富
        "com.eastmoney.android.fund", // 天天基金
        "com.ez08.compass", // 指南针股票
        "com.hexin.plat.android", // 同花顺
        "com.hundsun.winner.pazq", // 平安证券
        "com.licaimofang.app", // 理财魔方
        "com.nanfangjijin.app", // 南方基金
        "com.niuguwang.stock", // 牛股王股票
        "com.tencent.fortuneplat", // 腾讯理财通
        "com.zhongan.insurance", // 众安保险
        "com.zxscnew", // 中信证券
        // —— 电商 ——
        "com.dianping.v1", // 大众点评
        "com.jingdong.app.mall", // 京东
        "com.sankuai.meituan", // 美团
        "com.sankuai.meituan.takeoutnew", // 美团外卖
        "com.ss.android.ugc.livelite", // 抖音商城
        "com.taobao.idlefish", // 闲鱼
        "com.taobao.taobao", // 淘宝
        "com.xunmeng.pinduoduo", // 拼多多
        "me.ele", // 淘宝闪购
        "me.ele.napos", // 淘宝闪购商家版
        // —— 社交身份 ——
        "com.alibaba.android.rimet", // 钉钉
        "com.baidu.tieba", // 百度贴吧
        "com.duowan.kiwi", // 虎牙直播
        "com.hpbr.bosszhipin", // BOSS直聘
        "com.kuaishou.nebula", // 快手极速版
        "com.sina.weibo", // 微博
        "com.smile.gifmaker", // 快手
        "com.ss.android.ugc.aweme", // 抖音
        "com.tencent.mm", // 微信
        "com.tencent.mobileqq", // QQ
        "com.tencent.wework", // 企业微信
        "com.xingin.xhs", // 小红书
        "tv.danmaku.bili", // 哔哩哔哩
        // —— 运营商 ——
        "com.ai.obc.cbn.app", // 中国广电
        "com.ct.client", // 中国电信
        "com.greenpoint.android.mc10086.activity", // 中国移动
        "com.sinovatech.unicom.ui", // 中国联通
        // —— 政务证件 ——
        "cn.gov.tax.its", // 个人所得税
        "cn.hsa.app", // 国家医保服务平台
        "com.ctid.open", // 电子证件
        "com.fangjiehz.dzsfzgl", // 电子身份证管理
        "com.meinibuxing.dzsfzgj", // 电子身份证管家
        "com.service.android.gov.cn", // 国家政务服务平台
        "com.tmri.app.main", // 交管12123
        "com.wondersgroup.ybtproduct", // 江苏医保云
        "com.ywsocial.sbdz", // 社保电子医保管家
        // —— 凭证隐私 ——
        "cc.oasis.minor1", // 快帆
        "com.fido.asm", // FIDO 认证器
        "com.ingeek.fawcar.digitalkey", // 奔腾数字钥匙
        "com.miui.password", // 小米密码
        "com.oray.pgymanager", // 贝锐蒲公英管理
        "com.snda.wifilocating", // WiFi万能钥匙
        "com.wapi.wapicertmanage", // WAPI 证书管理
        "com.wicarlink.digitalcarkey", // 51数字钥匙
        "com.xcbf.xg", // 相册备份
        "com.xianshe.secret", // 密码管理器
        "org.ikuuu.vpn", // iKuuu VPN
        // —— 健康医疗 ——
        "com.ihaozhuo.youjiankang", // 优健康
        "com.pa.health", // 平安乐健康
    )

    /** 合法包名：至少两段、每段字母开头、只含字母数字下划线。
     *  通配符（`*` `?` `[` `]`）、`$VAR`、`..`、裸词都在这一步被挡掉，不许它们冒充「普通 App」。 */
    private val PKG_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

    /**
     * PackageManager 由 PhoneControl 初始化时注入（`ShellGuard.setPackageManager(app.packageManager)`）。
     * 没注入时 `systemOrUnknown` 一律返回 true，也就是所有 App 私有数据都不放行——
     * 失败方向必须安全：宁可退回「整块 /data 禁区」，也不能在不知道是不是系统应用时放开。
     */
    @Volatile
    private var packageManager: PackageManager? = null

    /** 包名 → 是否系统应用。判一次缓存一次；进程里装/卸应用后重新注入会清空。 */
    private val systemAppCache = HashMap<String, Boolean>()

    fun setPackageManager(pm: PackageManager?) {
        packageManager = pm
        synchronized(systemAppCache) { systemAppCache.clear() }
    }

    /**
     * 系统应用（含「系统应用的更新版」）或查不到的包 → true（不许碰它的数据）。
     * 不能用 uid 判：`com.android.providers.contacts` 的属主实测是 u0_a87，和普通 App 一样。
     */
    private fun systemOrUnknown(pkg: String): Boolean {
        val pm = packageManager ?: return true
        synchronized(systemAppCache) { systemAppCache[pkg]?.let { return it } }
        val sys = try {
            val info = pm.getApplicationInfo(pkg, 0)
            (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            true
        } catch (_: Exception) {
            true
        }
        synchronized(systemAppCache) {
            if (systemAppCache.size < 4096) systemAppCache[pkg] = sys
        }
        return sys
    }

    /** 普通 App 的私有数据是否可碰：非敏感、非系统应用、且确实装着。 */
    private fun appDataOpen(pkg: String): Boolean = pkg !in SENSITIVE_APPS && !systemOrUnknown(pkg)

    /** 永远可写的几个位置。 */
    private val BASE_ROOTS = listOf(
        "/sdcard/Download/", "/storage/emulated/0/Download/",
        "/data/local/tmp/",
    ) + APP_PRIVATE

    /** 用户单独确认过「整个 /sdcard」之后才能写的位置。 */
    private val SDCARD_ROOTS = listOf("/sdcard/", "/storage/emulated/0/")

    /** 会改文件的命令：目标必须落在 [BASE_ROOTS]（或开了开关后的 [SDCARD_ROOTS]）。 */
    /** 命令替换：$( )、${ }、反引号。能把任意命令藏进参数。 */
    private val SUBST = Regex("""\$\(|\$\{|`""")

    private val WRITE_CMDS = setOf(
        "rm", "mv", "cp", "mkdir", "rmdir", "touch", "chmod", "chown", "ln", "tee", "truncate",
        "sed", "awk",
    )

    /**
     * 只是「换个方式跑后面的命令」的包装器。它们自己的名字看不出危险，
     * 真正要执行的是后面的串，所以 judge 必须拆壳后对剩余部分递归重判。
     */
    private val WRAPPERS = setOf("env", "command", "nohup", "time", "nice", "setsid", "stdbuf")

    /**
     * 相册里的路径只允许这些命令碰：它们没有「把内容写到指定文件」的参数。
     * 只读组里 `sort -o`、`logcat -f`、`curl -o` 都能写文件，所以按命令白名单，
     * 不按「只读组」——名单外的一律当「可能写」处理。
     * 相册在进阶模式下是**纯只读**：连复制出去都不许（要复制请切高危）。
     */
    val PHOTO_READ = setOf(
        "ls", "cat", "head", "tail", "grep", "find", "file", "stat",
        "du", "wc", "cut", "tr", "uniq",
    )

    /** 相册/视频目录：可读可写，但不可删、不可覆盖已有文件。 */
    private val PHOTO_DIRS = listOf(
        "/sdcard/DCIM/", "/storage/emulated/0/DCIM/",
        "/sdcard/Pictures/", "/storage/emulated/0/Pictures/",
        "/sdcard/Movies/", "/storage/emulated/0/Movies/",
        "/sdcard/Android/media/", "/storage/emulated/0/Android/media/",
    )

    fun judge(
        mode: ShellMode,
        command: String,
        allowed: Set<String>,
        sdcardOn: Boolean = false,
        depth: Int = 0,
    ): ShellVerdict {
        val cmd = command.trim()
        if (cmd.isEmpty()) return ShellVerdict.Deny("命令是空的。")

        // 包装器拆壳会递归回来，套娃（env env env …）不能无限展开。
        if (depth > 8) {
            return ShellVerdict.Deny("命令套了太多层包装（env/command/nohup 之类），进阶模式下不接受。")
        }

        // 高危：命令、路径全放开。用户已经明确选了这个档位。
        if (mode == ShellMode.FULL) return ShellVerdict.Allow

        // 命令替换能把任意命令藏进参数里（$(pm uninstall ...)），这里一律不接受。
        // 正经用法用不到它；确实要跑脚本，请切到高危模式。
        if (SUBST.containsMatchIn(cmd)) {
            return ShellVerdict.Deny(
                "命令里有 \$(...)、\${...} 或反引号——它们能把别的命令藏起来绕开检查，进阶模式下不允许。要跑这种命令请切到高危模式。",
            )
        }

        for (seg in split(cmd)) {
            // 先把引号拼接的路径拼回成真实路径再检查：`rm -f /dat"a/ds"` 执行时是
            // `rm -f /data/ds`，守卫若只查原文就永远看不见。
            val eff = unquote(seg)
            // 变量和反引号能把路径藏起来（守卫和拼接都扫不到），进阶模式直接拒。
            if (seg.contains('$') || seg.contains('`')) {
                return ShellVerdict.Deny(
                    "命令里不能用 $ 变量或反引号——它们会把路径藏起来，进阶模式不允许。",
                )
            }
            // 取不到命令名（`(`、`{`、`!` 开头的裸段）就拒：以前这里 continue，
            // 等于整段全部放行——括号法就是这么把 /data 删掉的。
            val word = firstWord(seg) ?: return ShellVerdict.Deny(
                "这条命令的开头不是一个可识别的命令（可能有括号或特殊符号），进阶模式不接受。",
            )

            // 整组锁死：不管 allowed 里有没有，进阶模式一律拒绝。
            val group = ShellCommands.groupOf(word)
            if (group != null && group in ShellCommands.SAFE_LOCKED_GROUPS) {
                return ShellVerdict.Deny(
                    "「$word」属于「${ShellCommands.groupTitle(group)}」，进阶模式下整组锁死，切到高危模式才能用。",
                )
            }
            if (word in LOCKED) {
                return ShellVerdict.Deny(
                    "「$word」可能损坏分区或系统，进阶模式下不可用。要跑它请切到高危模式。",
                )
            }
            if (word !in allowed) {
                return ShellVerdict.Deny(
                    "「$word」没有在当前档位开启。去 设置 → 终端权限 里的命令清单把它打开。",
                )
            }

            // 包装器拆壳：env / command / nohup / time / nice / setsid / stdbuf 只是
            // 把后面的命令换个方式跑。以前只按首词过检查，内层命令完全没判：
            // `env reboot`、`env dd if=/dev/zero of=/dev/block/by-name/boot`、
            // `env sh -c 'reboot'`、`env setenforce 0` 全被放行（裸写都是 DENY）。
            // 这里把壳和选项、`VAR=val` 前缀剥掉，对剩下的命令整段递归重判一次；
            // 用拼回引号后的 seg，`env rm -f /dat"a/ds"` 才会进到 rm 再判路径。
            if (word in WRAPPERS) {
                val inner = unwrapWrapper(seg, word)
                    ?: return ShellVerdict.Deny("「$word」后面没有可识别的命令，进阶模式下不接受。")
                val innerVerdict = judge(mode, inner, allowed, sdcardOn, depth + 1)
                if (innerVerdict !is ShellVerdict.Allow) return innerVerdict
            }

            // 相对跳转（`..`）对所有命令都拦：读类命令同样能用
            // `cd /data/local/tmp && cat ../../user/0/<敏感包>/databases/x` 把数据读走，
            // 不能只在写类命令里查。写在公共检查里，写类命令那段的同名检查保留也无妨。
            if (hasDotDot(eff)) {
                return ShellVerdict.Deny(
                    "「$word」的参数里有 `..`，进阶模式下不许用相对路径跳到别的目录。",
                )
            }

            // `cd` 会改工作目录：只许进安全目录，否则 `cd 相册 && rm *` 能用相对路径
            // 绕过全部保护（pathArgs 只抓绝对路径，相对路径根本进不了检查）。
            if (word == "cd") {
                val dest = pathArgs(eff).firstOrNull()
                if (dest != null) {
                    // 相册/视频目录：即使开了 sdcard 也不许 cd 进去，
                    // 否则 `cd 相册 && rm *` 用相对路径就绕过了照片保护。
                    if (inPhoto(dest)) {
                        return ShellVerdict.Deny("「cd」不能进相册/视频目录（$dest）。")
                    }
                    if (!inSafeRoot(dest, sdcardOn)) {
                        return ShellVerdict.Deny(
                            "「cd」只能进 Download、/data/local/tmp 或 App 自己的私有目录（dsh_app），$dest 不行。",
                        )
                    }
                }
            }

            val paths = pathArgs(eff)

            // 相册路径 + 名单外的命令 → 一律拒。这条挡的是 `sort -o 相册/x`、
            // `logcat -f 相册/x`、`curl -o 相册/x` 这类「只读组里能写」的命令。
            val photoArg = paths.firstOrNull { inPhoto(it) }
            if (photoArg != null && word !in PHOTO_READ) {
                return ShellVerdict.Deny(
                    "「$word」的参数里有相册里的路径（$photoArg）。进阶模式下相册只给读，" +
                        "只允许 " + PHOTO_READ.joinToString("、") +
                        " 这些命令。能写文件的命令（sort -o、logcat -f、curl -o 之类）一律不许。",
                )
            }
            for (p in paths) {
                forbidden(p, sdcardOn)?.let { hit ->
                    return ShellVerdict.Deny("碰到受保护的位置：$hit，进阶模式下不允许。")
                }
                if (!sdcardOn && (p.startsWith("/sdcard/") || p.startsWith("/storage/emulated/0/")) &&
                    !inSafeRoot(p, false)
                ) {
                    return ShellVerdict.Deny(
                        "「$word」碰到了 $p。要让 AI 读写整个 /sdcard，去 设置 → 终端权限 → 额外设置 里打开「允许读写整个 /sdcard」。",
                    )
                }
            }

            // 重定向目标也是一次写入：`echo x > /path` 的 /path 必须过同样的检查，
            // 否则它能把相册覆盖保护、sdcard 开关全绕过去。
            for (p in redirectTargets(eff)) {
                if (!p.startsWith("/")) continue
                if (!inSafeRoot(p, sdcardOn, allowAppData = true)) {
                    return ShellVerdict.Deny(outOfScope(word, p, sdcardOn))
                }
                if (inPhoto(p)) {
                    return ShellVerdict.Deny(
                        "相册是只读的（$p）。进阶模式下只能看和复制出去，不能往里写、也不能删。",
                    )
                }
            }

            if (word in WRITE_CMDS) {
                // `cd /sdcard/Download && rm ../Pictures/x` 这种相对跳转能绕过路径检查
                // （pathArgs 只看绝对路径），写类命令一律不许带 `..`。
                if (hasDotDot(eff)) {
                    return ShellVerdict.Deny(
                        "「$word」的参数里有 `..`，进阶模式不支持用相对路径跳到别的目录。",
                    )
                }
                // `cp -t 目录 源` 里 -t 后面那串才是目标，其余全是源；
                // 按普通「最后一个参数是目标」处理会把 -t 的目录误判成源。
                val hasTargetFlag = Regex("""(^|\s)-t(\s|$)""").containsMatchIn(eff)
                val isMove = !hasTargetFlag && (word == "cp" || word == "mv" || word == "ln")
                val targets = if (isMove) paths.takeLast(1) else paths
                // 源也要查：`mv 相册/x Download/` 不看源就能把照片搬出相册，
                // 搬出去等于从相册消失了——和删掉一个效果，必须拦在源上。
                // 源在相册一律拒：`mv` 是搬走，`cp` 是复制出去，进阶模式两个都不许。
                if (isMove && paths.size > 1) {
                    for (src in paths.dropLast(1)) {
                        if (inPhoto(src)) {
                            return ShellVerdict.Deny(
                                "「$src」在相册/视频目录里，搬出去就等于从相册消失了，进阶模式下不允许。要动它请切到高危模式。",
                            )
                        }
                    }
                }
                for (p in targets) {
                    if (!p.startsWith("/")) {
                        return ShellVerdict.Deny("「$word」的目标要写绝对路径，$p 不是。")
                    }
                    if (!inSafeRoot(p, sdcardOn, allowAppData = true)) {
                        return ShellVerdict.Deny(outOfScope(word, p, sdcardOn))
                    }
                    // `rm -rf /data/user/0/<包>`（目标正好是数据根）= 被 Deny 的 `pm clear`：
                    // 不可逆、而且手滑就能达到，所以和 `pm clear` 同口径给一次确认。
                    // 只挡「根部」这一种，`rm -rf <包>/cache` 这类正常清理不受影响。
                    if (word == "rm" || word == "rmdir") {
                        appDataRootPackage(slashNorm(p))?.let { pkg ->
                            return ShellVerdict.Confirm(
                                "「$word」的目标正好是 ${pkg} 的数据根目录（$p）。这等于 pm clear，" +
                                    "会不可逆地清掉它的全部数据（聊天记录、登录态、本地库都在里面）。" +
                                    "真要清就确认这次执行；只想清缓存，把范围写小一点，比如 /data/user/0/$pkg/cache。",
                                null,
                            )
                        }
                    }
                    if (inPhoto(p)) {
                        return ShellVerdict.Deny(
                            "相册是只读的（$p）。进阶模式下只能看和复制出去，不能往里写、也不能删。",
                        )
                    }
                }
            }
            // awk 的 system() 是「参数即代码」：`awk 'BEGIN{system("rm -rf …")}'` 里
            // 没有段、没有绝对路径、也不经过 $/反引号检查，白名单形同不存在。
            if (word == "awk" && Regex("""\bsystem\s*\(|\bgetline\b|\|\s*getline""").containsMatchIn(eff)) {
                return ShellVerdict.Deny("awk 的 system()/getline 等于执行任意命令，进阶模式下不可用。")
            }
            if (word == "sed" && Regex("""(^|[\s'\"])e(\s|$)""").containsMatchIn(eff)) {
                return ShellVerdict.Deny("sed 的 e 命令会执行参数里的内容，进阶模式下不可用。")
            }
            if (word == "find" && Regex("""\s-(delete|exec|execdir|ok|okdir)\b""").containsMatchIn(seg)) {
                return ShellVerdict.Deny("find 带 -delete/-exec 等于执行任意操作，进阶模式下不可用。")
            }
            if (word == "pm" && Regex("""\bpm\s+clear\b""").containsMatchIn(seg)) {
                return ShellVerdict.Deny("pm clear 会清掉一个 App 的全部数据，进阶模式下不可用。")
            }
            // `cmd package clear|uninstall|install|delete|purge` 和 pm clear / pm uninstall
            // 是同一件事，但 `cmd` 属于 system 组、以前默认开，这里只认 `pm`，
            // `cmd package clear com.tencent.mm` 就直接放行，等于绕过上面的检查。
            // 现在 cmd 已移进 SAFE_OFF，这条是双保险：用户手动打开 cmd 之后，
            // 改动/卸载/清数据的子命令仍然拒；`cmd package list`、`cmd wifi status` 不受影响。
            if (word == "cmd") {
                // 装/卸/清数据之外，「禁用/启用/挂起/隐藏/清缓存/删用户」同样能停掉一个 App
                // （含银行、微信），效果和 disable 一样；查询类 list/path/dump 不受影响。
                val pkgSub = Regex(
                    """\bpackage\s+(clear|uninstall(?:-system-updates)?|install(?:-existing)?|delete|purge|disable|enable|set-enabled|set-hidden|suspend|unsuspend|hide|unhide|trim-caches|remove-user)\b""",
                ).find(eff)
                if (pkgSub != null) {
                    return ShellVerdict.Deny(
                        "「cmd package ${pkgSub.groupValues[1]}」会改动/停掉一个 App，进阶模式下不可用。",
                    )
                }
            }
        }
        return ShellVerdict.Allow
    }

    /** 按 `;` `&&` `||` `|` 拆段；每段都要单独过白名单。 */
    private fun split(cmd: String): List<String> =
        cmd.split(Regex("""(?:\|\||&&|;|\|(?!\|)|\r?\n)"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * 把 shell 的引号拼接拼回去：`/dat"a/ds"` → `/data/ds`、`'/data'/ds` → `/data/ds`。
     * 引号只是分组标记，不参与路径——守卫按字面查看到的是两段，永远差一层。
     * 只处理字面拼接，不做变量展开（变量由「段里含 $ 或反引号就拒」兜住）。
     */
    private fun unquote(s: String): String {
        val out = StringBuilder(s.length)
        var quote = ' '
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quote == ' ' && (c == '"' || c == '\'') -> quote = c
                quote != ' ' && c == quote -> quote = ' '
                c == '\\' && i + 1 < s.length -> { i++; out.append(s[i]) }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** 段首词，去掉路径和引号：`/system/bin/ls -la` → `ls`。 */
    private fun firstWord(seg: String): String? {
        // 段首的 `(`、`{`、`!` 必须先剥掉再取词。否则首词取不到，
        // judge 里 `?: continue` 会跳过整段——命令白名单、LOCKED、脚本组、
        // 全部路径检查一起失效，`(rm -rf /data/system)` 这种就裸跑。
        val s = seg.trimStart(' ', '\t', '(', '{', '!')
        val m = Regex("""^[^\s;&|()<>`]+""").find(s) ?: return null
        val w = m.value.substringAfterLast('/').trim('\'', '"').lowercase()
        return w.ifEmpty { null }
    }

    /**
     * 拆掉包装器，返回真正要执行的命令串：
     * `env -i FOO=1 rm -f /x` → `rm -f /x`、`nice -n 5 rm x` → `rm x`。
     * 剥掉的东西：首词、`--`、包装器选项（`-i`、`-oL`、`-n 5` 这类，带值的选项一起跳）、
     * env 的 `VAR=val` 赋值。剩下的原样交给 judge 递归重判。
     * 剥完什么都不剩（`env`、`nohup`）返回 null → 上层直接拒。
     */
    private fun unwrapWrapper(seg: String, word: String): String? {
        val s = seg.trimStart(' ', '\t', '(', '{', '!')
        val head = Regex("""^[^\s;&|()<>`]+""").find(s) ?: return null
        val toks = s.substring(head.value.length).trimStart()
            .split(Regex("""\s+""")).filter { it.isNotEmpty() }
        var i = 0
        var stop = false
        while (i < toks.size && !stop) {
            val t = toks[i]
            when {
                t == "--" -> i++
                // `-n 5`（nice）、`-i0`/`-oL`（stdbuf）这类带值的选项要连同值一起跳过。
                t.startsWith("-") -> i += if (Regex("""^-[nioe]$""").matches(t)) 2 else 1
                word == "env" && Regex("""^[A-Za-z_][A-Za-z0-9_]*=""").containsMatchIn(t) -> i++
                else -> stop = true
            }
        }
        if (i >= toks.size) return null
        return toks.drop(i).joinToString(" ")
    }

    /**
     * `..` 只按路径词判定：`a..b`、`v1..2.txt` 这类文件名里的两个点不是上级跳转，
     * 而 `..`、`../x`、`x/../y`、`x/..` 才是。
     */
    private fun hasDotDot(seg: String): Boolean = seg.split(Regex("""\s+""")).any { raw ->
        val w = raw.trim('\'', '"')
        w == ".." || w.startsWith("../") || w.endsWith("/..") || w.contains("/../")
    }

    /** 系统分区 / 别的 App 数据：不管开没开 sdcard 都不许碰。 */
    private fun forbidden(p: String, sdcardOn: Boolean = false): String? {
        // 先折掉 `..` 和 `//` 再判断：/data/local/tmp/../deepseek 实际是 /data/deepseek，
        // 只看字符串开头会被骗过去。
        val real = try {
            java.nio.file.Paths.get(p).normalize().toString()
        } catch (e: Exception) {
            p
        }
        val withSlash = if (real.endsWith("/")) real else "$real/"
        // App 自己的私有目录永远放行（FORBIDDEN 里的 /data/data/ 不能一刀切到它头上）。
        if (APP_PRIVATE.any { withSlash.startsWith(it) }) return null
        // /data/local/tmp 是约定的临时目录，也放行。
        if (withSlash.startsWith("/data/local/tmp/")) return null
        // 普通 App 的私有数据在进阶模式下放开读写：`/data/user/0/<包>/` 和它的符号链接
        // `/data/data/<包>/`。敏感 App 仍当禁区，提示里带上包名，前端就能和普通禁区区分开。
        // 只放开这一个包名的目录树：`/data/user/0` 本身、`/data/user/1/`、`/data/user_de/`、
        // `/data/app`、`/data/system` 之类继续落到下面的 /data 整块禁区。
        appDataPackage(withSlash)?.let { pkg ->
            if (pkg in SENSITIVE_APPS) return "敏感应用数据（${pkg}）"
            // 系统应用的数据不放开：短信库、系统设置库、keychain 都在这些包下面。
            // 包名不合法或查不到包时 appDataPackage 已返回 null，直接落回下面的 /data 整块禁区。
            if (systemOrUnknown(pkg)) return "系统应用的数据（${pkg}）"
            return null
        }
        // /data 根目录整体当禁区：只留上面两个已知位置。
        // 之前只列了 /data/adb、/data/data 这些子目录，根目录下放的文件反而漏过去了
        //（拦了房间没拦走廊）。/sdcard 是 /storage/emulated/0 的另一个名字，
        // 不会落到这里，所以不影响 Download。
        if (withSlash.startsWith("/data/")) return p
        for (f in FORBIDDEN) {
            if (withSlash != f && !withSlash.startsWith(f)) continue
            // 开了「允许读写 sdcard」后，/sdcard 下的目录交给 inSafeRoot + 相册规则判断，
            // 不再在这里一刀切。
            if (sdcardOn && (f.startsWith("/sdcard/") || f.startsWith("/storage/emulated/0/"))) continue
            return p
        }
        return null
    }

    /**
     * 从 normalize 之后的路径里取 `/data/user/0/<包>/…` 或 `/data/data/<包>/…` 的包名。
     * 形状不对返回 null：`/data/user/0/` 本身、`/data/user/1/…`、`/data/user_de/0/…`、
     * `/data/app/…` 都不算，继续走 [forbidden] 的 /data 整块禁区。
     * `/data/data/<包>` 是 `/data/user/0/<包>` 的符号链接，两个名字都得认。
     */
    private fun appDataPackage(withSlash: String): String? {
        val prefix = when {
            withSlash.startsWith("/data/user/0/") -> "/data/user/0/"
            withSlash.startsWith("/data/data/") -> "/data/data/"
            else -> return null
        }
        val rest = withSlash.substring(prefix.length)
        val pkg = rest.substringBefore('/')
        // 不校验的话 `/data/user/0/<任意>/shared_prefs/x.xml`、`/data/user/0/com.*/`、`$PKG` 都会被
        // 当成「普通 App」放行，黑名单等于没有。形状不合法就返回 null，落回下面的 /data 整块禁区。
        if (pkg.isEmpty() || pkg.length > 200 || !PKG_NAME.matches(pkg)) return null
        return pkg
    }

    /**
     * 路径正好是某个 App 的数据根目录或它的全部内容
     * （`/data/user/0/<包>`、`/data/data/<包>`、`<包> 下的全部内容`，末尾可有 `/`）时返回包名。
     * 这是 `rm -rf` 最危险的形态：等于被 Deny 的 `pm clear`，不可逆。
     * `rm -rf <包>` 和 `rm -rf <包> 下的全部内容` 都要确认，只有 `<包>/cache` 这类子目录才放行。
     */
    private fun appDataRootPackage(withSlash: String): String? {
        val pkg = appDataPackage(withSlash) ?: return null
        val rest = when {
            withSlash.startsWith("/data/user/0/") -> withSlash.substring("/data/user/0/".length)
            withSlash.startsWith("/data/data/") -> withSlash.substring("/data/data/".length)
            else -> return null
        }
        return if (rest == "$pkg/" || rest == "$pkg/*" || rest == "$pkg/*/" ||
            rest == "$pkg/.*" || rest == "$pkg/.*/" ||
            rest == "$pkg/{*,.*}" || rest == "$pkg/{*,.*}/"
        ) pkg else null
    }

    /** normalize 后的路径统一带一个结尾 `/`：判断包名段和「数据根」时都按这个形状比。 */
    private fun slashNorm(p: String): String {
        val real = try {
            java.nio.file.Paths.get(p).normalize().toString()
        } catch (_: Exception) {
            p
        }
        return if (real.endsWith("/")) real else "$real/"
    }

    /** 参数里的绝对路径，跳过重定向符号（`2>&1`、`>`、`>>`）。 */
    /** `> path`、`>> path`、`>/path` 的目标（相对路径交给调用方跳过）。 */
    private fun redirectTargets(seg: String): List<String> =
        Regex("""(?:^|[^>])>>?\s*([^\s;&|]+)""").findAll(seg)
            .map { it.groupValues[1].trim('\'', '"') }
            .toList()

    private fun pathArgs(seg: String): List<String> =
        // 前导可以是空白、`>`、引号、`=`、`(`：`rm -rf "/data/system/x"`、
        // `--opt="/path"` 这类写法里的绝对路径也必须被抓到，否则引号就是绕过全部检查的后门。
        Regex("""(?:^|[\s>'"=(])(/[^\s'"`()|;&<>]+)""").findAll(seg)
            .map { it.groupValues[1] }
            .toList()

    /**
     * 规范化后是否落在允许写的位置。[sdcardOn] = 用户是否确认过「整个 /sdcard」，
     * [allowAppData] = 是否放开普通 App 的私有数据（写类命令和重定向传 true；系统应用与敏感包仍不放）。
     */
    private fun inSafeRoot(p: String, sdcardOn: Boolean, allowAppData: Boolean = false): Boolean {
        val norm = try {
            java.nio.file.Paths.get(p).normalize().toString()
        } catch (_: Exception) {
            return false
        }
        // 黑洞设备：写进去什么都不会发生，`2>/dev/null` 是最常见的写法，放行。
        if (norm == "/dev/null") return true
        val withSlash = if (norm.endsWith("/")) norm else "$norm/"
        if (sdcardOn && SDCARD_ROOTS.any { withSlash.startsWith(it) }) return true
        // 写类命令/重定向的目标同样要覆盖普通 App 的私有目录：`cp x /data/user/0/<包>/files/`。
        // 敏感 App 与 /data 其他目录不在此列——这里只是多一个白名单分支，不放宽别处。
        // `cd` 不传 allowAppData：否则 `cd /data/user/0/<包>` + `rm *` 用相对路径就能绕过检查。
        if (allowAppData) {
            appDataPackage(withSlash)?.let { pkg -> if (appDataOpen(pkg)) return true }
        }
        return BASE_ROOTS.any { withSlash.startsWith(it) }
    }

    /** 是否在相册/视频目录里。 */
    private fun inPhoto(p: String): Boolean {
        val norm = try {
            java.nio.file.Paths.get(p).normalize().toString()
        } catch (_: Exception) {
            return false
        }
        val withSlash = if (norm.endsWith("/")) norm else "$norm/"
        return PHOTO_DIRS.any { withSlash.startsWith(it) }
    }

    /** 拒绝时说清「去哪开」。 */
    private fun outOfScope(word: String, p: String, sdcardOn: Boolean): String {
        // 入口先归一化一次：`/data/user/0/./com.tencent.mm/x`、`/data/data//com.x/` 这类写法
        // 直接拿原串切包名会切歪，提示就退化成「不在允许范围」的通用文案。
        val withSlash = slashNorm(p)
        // 相册要排在 sdcard 前面：它也在 /sdcard 下，但开了那个开关也不许写，
        // 提示成「去开开关」只会让人白点一次。
        if (inPhoto(withSlash)) {
            return "「$word」碰到的是相册/视频目录（$p）。进阶模式下相册是只读的：" +
                "「允许读写整个 /sdcard」开不开都一样，只能看和复制出去，不能写、不能删、不能覆盖。" +
                "要动它请切到高危模式。"
        }
        // 敏感 App 的数据目录：写类命令/重定向走到这里时点明是黑名单挡的，
        // 别让人读成「位置不在允许范围」而去乱调设置。
        appDataPackage(withSlash)?.let { pkg ->
            if (pkg in SENSITIVE_APPS) {
                return "「$word」要动的是敏感应用（$pkg）的数据：$withSlash。" +
                    "进阶模式下黑名单里的 App 私有数据一律不碰，要动它请切到高危模式。"
            }
        }
        if (!sdcardOn && (p.startsWith("/sdcard") || p.startsWith("/storage/emulated/0"))) {
            return "「$word」只能改 Download 和 /data/local/tmp，这一条碰到了 $p。" +
                "要让 AI 读写整个 /sdcard，去 设置 → 终端权限 → 额外设置 里打开「允许读写整个 /sdcard」。"
        }
        return "「$word」碰到了一个不在允许范围内的位置：$p。"
    }
}

/** 命令清单：设置页按组展示，默认值也在这里。 */
object ShellCommands {
    /** 分组顺序 + 中文标题 + 一句话说明。 */
    val GROUPS: List<Map<String, String>> = listOf(
        linkedMapOf("id" to "read", "title" to "只读命令", "desc" to "只看不改：读文件、看进程和系统状态"),
        linkedMapOf("id" to "file", "title" to "文件操作", "desc" to "复制、移动、删除；只能碰 Download、tmp、App 私有目录（开 /sdcard 后含整个存储）"),
        linkedMapOf("id" to "system", "title" to "系统操作", "desc" to "安卓层面的开关：装应用、模拟点击、改设置"),
        linkedMapOf("id" to "network", "title" to "网络", "desc" to "联网请求、测速、查 DNS"),
        linkedMapOf("id" to "packages", "title" to "包管理", "desc" to "apt / pip / npm / git，第一次用要联网下载"),
        linkedMapOf("id" to "script", "title" to "脚本执行", "desc" to "把整段脚本交给解释器：内容不受白名单约束"),
    )

    /** 命令 → (分组, 一句话介绍)。 */
    private val DEF: List<Triple<String, String, String>> = listOf(
        Triple("cd", "read", "切当前目录（只在同一条命令里有效）"),
        Triple("pwd", "read", "打印当前目录"),
        Triple("echo", "read", "打印一段文字"),
        Triple("ls", "read", "列出目录内容"),
        Triple("cat", "read", "输出文件内容"),
        Triple("head", "read", "看文件开头几行"),
        Triple("tail", "read", "看文件结尾几行"),
        Triple("grep", "read", "在文件或输出里搜文字"),
        Triple("wc", "read", "数行数、字数"),
        Triple("sort", "system", "排序（带 -o 会写文件）"),
        Triple("uniq", "read", "去重"),
        Triple("cut", "read", "按列截取"),
        Triple("find", "read", "按名字/时间找文件"),
        Triple("stat", "read", "看文件大小、权限、时间"),
        Triple("file", "read", "判断文件是什么类型"),
        Triple("du", "read", "看目录占多少空间"),
        Triple("df", "read", "看各分区剩余空间"),
        Triple("free", "read", "看内存占用"),
        Triple("ps", "read", "看正在跑的进程"),
        Triple("top", "read", "看谁在吃 CPU"),
        Triple("uptime", "read", "开机多久、负载多少"),
        Triple("id", "read", "看当前用户和权限"),
        Triple("whoami", "read", "看当前用户名"),
        Triple("getprop", "read", "读系统属性：型号、Android 版本"),
        Triple("dumpsys", "read", "读系统服务状态：电池、显示、WiFi"),
        Triple("logcat", "system", "读系统日志（带 -f 会写文件）"),
        Triple("screencap", "read", "截屏存成图片"),
        Triple("date", "system", "看时间（带 -s 会改系统时间）"),
        Triple("echo", "read", "输出一段文字"),
        Triple("printf", "read", "格式化输出文字"),
        Triple("pwd", "read", "显示当前目录"),
        Triple("env", "read", "看环境变量"),
        Triple("uname", "read", "看内核版本"),
        Triple("nproc", "read", "看有几个 CPU 核"),
        Triple("which", "read", "查命令装在哪"),
        Triple("basename", "read", "取路径里的文件名"),
        Triple("dirname", "read", "取路径里的目录部分"),
        Triple("realpath", "read", "把相对路径转成绝对路径"),
        Triple("tr", "read", "替换或删除字符"),
        Triple("cp", "file", "复制文件"),
        Triple("mv", "file", "移动或改名"),
        Triple("rm", "file", "删除文件"),
        Triple("mkdir", "file", "新建目录"),
        Triple("rmdir", "file", "删除空目录"),
        Triple("touch", "file", "新建空文件或改时间"),
        Triple("chmod", "file", "改文件权限"),
        Triple("chown", "file", "改文件属主"),
        Triple("ln", "file", "建快捷方式（软链接）"),
        Triple("tee", "file", "把输出同时写进文件"),
        Triple("truncate", "file", "把文件截断或撑大"),
        Triple("sed", "file", "按规则替换文件内容"),
        Triple("awk", "file", "按列处理文本"),
        Triple("am", "system", "启动应用、发广播"),
        Triple("pm", "packages", "装/卸应用、查包名"),
        Triple("input", "system", "模拟点屏幕、打字"),
        Triple("cmd", "system", "调系统命令（WiFi、电池等）"),
        Triple("settings", "system", "读改系统设置项"),
        Triple("svc", "system", "开关 WiFi、数据、飞行模式"),
        Triple("wm", "system", "改屏幕分辨率、密度"),
        Triple("curl", "network", "发 HTTP 请求"),
        Triple("ping", "network", "测网络通不通"),
        Triple("nslookup", "network", "查域名对应 IP"),
        Triple("netstat", "network", "看网络连接"),
        Triple("apt", "packages", "装系统软件包"),
        Triple("apt-get", "packages", "装系统软件包（老写法）"),
        Triple("pip", "packages", "装 Python 包"),
        Triple("pip3", "packages", "装 Python 3 包"),
        Triple("npm", "packages", "装 Node 包"),
        Triple("pnpm", "packages", "装 Node 包（更快）"),
        Triple("git", "packages", "拉代码、提交"),
        Triple("sh", "script", "跑一段 shell 脚本"),
        Triple("bash", "script", "跑一段 bash 脚本"),
        Triple("zsh", "script", "跑一段 zsh 脚本"),
        Triple("python", "script", "跑 Python 代码"),
        Triple("python3", "script", "跑 Python 3 代码"),
        Triple("node", "script", "跑 JavaScript 代码"),
        Triple("eval", "script", "把字符串当命令执行"),
        Triple("exec", "script", "替换成另一个进程执行"),
        Triple("xargs", "script", "把输入拼成命令批量执行"),
        Triple("source", "script", "加载脚本文件"),
    )

    /** 命令清单的版本：默认值或组别变一次就 +1（见 PhoneControl.allowedCommands 的重置逻辑）。 */
    const val CMDS_REV: Int = 3

    /** 设置页用的扁平清单，每项带分组和介绍。 */
    val CATALOG: List<Map<String, Any?>> = DEF.map {
        linkedMapOf<String, Any?>("name" to it.first, "group" to it.second, "desc" to it.third)
    }

    /** 进阶模式下整组锁死、不可打开的组。 */
    val SAFE_LOCKED_GROUPS: Set<String> = setOf("packages", "script")

    /** 危险到进阶模式不该默认开的单条命令（可手动开，但默认关）。 */
    private val SAFE_OFF: Set<String> = setOf(
        "rm", "rmdir", "chmod", "chown", "truncate", "ln", "mv",
        "am", "pm", "input", "settings", "svc", "wm", "curl",
        // cmd 和 pm 一样能装/卸/清数据（`cmd package clear/uninstall` 等于 pm clear），
        // env 能把后面的命令换个方式跑（`env reboot`），两个都默认关。
        // 手动打开后 judge 里还有拆壳递归和 cmd package 拒，不会裸奔。
        "cmd", "env",
        // 这几条名字像只读，实际带写参数：sort -o / logcat -f 能写文件，
        // date -s 能改系统时间。默认关，要用自己去额外设置里开。
        "sort", "logcat", "date",
        // awk/sed 的参数本身就是代码：`awk 'BEGIN{system("…")}'` 一行就能跑任意命令，
        // 白名单、路径检查、脚本组锁死全被绕过（实测能直接拿到 root）。
        // 默认关；真要开，judge 里还有一条 system( 的兜底拒绝。
        "awk", "sed",
    )

    /** 进阶模式默认打开：只读全开，文件类只留只读性的，系统类关掉能改状态的。 */
    val SAFE_DEFAULT: Set<String> = CATALOG
        .filter { it["group"] !in SAFE_LOCKED_GROUPS }
        .map { it["name"] as String }
        .filter { it !in SAFE_OFF }
        .toSet()

    fun groupOf(name: String): String? = CATALOG.firstOrNull { it["name"] == name }?.get("group") as String?

    fun groupTitle(id: String): String = GROUPS.firstOrNull { it["id"] == id }?.get("title") ?: id

    /** 高危模式默认：除包管理外全开（第一次 apt 要联网下载，默认关）。 */
    val DEFAULT_ALLOWED: Set<String> = CATALOG
        .filter { it["group"] != "packages" }
        .map { it["name"] as String }
        .toSet()
}
