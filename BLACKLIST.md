# 进阶模式禁区清单

> 本文档列出进阶模式默认拒绝的全部范围。高危模式不受此限（「命令级永久禁」除外）。

## 一、系统与敏感区（读也不行）

| 路径 | 是什么 |
|---|---|
| `/system/ /vendor/ /product/ /apex/ /odm/` | 系统分区 |
| `/data/adb/` | root 管理器：模块、授权记录 |
| `/data/system/` | 系统设置、账户 |
| `/data/misc/` | Wi-Fi、VPN、密钥 |
| `/data/app/` | 已安装 App 的 APK |
| `/data/vendor/` | 厂商数据 |
| `/data/ 根目录下的其他文件` | 拦了房间也要拦走廊 |

## 二、敏感 App 数据（92 条，默认拒）

| 分类 | 应用名 | 包名 |
|---|---|---|
| 支付 | 数字人民币 | `cn.gov.pbc.dcep` |
| 支付 | 翼支付 | `com.chinatelecom.bestpayclient` |
| 支付 | 银联商务 | `com.chinaums.onlineservice` |
| 支付 | 支付宝 | `com.eg.android.AlipayGphone` |
| 支付 | 京东金融 | `com.jd.jrapp` |
| 支付 | 小米钱包 | `com.mipay.wallet` |
| 支付 | 壹钱包 | `com.paic.zhifu.wallet.activity` |
| 支付 | 腾讯 SOTER 支付认证 | `com.tencent.soter.soterserver` |
| 支付 | 云闪付 | `com.unionpay` |
| 支付 | 微博钱包 | `com.wj.wallet` |
| 支付 | IFAA 生物认证 | `org.ifaa.aidl.manager` |
| 银行 | 招商银行 | `cmb.pb` |
| 银行 | 民生银行 | `cn.com.cmbc.newmbank` |
| 银行 | 上海银行 | `cn.com.shbank.mper` |
| 银行 | 浦发银行 | `cn.com.spdb.mobilebank.per` |
| 银行 | 农行掌上银行 | `com.android.bankabc` |
| 银行 | 交通银行 | `com.bankcomm.Bankcomm` |
| 银行 | 北京银行 | `com.bankofbeijing.mobilebanking` |
| 银行 | 光大银行 | `com.cebbank.mobile.cemb` |
| 银行 | 广发银行 | `com.cgbchina.xpt` |
| 银行 | 中国银行 | `com.chinamworld.bocmbci` |
| 银行 | 中国建设银行 | `com.chinamworld.main` |
| 银行 | 兴业银行 | `com.cib.cibmb` |
| 银行 | 中信银行 | `com.ecitic.bank.mobile` |
| 银行 | 华夏银行 | `com.hxb.mobile.client` |
| 银行 | 中国工商银行 | `com.icbc` |
| 银行 | 平安口袋银行 | `com.pingan.paces.ccms` |
| 银行 | 平安银行香港 | `com.pingan.paces.hk` |
| 银行 | 邮储企业银行 | `com.psbc.youenterprise` |
| 银行 | 山西农商银行 | `com.sxnx.mbank.android` |
| 银行 | 邮储银行 | `com.yitong.mbank.psbc` |
| 证券与保险 | 太平洋保险 | `com.cpic.sxbxxe` |
| 证券与保险 | 东方财富 | `com.eastmoney.android.berlin` |
| 证券与保险 | 天天基金 | `com.eastmoney.android.fund` |
| 证券与保险 | 指南针股票 | `com.ez08.compass` |
| 证券与保险 | 同花顺 | `com.hexin.plat.android` |
| 证券与保险 | 平安证券 | `com.hundsun.winner.pazq` |
| 证券与保险 | 理财魔方 | `com.licaimofang.app` |
| 证券与保险 | 南方基金 | `com.nanfangjijin.app` |
| 证券与保险 | 牛股王股票 | `com.niuguwang.stock` |
| 证券与保险 | 腾讯理财通 | `com.tencent.fortuneplat` |
| 证券与保险 | 众安保险 | `com.zhongan.insurance` |
| 证券与保险 | 中信证券 | `com.zxscnew` |
| 运营商 | 中国广电 | `com.ai.obc.cbn.app` |
| 运营商 | 中国电信 | `com.ct.client` |
| 运营商 | 中国移动 | `com.greenpoint.android.mc10086.activity` |
| 运营商 | 中国联通 | `com.sinovatech.unicom.ui` |
| 健康医疗 | 优健康 | `com.ihaozhuo.youjiankang` |
| 健康医疗 | 平安乐健康 | `com.pa.health` |
| 凭证与隐私 | 快帆 | `cc.oasis.minor1` |
| 凭证与隐私 | FIDO 认证器 | `com.fido.asm` |
| 凭证与隐私 | 奔腾数字钥匙 | `com.ingeek.fawcar.digitalkey` |
| 凭证与隐私 | 小米密码 | `com.miui.password` |
| 凭证与隐私 | 贝锐蒲公英管理 | `com.oray.pgymanager` |
| 凭证与隐私 | WiFi万能钥匙 | `com.snda.wifilocating` |
| 凭证与隐私 | WAPI 证书管理 | `com.wapi.wapicertmanage` |
| 凭证与隐私 | 51数字钥匙 | `com.wicarlink.digitalcarkey` |
| 凭证与隐私 | 相册备份 | `com.xcbf.xg` |
| 凭证与隐私 | 密码管理器 | `com.xianshe.secret` |
| 凭证与隐私 | iKuuu VPN | `org.ikuuu.vpn` |
| 政务与证件 | 个人所得税 | `cn.gov.tax.its` |
| 政务与证件 | 国家医保服务平台 | `cn.hsa.app` |
| 政务与证件 | 电子证件 | `com.ctid.open` |
| 政务与证件 | 电子身份证管理 | `com.fangjiehz.dzsfzgl` |
| 政务与证件 | 电子身份证管家 | `com.meinibuxing.dzsfzgj` |
| 政务与证件 | 国家政务服务平台 | `com.service.android.gov.cn` |
| 政务与证件 | 交管12123 | `com.tmri.app.main` |
| 政务与证件 | 江苏医保云 | `com.wondersgroup.ybtproduct` |
| 政务与证件 | 社保电子医保管家 | `com.ywsocial.sbdz` |
| 电商 | 大众点评 | `com.dianping.v1` |
| 电商 | 京东 | `com.jingdong.app.mall` |
| 电商 | 美团 | `com.sankuai.meituan` |
| 电商 | 美团外卖 | `com.sankuai.meituan.takeoutnew` |
| 电商 | 抖音商城 | `com.ss.android.ugc.livelite` |
| 电商 | 闲鱼 | `com.taobao.idlefish` |
| 电商 | 淘宝 | `com.taobao.taobao` |
| 电商 | 拼多多 | `com.xunmeng.pinduoduo` |
| 电商 | 淘宝闪购 | `me.ele` |
| 电商 | 淘宝闪购商家版 | `me.ele.napos` |
| 社交与身份 | 钉钉 | `com.alibaba.android.rimet` |
| 社交与身份 | 百度贴吧 | `com.baidu.tieba` |
| 社交与身份 | 虎牙直播 | `com.duowan.kiwi` |
| 社交与身份 | BOSS直聘 | `com.hpbr.bosszhipin` |
| 社交与身份 | 快手极速版 | `com.kuaishou.nebula` |
| 社交与身份 | 微博 | `com.sina.weibo` |
| 社交与身份 | 快手 | `com.smile.gifmaker` |
| 社交与身份 | 抖音 | `com.ss.android.ugc.aweme` |
| 社交与身份 | 微信 | `com.tencent.mm` |
| 社交与身份 | QQ | `com.tencent.mobileqq` |
| 社交与身份 | 企业微信 | `com.tencent.wework` |
| 社交与身份 | 小红书 | `com.xingin.xhs` |
| 社交与身份 | 哔哩哔哩 | `tv.danmaku.bili` |

## 三、系统应用数据（按规则拒）

判断依据为 `PackageManager` 的 `FLAG_SYSTEM` / `FLAG_UPDATED_SYSTEM_APP`：只要是系统应用，其数据一律拒绝。
每个系统的敏感系统应用不同（短信库、电话、设置、厂商服务等），因此按规则而非名单。拿不到包管理器信息时按系统应用拒（fail-closed）。

## 四、相册与视频（纯只读）

`/sdcard/DCIM`、`/sdcard/Pictures`、`/sdcard/Movies`、`/storage/emulated/0/DCIM` 等。
只允许 13 个命令：`ls cat head tail grep find file stat du wc cut tr uniq`。
`cp`、`mv`、`rm`、`sort -o`、`logcat -f` 一律拒。

## 五、可写白名单

| 位置 | 条件 |
|---|---|
| `/sdcard/Download/` | 一直可写 |
| `/data/local/tmp/` | 一直可写 |
| `App 私有目录` | 一直可写 |
| `普通第三方 App 数据` | 可读写（非敏感、非系统） |
| `/sdcard/ 其他部分` | 需打开「允许读写整个 /sdcard」 |
| `系统分区、敏感 App、系统应用、/data/adb` | 高危模式才能碰 |

## 六、命令级永久禁

`dd` `mkfs` `mke2fs` `mkntfs` `mkdosfs` `fdisk` `parted` `sgdisk` `gdisk` `fastboot` `setenforce` `insmod` `rmmod` `modprobe` `wipe` `chroot` `pivot_root` `reboot` `shutdown` `halt`
