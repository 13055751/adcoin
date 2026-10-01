import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';
import 'models.dart';

/// 登录会话（shared_preferences 持久化 + ValueNotifier 供 UI 监听）。
class SessionStore {
  static const _kToken = 'token';
  static const _kUsername = 'username';
  static const _kAppUserId = 'app_user_id';
  static const _kLinkedName = 'linked_name';

  static final ValueNotifier<Session?> session = ValueNotifier<Session?>(null);

  static Future<void> load() async {
    final sp = await SharedPreferences.getInstance();
    final token = sp.getString(_kToken);
    if (token == null || token.isEmpty) {
      session.value = null;
      return;
    }
    session.value = Session(
      token: token,
      username: sp.getString(_kUsername) ?? '',
      appUserId: sp.getString(_kAppUserId) ?? '',
      linkedPlayerName: sp.getString(_kLinkedName),
    );
  }

  /// 静默注册匿名设备账号（无 UI，玩家无感）；已登录/后端不可达时直接返回。
  /// 启动与"重连"都调用它——后端恢复后重试即可拿到账号。
  static Future<void> ensureAnonymousAccount() async {
    if (session.value != null) return;
    try {
      final hex = List.generate(
              12, (_) => '0123456789abcdef'[DateTime.now().microsecondsSinceEpoch % 16])
          .join();
      final res = await ApiClient().register('u_$hex', 'pw_$hex${hex.length}x9k');
      if (res.ok && res.token != null) {
        await save(Session(
          token: res.token!,
          username: res.user?.username ?? 'u_$hex',
          appUserId: res.user?.appUserId ?? '',
          linkedPlayerName: res.user?.linkedPlayerName,
        ));
      }
    } catch (_) {
      // 后端不可达：保持游客（演示数据）模式，横幅可重试
    }
  }

  static Future<void> save(Session s) async {
    final sp = await SharedPreferences.getInstance();
    await sp.setString(_kToken, s.token);
    await sp.setString(_kUsername, s.username);
    await sp.setString(_kAppUserId, s.appUserId);
    if (s.linkedPlayerName != null) {
      await sp.setString(_kLinkedName, s.linkedPlayerName!);
    }
    session.value = s;
  }

  static Future<void> updateLinked(String? name) async {
    final sp = await SharedPreferences.getInstance();
    if (name == null) {
      await sp.remove(_kLinkedName);
    } else {
      await sp.setString(_kLinkedName, name);
    }
    final s = session.value;
    if (s != null) {
      session.value = Session(
        token: s.token,
        username: s.username,
        appUserId: s.appUserId,
        linkedPlayerName: name,
      );
    }
  }

  static Future<void> clear() async {
    final sp = await SharedPreferences.getInstance();
    await sp.remove(_kToken);
    await sp.remove(_kUsername);
    await sp.remove(_kAppUserId);
    await sp.remove(_kLinkedName);
    session.value = null;
  }
}