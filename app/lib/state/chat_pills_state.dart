import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// 「聊天页显示胶囊」开关的持久化键（终端权限页写入，聊天页读取）。
const String kPrefChatPills = 'chat_pills_visible';

/// 聊天页是否显示终端胶囊。
///
/// 终端权限页开关改它、聊天页 watch 它——跨页面即时生效，不用重启 App。
/// 原版模式下也显示胶囊（隐藏由这个开关负责，不再按档位隐藏）。
final chatPillsVisibleProvider =
    NotifierProvider<ChatPillsNotifier, bool>(ChatPillsNotifier.new);

class ChatPillsNotifier extends Notifier<bool> {
  @override
  bool build() => true;

  void set(bool value) => state = value;
}

/// 启动时从本地偏好载入一次；读失败保持默认（显示）。
Future<void> loadChatPillsVisible(WidgetRef ref) async {
  try {
    final p = await SharedPreferences.getInstance();
    ref.read(chatPillsVisibleProvider.notifier).set(p.getBool(kPrefChatPills) ?? true);
  } catch (_) {
    // 读不到就按默认值来，不影响聊天。
  }
}
