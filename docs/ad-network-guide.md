# 广告平台接入指南（AdMob / Pangle）

AdCoin 默认 `AD_MODE=mock`（演示）。要跑真实广告，任选一个平台申请账号并接入。

## 一、Google AdMob（国际主流，推荐先试）

### 1. 申请
1. 注册 [AdMob](https://admob.google.com/)（需要 Google 账号 + 收款账户信息）。
2. 创建 App（Android，包名 `com.adcoin.app`）。
3. 创建**激励广告单元**（Rewarded Ad），拿到：
   - `App ID`：格式 `ca-app-pub-XXXX~YYYY`
   - 广告位 ID：格式 `ca-app-pub-XXXX/ZZZZ`
4. 创建后**必须验证 App**（Store listing 或广告单元审核），否则不展示真实广告。

### 2. 配置（App 侧）
```bash
./gradlew :app:assembleDebug \
  -Padcoin.adMode=admob \
  -Padcoin.admobAppId=ca-app-pub-XXXX~YYYY \
  -Padcoin.admobRewardedUnit=ca-app-pub-XXXX/ZZZZ
```

### 3. SSV（服务端验证，防刷）——后端
AdMob 支持广告平台直接把"奖励回调"POST 到你配置的 URL（SSV）：
1. AdMob 后台 → 激励广告单元 → **SSV 设置**：填 `https://你的公网/ssv/admob`（需 HTTPS 公网地址）。
2. 后端 `.env`：`AD_MODE=admob` + `ADMOB_SSV_SECRET`。
3. 后端 `/ssv/admob` 会收到广告平台的签名回调（`transaction_id`、`reward_amount`、`key_id`、`signature` 等）。真实生产必须验签：
   - 从 `https://www.gstatic.com/admob/reward/verifier-keys.json` 按 `key_id` 取公钥做 RSA 验签；
   - 校验 `transaction_id` 唯一（幂等），`reward_amount` 与你的配置一致。
   - 当前骨架用 `HMAC(ADMOB_SSV_SECRET, transaction_id)` 占位，**生产前必须换成官方 RSA 验签**。
4. 验签通过后后端调插件 `/api/v1/reward` 发币。

> 测试广告位（无需账号）：`ca-app-pub-3940256099942544/5224354917`（激励视频测试位，App ID `~3347511713`）。
> 测试设备：AdMob 后台添加测试设备 ID 可免审核看到测试广告。

## 二、Pangle 穿山甲（国内主流）

### 1. 申请
1. 注册 [穿山甲](https://www.pangle.cn/)（需国内开发者主体信息）。
2. 创建 App → 创建**激励视频广告位**，拿到 `App ID` 与广告位 ID（slot ID）。
3. 按后台要求配置 SDK 初始化（App 侧）。

### 2. 配置（App 侧）
```bash
./gradlew :app:assembleDebug \
  -Padcoin.adMode=pangle \
  -Padcoin.pangleAppId=5xxxxxx \
  -Padcoin.pangleRewardedSlot=9xxxxxx
```
`PangleAdProvider.kt` 里按官方文档补全 SDK 调用（`com.bytedance.sdk.openadsdk.TTAdSdk` / `TTAdNative` / `RewardVerify`）。

### 3. 服务端验证——后端
Pangle 客户端完成广告后拿到 `transaction_id`（`onRewardVerify` 回调），App 上报后端 `/api/ad/claim`；
后端 `AD_MODE=pangle` 时调用 Pangle 服务端验证接口确认该 `transaction_id` 真实且未消耗，再调插件发币。
（验证接口地址与鉴权以穿山甲后台文档为准，`backend/src/ad-validator.js` 留有骨架。）

## 三、防刷要点（无论哪家）

1. **真实模式一律走服务端验证**：AdMob SSV 回调 / Pangle 服务端校验，绝不信任 App 客户端自报。
2. 插件侧二次防线：HMAC 签名、`ts` 新鲜度、`txId` 幂等、`daily-per-player` 每日限额。
3. 后端记录每次发币的 `transaction_id`，重复使用直接拒绝（幂等）。
4. 上线前用 1~2 个测试账号做一次真实广告全流程，确认 SSV 到账链路。

## 常见问题

- **广告不展示**：App ID 未在 AdMob 验证 / 国家地区不支持 / 测试机未加入测试设备。
- **SSV 回调没收到**：回调 URL 必须 HTTPS 公网可达；AdMob 后台 SSV 设置保存后生效。
- **两边都想上**：都申请下来后，把 `AdManager` 改成运行时策略（如 AdMob 无填充 → Pangle 兜底）。
