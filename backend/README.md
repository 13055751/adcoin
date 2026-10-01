# AdCoin Backend（后端服务）

App 与 Minecraft 插件之间的桥梁：**用户系统 + 广告 SSV 验签 + HMAC 签名转发插件 API**。

```
手机 App ──(登录/token)──> Backend ──(HMAC 签名)──> AdCoin 插件
                        │  广告验签(SSV)
```

## 运行

```bash
cd backend
npm install
cp .env.example .env        # 按需修改
node src/server.js          # 默认 http://127.0.0.1:8787
```

联调（无插件、无广告账号也能跑通全链路）：

```bash
npm run mock-plugin         # 终端1：模拟插件（17890）
node src/server.js          # 终端2：后端（8787，AD_MODE=mock）
npm run smoke               # 全链路冒烟测试（自动起两个服务并断言）
```

## 环境变量（见 .env.example）

| 变量 | 说明 |
|---|---|
| `PORT` | 后端端口，默认 8787 |
| `DB_FILE` | 用户数据 JSON 路径 |
| `PLUGIN_BASE_URL` / `PLUGIN_API_KEY` | 插件地址与共享密钥（与插件 config.yml 一致） |
| `AD_MODE` | `mock`（联调）\| `admob` \| `pangle` |
| `AD_REWARD_AMOUNT` | 单次广告奖励金额 |
| `ADMOB_SSV_SECRET` / `PANGLE_APP_ID` / `PANGLE_SDK_SECRET` | 真实广告平台密钥 |

## API（App 调用）

| 端点 | 说明 |
|---|---|
| `POST /api/auth/register` `{username,password}` | 注册 → `{token, user}` |
| `POST /api/auth/login` | 登录 → `{token, user}` |
| `GET /api/me` | 用户信息 + 余额 + 绑定状态（`balance/dailyUsed/dailyLimit/adReward`，需 Bearer token） |
| `POST /api/ad/claim` `{platform,transactionId,adUnitId}` | 看广告完成上报 → 验真 → 插件发币（幂等） |
| `POST /api/link/bind` `{code}` | 输入游戏内绑定码完成绑定 |
| `POST /api/link/unbind` | 解绑 |
| `GET /api/friend/search?q=` | 按用户名搜玩家（需已绑定） |
| `POST /api/friend/:action` | `request\|accept\|reject\|remove\|list\|pending` |
| `POST /api/transfer` `{toAppUserId,amount,clientTxId}` | 好友转币（幂等） |
| `GET /api/leaderboard` | adcoins 持有榜（插件 /api/v1/top 透传） |
| `GET /api/transactions` | 我的最近账本动态（插件 /api/v1/ledger 透传） |
| `GET /health` | 存活检查 |

## 广告验证模式

- **mock**：看广告直接发币（联调/演示）。
- **admob**：AdMob 广告平台把 SSV 回调 POST 到你的公网 `/ssv/admob`，后端验签后发币（需 AdMob 开发者账号，申请指南见 [docs/ad-network-guide.md](../docs/ad-network-guide.md)）。
- **pangle**：App 上报 `transaction_id`，后端调 Pangle 服务端验证（需穿山甲账号）。

> 幂等：`transaction_id` 派生唯一 `txId`，重试不会重复发币；转账用 `clientTxId` 同理。
