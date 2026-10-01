import 'package:flutter/material.dart';

import 'api_client.dart';
import 'models.dart';
import 'session_store.dart';

/// 我的：账号信息 + 构建配置（API 地址/广告模式）+ 解绑/退出。
class ProfileScreen extends StatelessWidget {
  final Session? session;
  final VoidCallback onLoginRequest;
  const ProfileScreen({super.key, required this.session, required this.onLoginRequest});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final s = session;
    final api = ApiClient();

    Future<void> unbind() async {
      if (s == null) {
        onLoginRequest();
        return;
      }
      api.setToken(s.token);
      try {
        final r = await api.unbind();
        SessionStore.updateLinked(null);
        _snack(context, r.ok ? '已解绑' : (r.error ?? '解绑失败'));
      } catch (e) {
        _snack(context, '解绑失败: $e');
      }
    }

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('我的',
                  style: Theme.of(context)
                      .textTheme
                      .headlineSmall
                      ?.copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              Card(
                color: cs.surfaceContainerHighest,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (s == null)
                        _row(context, '连接状态', '未连接后端（演示模式）')
                      else ...[
                        _row(context, '用户名', s.username),
                        _row(context, 'App ID', s.appUserId),
                        _row(context, '绑定账号', s.linkedPlayerName ?? '未绑定'),
                      ],
                      _row(context, '后端地址', kApiBaseUrl),
                      _row(context, '广告模式', kAdMode),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              if (s == null)
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: onLoginRequest,
                    child: const Text('重试连接后端（自动创建匿名账号）'),
                  ),
                )
              else ...[
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton(
                    onPressed: unbind,
                    child: const Text('解绑游戏账号'),
                  ),
                ),
                const SizedBox(height: 12),
                // 长期令牌恢复绑定（换设备/重装后无需回游戏抢短码）
                if (s != null && s.linkedPlayerName == null)
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.link),
                      label: const Text('恢复绑定（长期令牌）'),
                      onPressed: () async {
                        api.setToken(s.token);
                        try {
                          final r = await api.bindLong();
                          if (r.ok) {
                            final name = r.user?.linkedPlayerName;
                            SessionStore.updateLinked(name);
                            _snack(context, '已恢复绑定：${name ?? "?"}');
                          } else {
                            _snack(context, r.error ?? '恢复失败（可能需先用短码绑定）');
                          }
                        } catch (e) {
                          _snack(context, '恢复失败: $e');
                        }
                      },
                    ),
                  ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () async {
                      await SessionStore.clear();
                      await SessionStore.ensureAnonymousAccount();
                    },
                    child: const Text('重置匿名账号'),
                  ),
                ),
              ],
              const SizedBox(height: 16),
              Text(
                '无需注册：首次启动自动创建匿名账号；换手机回游戏重新扫码即可。未部署后端时用演示模式浏览。',
                style: Theme.of(context)
                    .textTheme
                    .bodySmall
                    ?.copyWith(color: cs.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _row(BuildContext context, String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label,
                style: Theme.of(context)
                    .textTheme
                    .bodySmall
                    ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            Text(value, style: Theme.of(context).textTheme.bodyLarge),
          ],
        ),
      );
}

void _snack(BuildContext context, String text) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(text), behavior: SnackBarBehavior.floating));
}