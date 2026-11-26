#!/usr/bin/env bash
# 合并上游后检查 fork 的功能代码还在。CI 里跑，缺一个就失败。
set -uo pipefail
fail=0
check() {
  local file=$1 sym=$2
  if [ ! -f "$file" ]; then echo "MISSING FILE $file"; fail=1; return; fi
  grep -qF "$sym" "$file" || { echo "MISSING SYMBOL '$sym' in $file"; fail=1; }
}
K=app/android/app/src/main/kotlin/io/github/ccch1mneyyy/dsh_app/phone
check $K/ShellGuard.kt 'object ShellGuard'
check $K/ShellGuard.kt 'fun judge('
check app/lib/phone/phone_channel.dart 'setShellMode'
check app/lib/phone/phone_channel.dart 'PhoneShellStatus'
check app/lib/state/phone_state.dart 'shell'
check app/lib/ui/chat/dock/root_pill.dart 'class RootPill'
check app/lib/ui/phone/shell_permissions_page.dart 'class ShellPermissionsPage'
check app/lib/ui/plugins/plugin_translate.dart 'class PluginTranslator'
check app/lib/ui/plugins/plugin_translate.dart 'class TranslatableDescription'
# 引用关系：页面/胶囊/翻译被真正挂上去
check app/lib/ui/chat/dock/chat_dock.dart 'root_pill.dart'
check app/lib/ui/chat/chat_screen.dart 'root_pill.dart'
check app/lib/ui/settings/settings_screen.dart 'shell_permissions_page.dart'
check app/lib/ui/plugins/plugin_detail_sheet.dart 'plugin_translate.dart'
# update_config 是上游文件，仓库完整树里必有；本地 16 文件备份里缺
check_optional() {
  local file=$1 sym=$2
  [ -f "$file" ] || return 0
  grep -qF "$sym" "$file" || { echo "MISSING SYMBOL '$sym' in $file"; fail=1; }
}
check_optional app/lib/update/update_config.dart "ghjicb/dsh-Go"
# UI 入口：插件页要真正引用翻译插件（避免只加文件不挂入口）
check app/lib/ui/plugins/plugin_inventory_page.dart 'plugin_translate.dart'
# 上游 API 不能被回退掉（phone_skill_use.dart 依赖它）
check app/lib/phone/phone_channel.dart 'prepareSkillReplay'
# 默认终端预设（不走 run_code 扳手）：PhoneGuest 合并时必须保留
check $K/PhoneGuest.kt 'selectedDefault: standard'
check $K/PhoneGuest.kt 'PRESET_DEFAULT_YAML'
if [ $fail -ne 0 ]; then echo "FORK FEATURES MISSING"; exit 1; fi
echo "fork features intact"
