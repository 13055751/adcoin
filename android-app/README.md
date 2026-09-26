# AdCoin Android App（原生 Kotlin + Jetpack Compose）

手机端：**看广告赚 adcoins**、**绑定游戏账号**、**好友社交**（搜索/请求/接受/转账）、**排行榜**。

```
模拟器访问宿主机: 后端地址默认 http://10.0.2.2:8787（可在 gradle 属性覆盖）
真机联调: adb reverse tcp:8787 tcp:8787 或 -Padcoin.apiBaseUrl=http://<你的IP>:8787
```

## 构建

用 **Android Studio**（Koala+，AGP 8.5.2 / JDK 17）打开 `android-app/` 目录即可；或命令行：

```bash
cd android-app
./gradlew :app:assembleDebug        # 需要 Android SDK；产物 app/build/outputs/apk/debug/app-debug.apk
```

> 本仓库不包含 `gradlew` 脚本与 wrapper jar，Android Studio 打开时会自动补全；也可自行 `gradle wrapper`。

## 广告模式（-P 参数）

| 参数 | 默认 | 说明 |
|---|---|---|
| `adcoin.adMode` | `mock` | `mock`（演示，无需账号）\| `admob` \| `pangle` |
| `adcoin.apiBaseUrl` | `http://10.0.2.2:8787` | 后端地址 |
| `adcoin.admobAppId` | 官方测试 ID | 真实 AdMob App ID |
| `adcoin.admobRewardedUnit` | 官方测试广告位 | 真实激励视频广告位 |
| `adcoin.pangleAppId` / `adcoin.pangleRewardedSlot` | 空 | 真实穿山甲 App ID / 广告位 |

用法：`./gradlew :app:assembleDebug -Padcoin.adMode=admob -Padcoin.admobAppId=ca-app-pub-xxxx`（或写入 `~/.gradle/gradle.properties`）。

## 界面

- **登录/注册**：连后端 `/api/auth/*`
- **首页**：余额大卡、看广告按钮（当前广告 Provider）、绑定游戏账号（输入游戏内 `/adlink` 得到的 8 位绑定码）、解绑
- **好友**：按用户名搜索添加、待处理请求（接受/拒绝）、好友列表（在线状态），**点击好友转账**
- **排行**：`/api/leaderboard`
- **我的**：账号信息、广告模式、后端地址、解绑、退出

## 广告 SDK 接入状态

- **Mock**：完整可用（弹窗模拟，配合后端 `AD_MODE=mock` 全链路联调）。
- **AdMob**：`AdMobAdProvider` 已按官方 API 写好（RewardedAd 加载/展示/回调），配测试 ID 即可出广告。SSV 由广告平台回调后端 `/ssv/admob`（真实发币以 SSV 为准）。
- **Pangle**：`PangleAdProvider` 保留类型安全外壳 + 注释骨架（`com.bytedance.sdk.openadsdk` 类），申请到穿山甲账号后按官方文档取消注释补全即可。

> 两平台都接上后，`AdManager` 可按 `AD_MODE` 切换；也可以改为运行时智能选择（如 AdMob 无填充 → 回落 Pangle）。

## 联调（全链路）

```bash
# 1) 起模拟插件 + 后端（backend 目录）
npm run mock-plugin & node src/server.js
# 2) 起 App（Android Studio 模拟器），登录 → 游戏内 /adlink → App 绑定码 → 看广告 → 好友 → 转账
```

真实服务器联调：把 `PLUGIN_BASE_URL/PLUGIN_API_KEY` 指向真实 Paper 插件的地址与密钥，`AD_MODE=mock` 仍可先跑通；广告账号到位后切真实模式（见 [docs/ad-network-guide.md](../docs/ad-network-guide.md)）。
