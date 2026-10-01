import 'package:flutter/material.dart';

import 'api_client.dart';
import 'models.dart';
import 'session_store.dart';

/// 登录/注册页。可返回主界面（游客模式），登录成功自动回到主界面。
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _api = ApiClient();
  bool _registerMode = false;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (_username.text.trim().isEmpty || _password.text.isEmpty) {
      setState(() => _error = '请输入用户名和密码');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final AuthResult res = _registerMode
          ? await _api.register(_username.text.trim(), _password.text)
          : await _api.login(_username.text.trim(), _password.text);
      if (!mounted) return;
      if (res.ok && res.token != null) {
        await SessionStore.save(Session(
          token: res.token!,
          username: res.user?.username ?? _username.text.trim(),
          appUserId: res.user?.appUserId ?? '',
          linkedPlayerName: res.user?.linkedPlayerName,
        ));
        if (mounted) Navigator.of(context).pop();
      } else {
        setState(() => _error = res.error ?? '请求失败');
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = '连不上后端（$e）。可返回用演示模式浏览。');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.monetization_on, size: 56, color: cs.primary),
                const SizedBox(height: 8),
                Text('AdCoin', style: Theme.of(context).textTheme.displaySmall),
                Text('看广告赚游戏币',
                    style: Theme.of(context).textTheme.bodyLarge),
                const SizedBox(height: 24),
                TextField(
                  controller: _username,
                  decoration: const InputDecoration(
                      labelText: '用户名', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _password,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: _registerMode ? '密码（至少 6 位）' : '密码',
                    border: const OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 16),
                if (_error != null) ...[
                  Text(_error!,
                      style: TextStyle(color: cs.error),
                      textAlign: TextAlign.center),
                  const SizedBox(height: 8),
                ],
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: _busy
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Text(_registerMode ? '注册并登录' : '登录'),
                  ),
                ),
                TextButton(
                  onPressed: () =>
                      setState(() => _registerMode = !_registerMode),
                  child: Text(_registerMode ? '已有账号？去登录' : '没有账号？去注册'),
                ),
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('先不登录，用演示模式浏览'),
                ),
                Text(
                  '提示：未部署后端时无法登录，但可用演示模式查看所有界面。',
                  style: Theme.of(context)
                      .textTheme
                      .bodySmall
                      ?.copyWith(color: cs.onSurfaceVariant),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}