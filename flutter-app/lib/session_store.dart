import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

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