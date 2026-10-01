import 'package:flutter/material.dart';

import 'api_client.dart';
import 'demo_data.dart';
import 'models.dart';

/// 好友：搜索添加 / 待处理请求 / 好友列表（在线状态彩点）/ 点击转账。
class FriendsScreen extends StatefulWidget {
  final Session? session;
  final VoidCallback onLoginRequest;
  const FriendsScreen({super.key, required this.session, required this.onLoginRequest});

  @override
  State<FriendsScreen> createState() => _FriendsScreenState();
}

class _FriendsScreenState extends State<FriendsScreen> {
  final _api = ApiClient();
  final _searchCtrl = TextEditingController();

  List<FriendItem> _friends = [];
  List<Map<String, dynamic>> _pending = [];
  List<SearchResult> _results = [];
  bool _loading = false;
  bool _searching = false;

  bool get guest => widget.session == null;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (guest) {
      setState(() {
        _friends = DemoData.friends
            .map((f) => FriendItem(
                uuid: 'demo-${f.name}',
                name: f.name,
                online: f.online,
                appUserId: 'demo-${f.name}'))
            .toList();
        _pending = <Map<String, dynamic>>[
          {'name': 'Alex', 'appUserId': 'demo-Alex'},
        ];
      });
      return;
    }
    setState(() => _loading = true);
    _api.setToken(widget.session!.token);
    try {
      final f = await _api.friend('list');
      final p = await _api.friend('pending');
      setState(() {
        _friends = f.friends;
        _pending = p.requests;
      });
    } catch (e) {
      _snack('加载失败: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text), behavior: SnackBarBehavior.floating));
  }

  Future<void> _doSearch() async {
    final q = _searchCtrl.text.trim();
    if (q.isEmpty) return;
    if (guest) {
      widget.onLoginRequest();
      return;
    }
    setState(() => _searching = true);
    try {
      final r = await _api.search(q);
      setState(() => _results = r.results);
    } catch (e) {
      _snack('搜索失败: $e');
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  Future<void> _sendRequest(SearchResult r) async {
    if (guest || r.appUserId == null) {
      widget.onLoginRequest();
      return;
    }
    try {
      final res = await _api.friend('request', {'otherAppUserId': r.appUserId});
      _snack(res.ok ? '已发送好友请求' : (res.error ?? '发送失败'));
    } catch (e) {
      _snack('发送失败: $e');
    }
  }

  Future<void> _accept(Map<String, dynamic> p) async {
    final id = p['appUserId'] ?? p['uuid'];
    if (id == null) return;
    try {
      final r = await _api.friend('accept', {'otherAppUserId': id});
      _snack(r.ok ? '已接受' : (r.error ?? '操作失败'));
      _load();
    } catch (e) {
      _snack('操作失败: $e');
    }
  }

  Future<void> _reject(Map<String, dynamic> p) async {
    final id = p['appUserId'] ?? p['uuid'];
    if (id == null) return;
    try {
      await _api.friend('reject', {'otherAppUserId': id});
      _load();
    } catch (_) {}
  }

  Future<void> _transferDialog(FriendItem f) async {
    if (guest) {
      widget.onLoginRequest();
      return;
    }
    final ctrl = TextEditingController();
    final amount = await showDialog<double>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text('转账给 ${f.name ?? "?"}'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('从你的 adcoins 余额转出：'),
            const SizedBox(height: 12),
            TextField(
              controller: ctrl,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                  labelText: '金额', border: OutlineInputBorder()),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.of(c).pop(), child: const Text('取消')),
          FilledButton(
            onPressed: () {
              final v = double.tryParse(ctrl.text.trim());
              if (v != null && v > 0) Navigator.of(c).pop(v);
            },
            child: const Text('转账'),
          ),
        ],
      ),
    );
    if (amount == null || f.appUserId == null) return;
    try {
      final r = await _api.transfer(
        toAppUserId: f.appUserId!,
        amount: amount,
        clientTxId: 'c-${DateTime.now().millisecondsSinceEpoch}',
      );
      if (r.ok) {
        _snack('已转出 ${_fmtAmt(amount)} adcoins 给 ${f.name}');
        _load();
      } else {
        _snack(r.error ?? '转账失败');
      }
    } catch (e) {
      _snack('转账失败: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final items = <Widget>[
      // ---- 搜索 ----
      Row(
        children: [
          Expanded(
            child: TextField(
              controller: _searchCtrl,
              decoration: const InputDecoration(
                labelText: '搜索用户名添加好友',
                border: OutlineInputBorder(),
                isDense: true,
              ),
              onSubmitted: (_) => _doSearch(),
            ),
          ),
          const SizedBox(width: 8),
          FilledButton(
            onPressed: _searching ? null : _doSearch,
            child: _searching
                ? const SizedBox(
                    width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('搜索'),
          ),
        ],
      ),
      const SizedBox(height: 12),

      // ---- 搜索结果 ----
      if (_results.isNotEmpty) ...[
        _sectionTitle('搜索结果'),
        ..._results.map((r) => ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.person_outline),
              title: Text(r.username ?? r.name ?? '?'),
              subtitle: Text('游戏账号: ${r.playerName ?? r.name ?? "未知"}'),
              trailing: OutlinedButton(
                onPressed: () => _sendRequest(r),
                child: const Text('加好友'),
              ),
            )),
        const Divider(),
      ],

      // ---- 待处理请求 ----
      if (_pending.isNotEmpty) ...[
        _sectionTitle('待处理请求（${_pending.length}）'),
        ..._pending.map((p) => Card(
              color: cs.surfaceContainerHighest,
              child: ListTile(
                title: Text(p['name'] ?? p['username'] ?? '?'),
                subtitle: const Text('想加你为好友'),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextButton(
                        onPressed: () => _accept(p), child: const Text('接受')),
                    TextButton(
                        onPressed: () => _reject(p), child: const Text('拒绝')),
                  ],
                ),
              ),
            )),
        const Divider(),
      ],

      // ---- 好友列表 ----
      _sectionTitle('我的好友（${_friends.length}）${guest ? ' · 演示' : ''}'),
      if (_friends.isEmpty && !_loading)
        Text('还没有好友，用上方搜索添加吧。',
            style: TextStyle(color: cs.onSurfaceVariant)),
      ..._friends.map((f) => ListTile(
            contentPadding: EdgeInsets.zero,
            onTap: () => _transferDialog(f),
            leading: Container(
              width: 9,
              height: 9,
              decoration: BoxDecoration(
                color: f.online ? cs.primary : cs.outlineVariant,
                shape: BoxShape.circle,
              ),
            ),
            title: Row(
              children: [
                Text(f.name ?? '?',
                    style: const TextStyle(fontWeight: FontWeight.w500)),
                if (f.online) ...[
                  const SizedBox(width: 8),
                  Text('在线',
                      style: TextStyle(fontSize: 12, color: cs.primary)),
                ],
              ],
            ),
          )),
      if (_friends.isNotEmpty)
        Text('点击好友可转账${guest ? '（演示模式需先登录）' : ''}',
            style:
                Theme.of(context).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
    ];

    return Scaffold(
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : RefreshIndicator(
                onRefresh: _load,
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                  children: items,
                ),
              ),
      ),
    );
  }

  Widget _sectionTitle(String text) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Text(
          text,
          style: Theme.of(context)
              .textTheme
              .titleMedium
              ?.copyWith(color: Theme.of(context).colorScheme.primary),
        ),
      );
}

String _fmtAmt(double v) =>
    v == v.roundToDouble() ? v.round().toString() : v.toStringAsFixed(2);