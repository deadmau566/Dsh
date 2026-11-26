# dsh-go 1.4.0 + fork 特性（自建构建）

本树 = 上游 `v1.4.0`（ccch1mneyyy/dsh-Go）为基线，叠加 `ghjicb/dsh-Go` 独有的：

- `shell` 工具（经 su 以 root 执行）与 `ShellGuard`（三档：原版 / 进阶 / 高危）
- 输入框上方终端胶囊、`设置 → 终端权限` 页、命令清单、最近记录
- 进阶模式的路径保护：相册只读、92 个敏感包名、系统应用 fail-closed、`LOCKED` 命令
- 翻译插件（`plugin_translate.dart`）、`BLACKLIST.md`
- 默认 agent 预设固定 `standard`

## 相对 fork 的改动

1. `awk`、`sed` 移出进阶默认（`ShellCommands.SAFE_OFF`）。原因：`awk 'BEGIN{system("…")}'`
   一行即可绕过全部静态检查，实测直接拿到 `uid=0`。
2. `ShellGuard.judge()` 增加兜底：进阶模式下 `awk` 带 `system(`/`getline`、`sed` 带 `e` 命令一律拒。
3. `ShellCommands.CMDS_REV` 2 → 3：已装的旧清单会自动重置成新默认（awk/sed 关）。

## 没做的

- 没有在真机/模拟器上跑过；只有 Dart 全量语法检查通过、Kotlin 结构检查通过。
- 没有把 fork 的 CI 自愈脚本（`scripts/auto_fix.py`）接进来。

## 构建

x86_64 Linux（或 GitHub Actions，见 `.github/workflows/build-port.yml`）：

```sh
bash runtime/build-runtime.sh arm64-v8a
python3 -I tooling/check_runtime_bundle.py arm64
bash tooling/build-apk.sh -f arm64 --no-companion
# 产物：app/build/app/outputs/flutter-apk/*.apk
```

APK 不能在本机（aarch64 Android proot）构建：Flutter Linux SDK 只发布 x86_64，
Android SDK 的 `aapt2` 与 NDK 工具链同样是 x86_64 二进制，运行直接
`Exec format error`。
