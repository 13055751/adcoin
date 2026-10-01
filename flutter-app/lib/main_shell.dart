import 'package:flutter/material.dart';

import 'api_client.dart';
import 'friends_screen.dart';
import 'home_screen.dart';
import 'leaderboard_screen.dart';
import 'login_screen.dart';
import 'models.dart';
import 'profile_screen.dart';
import 'session_store.dart';

/// 主界面壳：顶部游客横幅（仅未登录）+ 4 tab 底部导航（好友 tab 角标）。
class MainShell extends StatefulWidget {
  final Session? session;
  const MainShell({super.key, required this.session});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _tab = 0;
  int _pendingCount = 0;
  final _api = ApiClient();

  @override
  void initState() {
    super.initState();
    _refreshBadge();
  }

  @override
  void didUpdateWidget(MainShell old) {
    super.didUpdateWidget(old);
    if (old.session?.token != widget.session?.token) _refreshBadge();
  }

  Future<void> _refreshBadge() async {
    final s = widget.session;
    if (s == null) {
      setState(() => _pendingCount = 2); // 游客演示角标
      return;
    }
    _api.setToken(s.token);
    try {
      final r = await _api.friend('pending');
      if (mounted) setState(() => _pendingCount = r.requests.length);
    } catch (_) {
      if (mounted) setState(() => _pendingCount = 0);
    }
  }

  void _openLogin() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final guest = widget.session == null;
    final screens = [
      HomeScreen(
        session: widget.session,
        pendingCount: _pendingCount,
        onLoginRequest: _openLogin,
        onNavigate: (i) => setState(() => _tab = i),
        onBadgeChanged: () => _refreshBadge(),
      ),
      FriendsScreen(session: widget.session, onLoginRequest: _openLogin),
      LeaderboardScreen(session: widget.session),
      ProfileScreen(session: widget.session, onLoginRequest: _openLogin),
    ];
    return Scaffold(
      appBar: guest
          ? AppBar(
              backgroundColor: Theme.of(context).colorScheme.secondaryContainer,
              toolbarHeight: 34,
              title: Row(
                children: [
                  Expanded(
                    child: Text(
                      '未登录 · 演示数据（功能需登录后可用）',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                  TextButton(
                    onPressed: _openLogin,
                    child: const Text('登录/注册'),
                  ),
                ],
              ),
            )
          : null,
      body: IndexedStack(index: _tab, children: screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) {
          setState(() => _tab = i);
          if (i == 1) _refreshBadge();
        },
        destinations: [
          const NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: '首页'),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: _pendingCount > 0,
              label: Text('$_pendingCount'),
              child: const Icon(Icons.people_outline),
            ),
            selectedIcon: Badge(
              isLabelVisible: _pendingCount > 0,
              label: Text('$_pendingCount'),
              child: const Icon(Icons.people),
            ),
            label: '好友',
          ),
          const NavigationDestination(icon: Icon(Icons.leaderboard_outlined), selectedIcon: Icon(Icons.leaderboard), label: '排行'),
          const NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: '我的'),
        ],
      ),
    );
  }
}