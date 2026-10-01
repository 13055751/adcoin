/// 未登录（游客）模式下的演示数据——没有后端也能浏览全部页面。
library;

class DemoFriend {
  final String name;
  final bool online;
  final double balance;
  const DemoFriend(this.name, this.online, this.balance);
}

class DemoTx {
  final String title;
  final String time;
  final String amount;
  final bool? positive; // true=收入绿 false=支出红 null=中性
  const DemoTx(this.title, this.time, this.amount, this.positive);
}

class DemoData {
  static const double balance = 1288;
  static const String linkedName = 'Steve（演示）';
  static const int dailyUsed = 3;
  static const int dailyLimit = 20;
  static const double adReward = 50;
  static const String adMode = 'mock';

  static const friends = [
    DemoFriend('Alex', true, 320),
    DemoFriend('Notch', false, 1500),
    DemoFriend('Herobrine', true, 66),
  ];

  static const leaderboard = [
    ('Notch', 1500.0),
    ('Steve（演示）', 1288.0),
    ('Alex', 320.0),
    ('Herobrine', 66.0),
  ];

  static const transactions = [
    DemoTx('看广告获得奖励', '09:20', '+50', true),
    DemoTx('转出给 Alex', '09:15', '-20', false),
    DemoTx('看广告获得奖励', '昨天 21:04', '+50', true),
    DemoTx('绑定游戏账号 Steve', '昨天 20:58', '✓', null),
  ];

  /// 演示玩家在排行榜中的名次（1-based）。
  static int demoRank() {
    final i = leaderboard.indexWhere((e) => e.$1 == 'Steve（演示）');
    return i < 0 ? 0 : i + 1;
  }
}