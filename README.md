# AdCoin · 看广告赚游戏币（Paper 插件 + Android App + 后端）

完整三端方案：**Android 手机 App** 看广告赚游戏内独立货币 `adcoins`，**Node 后端**做用户系统与广告验签，**Paper 服务器插件**发币并支持好友社交（列表/在线状态/私聊/转币）。

```
Android App（看广告/绑定/好友/转账）        Node 后端（backend/）              Paper 插件（本仓库）
┌─────────────────────────────┐  登录/token   ┌──────────────────────┐  HMAC 签名   ┌────────────────────────────┐
│ 注册/登录                     │ ────────────→ │ 用户系统(JSON)        │ ────────────→ │ HTTP API（内置）            │
│ 看广告(AdMob/Pangle/Mock)     │  广告完成上报  │ 广告 SSV 验真          │  /reward    │  /api/v1/reward → 发币      │
│ 绑定码 → 绑定游戏账号          │ ────────────→ │ (AdMob SSV/Pangle)   │ ────────────→ │  /api/v1/link → 绑定        │
│ 好友搜索/请求/接受/转账        │ ────────────→ │ 转发好友/转币/排行榜    │  /friend    │  /api/v1/friend → 好友      │
└─────────────────────────────┘               └──────────────────────┘  /transfer   │  /api/v1/transfer → 转币    │
                                                                                    │  /api/v1/balance → 余额查询  │
                                                                                    │  /api/v1/top → 排行榜        │
                                                                                    └────────────────────────────┘
```

**目录**：
- `android-app/` — 原生 Android（Kotlin + Jetpack Compose）客户端
- `backend/` — Node.js 后端（用户系统、广告 SSV 验签、HMAC 签名转发插件 API，含 mock 插件与冒烟测试）
- 本目录顶层 — Paper 服务器插件（Maven 工程）

## 特性

- **广告奖励**：App 看广告 → 后端 SSV 验真（AdMob SSV 回调 / Pangle 服务端验证 / mock）→ 插件发 adcoins。
  - 离线也能累积：奖励直接写入服务器侧余额。
  - **防刷**：HMAC-SHA256 共享密钥签名 + 时间戳新鲜度窗口 + txId 幂等账本 + 每玩家每日次数限额 + 后端 transaction_id 幂等。
- **独立货币**：adcoins 由插件自身记账（JSON 原子持久化），不碰服务器主经济；可配置把奖励按百分比**镜像到 Vault 主余额**（`economy.vault`）。
- **账号绑定**：游戏内 `/adlink` 生成一次性绑定码（默认 8 位、5 分钟有效），App 输入完成绑定（`/api/v1/link`）。
- **好友社交**（游戏内为主，App 双向确认）：
  - App 发起/接受/拒绝好友请求（`/api/v1/friend` request/accept/reject/remove/list/pending）
  - 游戏内 `/friend list`（在线状态+余额）、`/friend pending`、`/friend msg <名> <话>`（好友私聊）、`/friend transfer <名> <金额>`（好友转币，幂等账本）
  - 默认只有好友之间可私聊/转币（`social.require-friend`，可关）
- **App 端**：登录/注册、余额、看广告（AdMob+Pangle 双平台可配 + mock）、绑定、好友搜索/请求/接受/转账、排行榜、解绑。
- **占位符**：`%adcoin_balance%`（PlaceholderAPI，可选）。
- **事件**：`AdRewardEvent`（入账后主线程触发），供其他插件扩展。

## 技术栈与假设

| 项 | 值 |
|---|---|
| 服务器 | **Paper 1.21+**（api-version 1.21） |
| 插件 | Java 21，Maven（Gson 已 shade + relocate 到 `dev.adcoin.libs.gson`），Vault/PAPI 可选 |
| 后端 | Node.js 18+（仅依赖 express），JSON 文件存储，零数据库 |
| App | Android（minSdk 26），Kotlin 2.0 + Jetpack Compose + Retrofit + DataStore + AdMob/Pangle SDK |

## 构建与部署

### 1. 插件（本目录）

```bash
export JAVA_HOME=$HOME/tools/jdk21        # 本机无系统 Java 时
$HOME/tools/maven/bin/mvn -s .mvn-settings.xml clean package
# 产物 target/adcoin.jar → 放入服务器 plugins/，改 config.yml 的 api-key
```

> `.mvn-settings.xml`：本机 `~/.m2/settings.xml` 有 aliyun 镜像，会劫持 jitpack/extendedclip；此文件让 aliyun 只管 central，papermc/jitpack/extendedclip 直连。

### 2. 后端

```bash
cd backend && npm install
cp .env.example .env   # 填 PLUGIN_API_KEY（与插件一致），AD_MODE=mock 先联调
node src/server.js     # 默认 127.0.0.1:8787
# 联调/验证：
npm run mock-plugin &  # 模拟插件（17890）
npm run smoke          # 全链路冒烟测试（21 项断言）
```

### 3. Android App

用 Android Studio 打开 `android-app/`；默认 `adMode=mock`、后端 `http://10.0.2.2:8787`（模拟器访问宿主机）。
真机联调：`adb reverse tcp:8787 tcp:8787`。广告接入见 [docs/ad-network-guide.md](docs/ad-network-guide.md)。

## HTTP API（插件，后端调用）

| 端点 | 用途 | 关键字段 |
|---|---|---|
| `POST /api/v1/link` | 绑定码兑换 | `code, appUserId` |
| `POST /api/v1/unlink` | 解绑 | `appUserId` |
| `POST /api/v1/reward` | 广告奖励（幂等） | `txId, appUserId, amount, adNetwork?, adUnitId?` |
| `POST /api/v1/friend` | 好友 request/accept/reject/remove/list/pending | `action, appUserId, otherAppUserId?` |
| `POST /api/v1/transfer` | 好友转币（幂等） | `txId, fromAppUserId, toAppUserId, amount` |
| `POST /api/v1/balance` | 查询绑定与余额 | `appUserId` |
| `POST /api/v1/top` | 排行榜 | — |
| `GET /health` | 存活检查 | — |

完整契约与签名算法见 [docs/api.md](docs/api.md)。后端对 App 的接口见 [backend/README.md](backend/README.md)。

## 安全提示（务必读）

- 插件 HTTP API **不要直接暴露公网**：绑定 `127.0.0.1` 配反向代理（TLS），或 `0.0.0.0` + 防火墙只放行后端 IP，加 `server.allowed-ips` 白名单。
- `api-key` 是插件↔后端的信任边界；后端必须**先做广告 SSV 验真**再调插件（App 客户端上报一律不可信）。
- 广告账号申请与真实接入步骤见 [docs/ad-network-guide.md](docs/ad-network-guide.md)。

## 游戏内命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/adlink` | `adcoin.link`(默认true) | 生成绑定码 |
| `/adlink status` / `/adlink unlink` | 同上 | 查看绑定 / 解绑 |
| `/friend list` / `/friend pending` | `adcoin.friend`(默认true) | 好友列表 / 待处理请求 |
| `/friend msg <玩家> <话>` | 同上 | 好友私聊（在线） |
| `/friend transfer <玩家> <金额>` | 同上 | 好友转币 |
| `/adcoin balance/give/take/set/top/unlink/reload` | `adcoin.admin`(默认op) | 管理命令 |

## 首轮测试清单（真实服务器）

1. 插件加载无异常，`curl http://127.0.0.1:17890/health` 正常。
2. 后端 `AD_MODE=mock` + `npm run smoke` 全绿（已在本仓库验证）。
3. App（模拟器）注册登录 → 游戏内 `/adlink` → App 输入绑定码 → 看广告（mock）→ 余额增加。
4. 两个账号互加好友 → 转账 → 双方余额变化。
5. 错误路径：坏签名/过期 ts/重复 txId/非好友转账。
6. 重启服务器：余额/绑定/好友不丢。
