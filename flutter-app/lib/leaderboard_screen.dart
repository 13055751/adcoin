import 'package:flutter/material.dart';

import 'api_client.dart';
import 'demo_data.dart';
import 'models.dart';

/// 排行榜（adcoins 持有榜）：后端 /api/leaderboard → 插件 /api/v1/top。
class LeaderboardScreen extends StatefulWidget {
  final Session? session;
  const LeaderboardScreen({super.key, required this.session});

  @override
  State<LeaderboardScreen> createState() => _LeaderboardScreenState();
}

class _LeaderboardScreenState extends State<LeaderboardScreen> {
  final _api = ApiClient();
  List<LeaderboardEntry>? _top; // null = 未加载
  String? _error;

  bool get guest => widget.session == null;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (guest) return;
    _api.setToken(widget.session!.token);
    try {
      final r = await _api.leaderboard();
      if (mounted) setState(() => _top = r.top);
    } catch (e) {
      if (mounted) setState(() => _error = '加载失败: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    final List<(String, double)> rows;
    if (guest) {
      rows = DemoData.leaderboard;
    } else if (_error != null) {
      rows = const [];
    } else if (_top == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    } else {
      rows = _top!.map((e) => (e.name ?? '?', e.balance)).toList();
    }

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'adcoins 排行榜${guest ? '（演示）' : ''}',
                style: Theme.of(context)
                    .textTheme
                    .headlineSmall
                    ?.copyWith(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              if (_error != null)
                Text(_error!, style: TextStyle(color: cs.error))
              else if (rows.isEmpty)
                Text('暂时没有数据', style: TextStyle(color: cs.onSurfaceVariant))
              else
                Expanded(
                  child: ListView.builder(
                    itemCount: rows.length,
                    itemBuilder: (context, i) {
                      final (name, balance) = rows[i];
                      return Card(
                        color: cs.surfaceContainerHighest,
                        margin: const EdgeInsets.symmetric(vertical: 4),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 14, vertical: 12),
                          child: Row(
                            children: [
                              Text('#${i + 1}',
                                  style: Theme.of(context)
                                      .textTheme
                                      .titleMedium
                                      ?.copyWith(fontWeight: FontWeight.bold)),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(name,
                                    style: const TextStyle(
                                        fontWeight: FontWeight.w500)),
                              ),
                              Text(_fmtNum(balance),
                                  style:
                                      Theme.of(context).textTheme.titleMedium),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

String _fmtNum(double v) {
  final s = v == v.roundToDouble() ? v.round().toString() : v.toStringAsFixed(2);
  final parts = s.split('.');
  final grouped = parts[0].replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
  return parts.length > 1 ? '$grouped.${parts[1]}' : grouped;
}