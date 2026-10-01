# AdCoin Flutter App

AdCoin 手机端（原生 Android Kotlin 版的 Flutter 重写版，与 hr-broadcast 同栈）。

## 结构

- `lib/` — 全部 Dart 代码（M3 主题 seed 霓虹紫、B 版首页、游客/演示模式、登录/好友/排行/我的）
- `pubspec.yaml` — 依赖：http / shared_preferences / google_mobile_ads
- `ci/patch_android.sh` — CI 里给 `flutter create` 脚手架打补丁（INTERNET 权限、AdMob APPLICATION_ID、启动图标覆盖）
- `ci/android-overlay/res/` — 启动图标（紫粉渐变 + 金币播放币 + Android 13 monochrome）

## 构建（CI）

`.github/workflows/build.yml` 的 Android job：

```
flutter create --org com.adcoin --project-name adcoin --platforms android /tmp/gen
覆盖 lib/ + pubspec.yaml → patch_android.sh → flutter pub get
flutter build apk --debug --dart-define=API_BASE_URL=... --dart-define=AD_MODE=...
```

产物 artifact：`adcoin-apk`。

## 构建参数（--dart-define）

| 参数 | 默认 | 说明 |
|---|---|---|
| `API_BASE_URL` | `http://10.0.2.2:8787` | 后端地址（模拟器访问宿主机） |
| `AD_MODE` | `mock` | `mock` / `admob` / `pangle` |
| `ADMOB_REWARDED_UNIT` | 官方测试广告位 | AdMob 激励视频位 |

手动触发：仓库 Actions → Build → Run workflow 填 inputs。

## 设计对照

界面为 M3（ColorScheme.fromSeed #7C3AED，深浅跟随系统）B 版布局：
大数字余额 + 趋势胶囊 + 药丸 CTA + 今日进度卡 + 快捷操作三卡 + 时间线动态 + 好友 tab 角标。
游客模式（未登录）内置演示数据，可不部署后端浏览全部页面。

## 与 Kotlin 版关系

`android-app/`（Kotlin+Compose）为上一版，已由本目录替代（用户 2026-10-01 决定改 Flutter）；
待 Flutter 版稳定后删除 android-app/。