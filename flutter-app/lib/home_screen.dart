import 'package:flutter/material.dart';

import 'ad_providers.dart';
import 'api_client.dart';
import 'demo_data.dart';
import 'models.dart';
import 'session_store.dart';

class _TxUi {
  final String title;
  final String time;
  final String amount;
  final bool? positive;
  const _TxUi(this.title, this.time, this.amount, this.positive);
}

/// 首页（方案 B · 清爽钱包 · M3）：大数字余额 + 趋势胶囊 + 药丸 CTA +
/// 今日进度卡 + 快捷操作 + 时间线动态。session=null 时为游客（演示数据）。
class HomeScreen extends StatefulWidget {
  final Session? session;
  final int pendingCount;
  final VoidCallback onLoginRequest;
  final void Function(int) onNavigate;
  final VoidCallback onBadgeChanged;

  const HomeScreen({
    super.key,
    required this.session,
    required this.pendingCount,
    required this.onLoginRequest,
    required this.onNavigate,
    required this.onBadgeChanged,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _api = ApiClient();

  // 真实数据（session 变化时重置；游客模式渲染时用 DemoData 常量）
  double? _balance;
  String? _linkedName;
  int _dailyUsed = 0;
  int _dailyLimit = 20;
  double _adReward = 50;
  int? _rank;
  int _friendCount = 0;
  int _friendOnline = 0;
  List<_TxUi>? _txs; // null = 未加载
  bool _refreshing = false;
  bool _watching = false;

  @override
  void initState() {
    super.initState();
    _bindApi();
    _refresh();
  }

  @override
  void didUpdateWidget(HomeScreen old) {
    super.didUpdateWidget(old);
    if (old.session?.token != widget.session?.token) {
      setState(() {
        _balance = null;
        _linkedName = null;
        _dailyUsed = 0;
        _dailyLimit = 20;
        _adReward = 50;
        _rank = null;
        _friendCount = 0;
        _friendOnline = 0;
        _txs = null;
      });
      _bindApi();
      _refresh();
    }
  }

  void _bindApi() => _api.setToken(widget.session?.token);

  Future<void> _refresh() async {
    final s = widget.session;
    if (s == null) return;
    setState(() => _refreshing = true);
    try {
      final me = await _api.me();
      setState(() {
        _balance = me.balance;
        _dailyUsed = me.dailyUsed;
        _dailyLimit = me.dailyLimit;
        _adReward = me.adReward;
        if (me.linked) {
          _linkedName = me.user?.linkedPlayerName;
          if (_linkedName != null) {
            SessionStore.updateLinked(_linkedName);
          }
        }
      });
    } catch (_) {}
    try {
      final lb = await _api.leaderboard();
      final me = await _api.me();
      final uuid = me.user?.linkedPlayerUuid;
      final idx = lb.top.indexWhere((e) => e.uuid == uuid);
      setState(() => _rank = (idx >= 0 && uuid != null) ? idx + 1 : null);
    } catch (_) {}
    try {
      final fl = await _api.friend('list');
      setState(() {
        _friendCount = fl.friends.length;
        _friendOnline = fl.friends.where((f) => f.online).length;
      });
    } catch (_) {}
    try {
      final tr = await _api.transactions();
      setState(() {
        _txs = tr.entries.map((e) {
          if (e.adNetwork == 'transfer') {
            return _TxUi('收到好友转币', _fmtTime(e.ts), '+${_fmtAmt(e.amount)}', true);
          }
          return _TxUi('看广告获得奖励', _fmtTime(e.ts), '+${_fmtAmt(e.amount)}', true);
        }).toList();
      });
    } catch (_) {}
    if (mounted) setState(() => _refreshing = false);
  }

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text), behavior: SnackBarBehavior.floating));
  }

  Future<void> _watchAd() async {
    final s = widget.session;
    if (s == null) {
      _snack('演示模式：登录后才能看广告赚币');
      widget.onLoginRequest();
      return;
    }
    if (_watching || !mounted) return;
    setState(() => _watching = true);
    final provider = AdManager.provider();
    await provider.showRewarded(
      context,
      (txId, adUnit) async {
        try {
          final res = await _api.claim(
            platform: provider.id,
            transactionId: txId,
            adUnitId: adUnit,
          );
          if (!mounted) return;
          _snack(res.duplicate ? '该广告已结算过' : '看广告成功，奖励已发放！');
          await _refresh();
        } catch (e) {
          _snack('发放失败: $e');
        } finally {
          if (mounted) setState(() => _watching = false);
        }
      },
      (err) {
        if (mounted) setState(() => _watching = false);
        _snack(err);
      },
    );
  }

  Future<void> _openBindDialog() async {
    final controller = TextEditingController();
    final code = await showDialog<String>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('绑定游戏账号'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('在游戏里输入 /adlink 获取绑定码，然后输入到下面：'),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(labelText: '绑定码', border: OutlineInputBorder()),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(c).pop(), child: const Text('取消')),
          FilledButton(
            onPressed: () {
              final v = controller.text.trim().toUpperCase();
              if (v.length >= 4) Navigator.of(c).pop(v);
            },
            child: const Text('绑定'),
          ),
        ],
      ),
    );
    if (code == null) return;
    try {
      final res = await _api.bind(code);
      if (res.ok) {
        final name = res.user?.linkedPlayerName;
        setState(() => _linkedName = name);
        SessionStore.updateLinked(name);
        _snack('绑定成功：${name ?? "已绑定"}');
        _refresh();
      } else {
        _snack('绑定失败: ${res.error ?? "未知错误"}');
      }
    } catch (e) {
      _snack('绑定失败: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final guest = widget.session == null;

    // 渲染值：游客=演示常量，登录=真实 state
    final vBalance = guest ? DemoData.balance : (_balance ?? 0.0);
    final vLinked = guest ? DemoData.linkedName : _linkedName;
    final vUsed = guest ? DemoData.dailyUsed : _dailyUsed;
    final vLimit = guest ? DemoData.dailyLimit : _dailyLimit;
    final vReward = guest ? DemoData.adReward : _adReward;
    final vRank = guest ? DemoData.demoRank() : _rank;
    final vFriendCount = guest ? DemoData.friends.length : _friendCount;
    final vFriendOnline =
        guest ? DemoData.friends.where((f) => f.online).length : _friendOnline;
    final vTxs = guest
        ? DemoData.transactions
            .map((t) => _TxUi(t.title, t.time, t.amount, t.positive))
            .toList()
        : (_txs ?? const <_TxUi>[]);
    final remain = (vLimit - vUsed).clamp(0, 1 << 30).toInt();

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
          children: [
            // ---- 顶栏 ----
            Row(
              children: [
                Text('AdCoin',
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.w600)),
                const Spacer(),
                if (guest)
                  TextButton(
                    onPressed: widget.onLoginRequest,
                    child: const Text('登录/注册', style: TextStyle(fontWeight: FontWeight.bold)),
                  )
                else ...[
                  _IconDotButton(
                    icon: Icons.notifications_outlined,
                    filled: widget.pendingCount > 0,
                    onTap: () => widget.onNavigate(1),
                  ),
                  const SizedBox(width: 6),
                ],
                _IconDotButton(
                  icon: Icons.settings_outlined,
                  filled: false,
                  onTap: () => widget.onNavigate(3),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // ---- 余额大数字 + 趋势胶囊 ----
            Text('ADCOINS 余额',
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    letterSpacing: 1.2, color: cs.onSurfaceVariant)),
            Row(
              children: [
                Text(_fmtNum(vBalance),
                    style: Theme.of(context)
                        .textTheme
                        .displaySmall
                        ?.copyWith(fontWeight: FontWeight.w800)),
                const SizedBox(width: 12),
                if (guest)
                  const _TrendPill('▲ 今日 +150')
                else if (vUsed > 0)
                  _TrendPill('今日已看 $vUsed 次'),
              ],
            ),
            Text(
              guest
                  ? '已绑定 ${DemoData.linkedName} · 演示数据'
                  : (vLinked != null ? '已绑定 $vLinked' : '未绑定游戏账号'),
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: cs.onSurfaceVariant),
            ),
            const SizedBox(height: 16),

            // ---- CTA：药丸主按钮 + 排行榜方形按钮 ----
            Row(
              children: [
                Expanded(
                  child: FilledButton(
                    onPressed: _watchAd,
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(54),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(28)),
                    ),
                    child: _watching
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.play_arrow),
                              const SizedBox(width: 6),
                              Text('看广告赚 ${_fmtAmt(vReward)} adcoins',
                                  style: const TextStyle(fontWeight: FontWeight.bold)),
                            ],
                          ),
                  ),
                ),
                const SizedBox(width: 10),
                _IconDotButton(
                  icon: Icons.leaderboard_outlined,
                  filled: false,
                  size: 54,
                  onTap: () => widget.onNavigate(2),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // ---- 今日进度卡 ----
            Card(
              color: cs.surfaceContainerHighest,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Text('今日看广告进度',
                            style: Theme.of(context).textTheme.bodyMedium),
                        const Spacer(),
                        Text('$vUsed / $vLimit 次',
                            style: Theme.of(context)
                                .textTheme
                                .labelLarge
                                ?.copyWith(color: cs.primary, fontWeight: FontWeight.bold)),
                      ],
                    ),
                    const SizedBox(height: 9),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: vLimit <= 0 ? 0.0 : vUsed / vLimit,
                        minHeight: 7,
                        color: cs.primary,
                        backgroundColor: cs.surface,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        remain > 0
                            ? '再看 $remain 次可得 ${_fmtAmt(remain * vReward)} adcoins'
                            : '今日次数已用完，明天再来',
                        style: Theme.of(context)
                            .textTheme
                            .bodySmall
                            ?.copyWith(color: cs.onSurfaceVariant),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 18),

            // ---- 快捷操作 ----
            Text('快捷操作',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: _QuickCard(
                    icon: Icons.people_outline,
                    value: '$vFriendCount 位好友',
                    sub: '$vFriendOnline 人在线',
                    onTap: guest ? widget.onLoginRequest : () => widget.onNavigate(1),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _QuickCard(
                    icon: Icons.leaderboard_outlined,
                    value: vRank != null ? '第 $vRank 名' : '未上榜',
                    sub: 'adcoins 持有榜',
                    onTap: () => widget.onNavigate(2),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _QuickCard(
                    icon: Icons.link,
                    value: vLinked != null ? '已绑定' : '未绑定',
                    sub: vLinked ?? '去绑定',
                    onTap: () {
                      if (guest) {
                        widget.onLoginRequest();
                      } else if (vLinked != null) {
                        _snack('已绑定 $vLinked，解绑去「我的」');
                      } else {
                        _openBindDialog();
                      }
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),

            // ---- 最近动态 ----
            Text('最近动态',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            Card(
              color: cs.surfaceContainerHighest,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                child: !guest && _txs == null && !_refreshing
                    ? const Padding(
                        padding: EdgeInsets.symmetric(vertical: 14),
                        child: Text('加载中…'),
                      )
                    : vTxs.isEmpty
                        ? const Padding(
                            padding: EdgeInsets.symmetric(vertical: 14),
                            child: Text('还没有动态，看一个广告试试'),
                          )
                        : Column(
                            children: vTxs.map((tx) => _TxRow(tx)).toList(),
                          ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------- 小组件

class _TrendPill extends StatelessWidget {
  final String text;
  const _TrendPill(this.text);

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: cs.secondaryContainer,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(text,
          style: Theme.of(context)
              .textTheme
              .labelMedium
              ?.copyWith(color: cs.onSecondaryContainer, fontWeight: FontWeight.bold)),
    );
  }
}

class _IconDotButton extends StatelessWidget {
  final IconData icon;
  final bool filled;
  final double size;
  final VoidCallback onTap;
  const _IconDotButton({
    required this.icon,
    required this.filled,
    required this.onTap,
    this.size = 36,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(size / 2),
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: filled ? cs.secondaryContainer : cs.surfaceContainerHigh,
          shape: BoxShape.circle,
        ),
        child: Stack(
          alignment: Alignment.center,
          children: [
            Icon(icon,
                size: size * 0.55,
                color: filled ? cs.onSecondaryContainer : cs.onSurfaceVariant),
            if (filled)
              Positioned(
                top: 7,
                right: 8,
                child: Container(
                  width: 9,
                  height: 9,
                  decoration: BoxDecoration(color: cs.error, shape: BoxShape.circle),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _QuickCard extends StatelessWidget {
  final IconData icon;
  final String value;
  final String sub;
  final VoidCallback onTap;
  const _QuickCard({
    required this.icon,
    required this.value,
    required this.sub,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      color: cs.surfaceContainerHighest,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, size: 20, color: cs.primary),
              const SizedBox(height: 8),
              Text(value,
                  style: Theme.of(context)
                      .textTheme
                      .titleSmall
                      ?.copyWith(fontWeight: FontWeight.bold)),
              Text(sub,
                  style: Theme.of(context)
                      .textTheme
                      .bodySmall
                      ?.copyWith(color: cs.onSurfaceVariant)),
            ],
          ),
        ),
      ),
    );
  }
}

class _TxRow extends StatelessWidget {
  final _TxUi tx;
  const _TxRow(this.tx);

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final color = switch (tx.positive) {
      true => cs.primary,
      false => cs.error,
      _ => cs.onSurfaceVariant,
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 11),
      child: Row(
        children: [
          Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(tx.title, style: Theme.of(context).textTheme.bodyMedium),
                Text(tx.time,
                    style: Theme.of(context)
                        .textTheme
                        .bodySmall
                        ?.copyWith(color: cs.onSurfaceVariant)),
              ],
            ),
          ),
          Text(tx.amount,
              style: Theme.of(context)
                  .textTheme
                  .titleSmall
                  ?.copyWith(fontWeight: FontWeight.bold, color: color)),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- 格式化

String _fmtNum(double v) {
  final s = v == v.roundToDouble()
      ? v.round().toString()
      : v.toStringAsFixed(2);
  final parts = s.split('.');
  final grouped = parts[0].replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
  return parts.length > 1 ? '$grouped.${parts[1]}' : grouped;
}

String _fmtAmt(double v) =>
    v == v.roundToDouble() ? v.round().toString() : v.toStringAsFixed(2);

String _fmtTime(int ts) {
  final d = DateTime.fromMillisecondsSinceEpoch(ts);
  String p(int x) => x.toString().padLeft(2, '0');
  return '${p(d.month)}-${p(d.day)} ${p(d.hour)}:${p(d.minute)}';
}