import 'package:flutter/material.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

import 'api_client.dart';
import 'main_shell.dart';
import 'models.dart';
import 'session_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SessionStore.load(); // 恢复本地会话
  // 静默注册匿名账号（无登录 UI；后端不可达时保持游客演示模式）
  await SessionStore.ensureAnonymousAccount();
  if (kAdMode.toLowerCase() == 'admob') {
    // AdMob 模式才初始化（需 manifest 里 APPLICATION_ID，由 CI 补丁注入）
    try {
      await MobileAds.instance.initialize();
    } catch (e) {
      debugPrint('MobileAds init failed: $e');
    }
  }
  runApp(const AdCoinApp());
}

class AdCoinApp extends StatelessWidget {
  const AdCoinApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AdCoin',
      debugShowCheckedModeBanner: false,
      // M3：霓虹紫 seed，深浅色跟随系统（fromSeed 生成全语义色）
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF7C3AED)),
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFFFDF7FF),
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF7C3AED),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      themeMode: ThemeMode.system,
      // 关键：无论登录与否都进主界面——未登录为游客（演示数据）模式，登录是可选入口。
      home: ValueListenableBuilder<Session?>(
        valueListenable: SessionStore.session,
        builder: (context, session, _) => MainShell(session: session),
      ),
    );
  }
}