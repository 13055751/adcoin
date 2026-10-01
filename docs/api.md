# AdCoin HTTP API 契约

所有 `/api/v1/*` 端点：`POST`，`Content-Type: application/json`，请求体 UTF-8（≤64KB）。
除 `/health` 外全部需要**签名**。

## 签名算法（与插件 `Signer` 完全一致）

```
canonical = <端点前缀串>          // 每个端点有固定格式，见下
sig       = hex( HMAC-SHA256( apiKey, canonical ) )   // 小写 hex
```

- `apiKey` = `config.yml` 的 `server.api-key`（UTF-8 字节）。
- 请求体必须含 `ts`（Unix 毫秒）。服务器接受偏差 ≤ `server.ts-window-seconds`（默认 300s）。
- `sig` 字段 = 上述 hex。
- **金额规范化**：canonical 里的金额 = JSON 数值解析后的十进制字符串。
  JS 侧用 `String(Number(x))`（`50.0` → `"50"`，`50.5` → `"50.5"`）；
  Java 侧同样去尾零，两侧一致。
- 幂等：`txId` 全局唯一，重复提交返回 `duplicate` 且不重复入账/扣款。

### canonical 串（逐字，`\n` 为真实换行）

| 端点 | canonical |
|---|---|
| `/api/v1/link` | `link\n{code大写}\n{appUserId}\n{ts}` |
| `/api/v1/unlink` | `unlink\n{appUserId}\n{ts}` |
| `/api/v1/reward` | `reward\n{txId}\n{appUserId}\n{ts}\n{amount}\n{adNetwork}\n{adUnitId}` |
| `/api/v1/friend` | `friend\n{action}\n{appUserId}\n{otherAppUserId}\n{ts}`（list 时 otherAppUserId 为空串） |
| `/api/v1/transfer` | `transfer\n{txId}\n{fromAppUserId}\n{toAppUserId}\n{ts}\n{amount}` |
| `/api/v1/balance` | `balance\n{appUserId}\n{ts}` |
| `/api/v1/top` | `top\n{ts}` |
| `/api/v1/ledger` | `ledger\n{appUserId}\n{ts}` |

> `adNetwork`/`adUnitId` 缺省时在 canonical 中为空串（仍保留换行）。
> 完整可运行示例见 `examples/backend-node.mjs`。

---

## POST /api/v1/link

App 端提交游戏内生成的绑定码，绑定 App 账号与游戏账号。

请求：
```json
{ "code": "ABC12345", "appUserId": "user_123", "ts": 1730000000000, "sig": "<hex>" }
```

响应：
- `200`：`{ "ok": true, "playerName": "Steve", "playerUuid": "..." }`（游戏内在线会收到通知）
- `404 code_invalid` / `410 code_expired` / `409 app_already_bound|player_already_bound`
- `401 bad_signature|expired` / `403 forbidden` / `400 bad_json|missing_field:*`

## POST /api/v1/unlink

请求：`{ "appUserId", "ts", "sig" }` → `200 { "ok": true, "unlinked": bool }`

## POST /api/v1/reward

后端**完成广告 SDK SSV 验真后**上报一次奖励。幂等（txId）。

请求：
```json
{
  "txId": "ssv-<广告会话ID>", "appUserId": "user_123",
  "adNetwork": "admob", "adUnitId": "ca-app-pub-xxx/yyy", "amount": 50,
  "ts": 1730000000000, "sig": "<hex>"
}
```

响应：
- `200`：`{ "ok": true, "credited": true, "playerName": "Steve", "balance": 150, "dailyUsed": 3 }`
- `200`（幂等命中）：`{ "ok": true, "credited": false, "duplicate": true, "playerName": "...", "balance": 150 }`
- `429 daily_limit`（附 `playerName`/`balance`）/ `400 amount_invalid` / `404 not_linked`

金额规则：
- `currency.ad-units` 命中 `adUnitId` 时以配置金额为准（忽略请求内 `amount`）；
- 否则用请求内 `amount`，必须 `> 0` 且 `≤ currency.max-amount`。

## POST /api/v1/friend

App 端好友关系管理（双向确认）。

请求：`{ "action": "...", "appUserId": "...", "otherAppUserId": "..."(list 可省), "ts", "sig" }`

| action | 语义 | 成功响应 |
|---|---|---|
| `request` | appUserId 请求加 otherAppUserId 为好友 | `200 { ok, pending:true|false, alreadyPending?, alreadyFriends? }` |
| `accept` | 接受对方请求（需存在 pending） | `200 { ok: true }`（双方在线通知"成为好友"） |
| `reject` | 拒绝/忽略请求（幂等） | `200 { ok: true }` |
| `remove` | 解除好友 | `200 { ok, removed: bool }` |
| `list` | 好友列表（含在线状态、appUserId） | `200 { ok, friends: [{ uuid, name, online, appUserId? }] }` |
| `pending` | 我的待处理好友请求 | `200 { ok, requests: [{ uuid, name, appUserId? }] }` |

错误：`404 not_linked|no_request`、`400 self|bad_action`。

## POST /api/v1/balance

查询绑定与余额（App「我的/首页」刷新用）。

请求：`{ "appUserId", "ts", "sig" }`

响应：
- 已绑定：`200 { ok, linked: true, playerName, playerUuid, balance, dailyUsed, dailyLimit }`
  （`dailyUsed` = 今日已看次数；`dailyLimit` = 插件 `limits.daily-per-player`）
- 未绑定：`200 { ok, linked: false }`

## POST /api/v1/top

余额排行榜。请求：`{ "ts", "sig" }` → `200 { ok, top: [{ uuid, name, balance }] }`（降序前10）

## POST /api/v1/ledger

最近账本记录（App「最近动态」时间线）。

请求：`{ "appUserId", "ts", "sig" }`

响应：
- `200 { ok, linked: true, entries: [{ txId, amount, adNetwork, adUnitId, ts, fromAppUserId? }] }`（时间正序，最多 20 条）
  - `adNetwork` 为 `transfer` = 收到好友转币（`fromAppUserId` 为转出方）；其余 = 广告奖励（`adNetwork` 为广告平台）
- 未绑定：`200 { ok, linked: false, entries: [] }`

## POST /api/v1/transfer

App 端好友转币（幂等：txId 唯一）。

请求：
```json
{ "txId": "transfer-<ID>", "fromAppUserId": "user_a", "toAppUserId": "user_b",
  "amount": 30, "ts": 1730000000000, "sig": "<hex>" }
```

响应：
- `200`：`{ "ok": true, "fromBalance": 70, "toBalance": 30, "toName": "Alex" }`
- `403 not_friends`（`social.require-friend=true` 且非好友）/ `400 insufficient|min|self|invalid`
- `200`（幂等命中）：`{ "ok": false, "error": "duplicate" }`

## GET /health

无签名。`200 { "ok": true, "plugin": "AdCoin", "version": "..." }`。受 `allowed-ips` 白名单约束。

## 通用错误

所有错误统一：`{ "ok": false, "error": "<code>" }`
`401 bad_signature` / `401 expired` / `403 forbidden` / `405 method_not_allowed` / `413 payload_too_large` / `500 internal`
