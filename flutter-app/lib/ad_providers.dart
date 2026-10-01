import 'dart:math';

import 'package:flutter/material.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

import 'api_client.dart';

/// 广告 Provider 抽象：看完一个激励视频 → 回调 (transactionId, adUnitId)。
abstract class AdProvider {
  String get id;
  String get displayName;
  Future<void> showRewarded(
    BuildContext context,
    void Function(String txId, String? adUnitId) onReward,
    void Function(String error) onError,
  );
}

String _txId(String prefix) {
  final r = Random.secure();
  final hex = List.generate(8, (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
  return '$prefix-$hex';
}

/// 模拟广告（联调/演示，无需广告账号）。
class MockAdProvider implements AdProvider {
  @override
  String get id => 'mock';
  @override
  String get displayName => '模拟广告';

  @override
  Future<void> showRewarded(
    BuildContext context,
    void Function(String, String?) onReward,
    void Function(String) onError,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('模拟广告'),
        content: const Text('正在播放广告…\n（开发模式：点击"看完了"即可领取奖励）'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(c).pop(true),
            child: const Text('看完了'),
          ),
        ],
      ),
    );
    if (ok == true) {
      onReward(_txId('mock'), 'mock-ad-unit');
    } else {
      onError('广告未完成');
    }
  }
}

/// AdMob 激励视频（google_mobile_ads）。SSV 由广告平台回调后端为准。
class AdMobAdProvider implements AdProvider {
  static const String rewardedUnit = String.fromEnvironment(
    'ADMOB_REWARDED_UNIT',
    defaultValue: 'ca-app-pub-3940256099942544/5224354917', // 官方测试广告位
  );

  @override
  String get id => 'admob';
  @override
  String get displayName => 'AdMob';

  @override
  Future<void> showRewarded(
    BuildContext context,
    void Function(String, String?) onReward,
    void Function(String) onError,
  ) async {
    // adLoadCallback 风格（google_mobile_ads v5~v9 通用），不依赖 load 的返回值
    RewardedAd.load(
      adUnitId: rewardedUnit,
      request: const AdRequest(),
      adLoadCallback: RewardedAdLoadCallback(
        onAdLoaded: (ad) {
          ad.fullScreenContentCallback = FullScreenContentCallback(
            onAdFailedToShowFullScreenContent: (ad, err) {
              ad.dispose();
              onError('广告展示失败: $err');
            },
            onAdDismissedFullScreenContent: (ad) => ad.dispose(),
          );
          ad.show(onUserEarnedReward: (ad, reward) {
            onReward(_txId('admob'), rewardedUnit);
          });
        },
        onAdFailedToLoad: (e) => onError('广告加载失败: ${e.message}'),
      ),
    );
  }
}

/// Pangle（穿山甲）——申请到账号后按官方文档补全（与 Kotlin 版策略一致）。
class PangleAdProvider implements AdProvider {
  @override
  String get id => 'pangle';
  @override
  String get displayName => 'Pangle 穿山甲';

  @override
  Future<void> showRewarded(
    BuildContext context,
    void Function(String, String?) onReward,
    void Function(String) onError,
  ) async {
    onError('Pangle 真实接入待补全（申请到穿山甲账号后按官方文档实现）');
  }
}

class AdManager {
  static AdProvider provider() {
    switch (kAdMode.toLowerCase()) {
      case 'admob':
        return AdMobAdProvider();
      case 'pangle':
        return PangleAdProvider();
      default:
        return MockAdProvider();
    }
  }
}