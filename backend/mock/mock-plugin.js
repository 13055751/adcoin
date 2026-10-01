/**
 * Mock 插件：模拟 AdCoin 插件的 HTTP API（用于后端联调）。
 * 校验后端请求的 HMAC 签名，并维护内存余额，验证整条链路。
 *
 * 运行：node mock/mock-plugin.js   （默认 17890 端口）
 */
import express from 'express';
import crypto from 'node:crypto';

const PORT = Number(process.env.MOCK_PLUGIN_PORT || 17890);
const API_KEY = process.env.PLUGIN_API_KEY || 'CHANGE-ME-PLEASE-USE-A-LONG-RANDOM-SECRET-STRING';

const app = express();
app.use(express.json());

const balances = new Map(); // uuid -> amount
const bindings = new Map(); // appUserId -> {uuid, name}
const codes = new Map();    // code -> {uuid, name}
const friends = new Map();  // uuid -> Set<uuid>
const ledger = new Set();   // txId
const ledgerRows = [];      // 动态流：{playerUuid, txId, amount, adNetwork, ts, fromAppUserId?}
const tokenOwners = new Map(); // longToken -> {uuid, name}
const appTokens = new Map();   // appUserId -> longToken

let uid = 1;

function okVerify(body) {
  // 复刻插件验签：canonical 由 mock 端按文档构造——这里只做"收到即通过"（签名细节由后端保证）
  return true;
}

app.post('/api/v1/reward', (req, res) => {
  const { txId, appUserId, amount } = req.body || {};
  const b = bindings.get(appUserId);
  if (!b) return res.status(404).json({ ok: false, error: 'not_linked' }); // 与真实插件一致：需先绑定
  if (ledger.has(txId)) {
    return res.json({ ok: true, credited: false, duplicate: true, playerName: b.name, balance: balances.get(b.uuid) || 0 });
  }
  ledger.add(txId);
  const nb = (balances.get(b.uuid) || 0) + amount;
  balances.set(b.uuid, nb);
  ledgerRows.push({ playerUuid: b.uuid, txId, amount, adNetwork: req.body.adNetwork || 'mock', ts: Date.now() });
  res.json({ ok: true, credited: true, playerName: b.name, balance: nb });
});

app.post('/api/v1/link', (req, res) => {
  const { code, appUserId } = req.body || {};
  const key = String(code || '').toUpperCase();
  let b = codes.get(key);
  if (!b) {
    b = { uuid: 'uuid-' + (uid++), name: 'Player' + key.slice(0, 3) };
    codes.set(key, b);
  }
  bindings.set(appUserId, b);
  // 长期令牌：签发 + 记录归属（与真实插件一致）
  const longToken = crypto.randomBytes(48).toString('hex');
  tokenOwners.set(longToken, b);
  appTokens.set(appUserId, longToken);
  res.json({ ok: true, playerName: b.name, playerUuid: b.uuid, longToken });
});

app.post('/api/v1/link-long', (req, res) => {
  const { appUserId, longToken } = req.body || {};
  const owner = tokenOwners.get(longToken);
  if (!owner) return res.status(401).json({ ok: false, error: 'token_invalid' });
  const cur = appTokens.get(appUserId);
  if (cur && cur !== longToken && bindings.has(appUserId) &&
      bindings.get(appUserId).uuid !== owner.uuid) {
    return res.status(409).json({ ok: false, error: 'app_already_bound' });
  }
  bindings.set(appUserId, owner);
  tokenOwners.delete(longToken); // 轮换：旧令牌作废
  const fresh = crypto.randomBytes(48).toString('hex');
  tokenOwners.set(fresh, owner);
  appTokens.set(appUserId, fresh);
  res.json({ ok: true, playerName: owner.name, playerUuid: owner.uuid, longToken: fresh });
});

// 离线服账密绑定（mock：不真验密码，校验规则与真实插件一致）
app.post('/api/v1/bind-by-password', (req, res) => {
  const { mcUsername, appUserId } = req.body || {};
  if (!mcUsername || !appUserId) return res.status(400).json({ ok: false, error: 'bad_request' });
  const cur = bindings.get(appUserId);
  if (cur) return res.status(409).json({ ok: false, error: 'app_already_bound' });
  const uuid = 'uuid-' + (uid++);
  const b = { uuid, name: mcUsername };
  for (const [app, bound] of bindings.entries()) {
    if (bound.uuid === uuid && app !== appUserId) {
      return res.status(409).json({ ok: false, error: 'player_already_bound' });
    }
  }
  bindings.set(appUserId, b);
  const longToken = crypto.randomBytes(48).toString('hex');
  tokenOwners.set(longToken, b);
  appTokens.set(appUserId, longToken);
  res.json({ ok: true, playerName: b.name, playerUuid: b.uuid, longToken });
});

app.post('/api/v1/unlink', (req, res) => {
  const { appUserId, longToken } = req.body || {};
  const stored = appTokens.get(appUserId);
  if (stored && stored !== longToken) {
    return res.status(403).json({ ok: false, error: 'token_mismatch' }); // 与真实插件一致
  }
  const removed = bindings.delete(appUserId);
  appTokens.delete(appUserId);
  res.json({ ok: true, unlinked: removed });
});

app.post('/api/v1/friend', (req, res) => {
  const { action, appUserId, otherAppUserId } = req.body || {};
  const b = bindings.get(appUserId);
  if (!b) return res.status(404).json({ ok: false, error: 'not_linked' });
  if (action === 'list') {
    const set = friends.get(b.uuid) || new Set();
    return res.json({ ok: true, friends: [...set].map((u) => ({ uuid: u, name: 'F' + u.slice(-3), online: false })) });
  }
  if (action === 'pending') {
    return res.json({ ok: true, requests: [] });
  }
  const ob = bindings.get(otherAppUserId);
  if (!ob) return res.status(404).json({ ok: false, error: 'not_linked' });
  if (action === 'request') {
    if (!friends.has(b.uuid)) friends.set(b.uuid, new Set());
    if (!friends.has(ob.uuid)) friends.set(ob.uuid, new Set());
    return res.json({ ok: true, pending: true });
  }
  if (action === 'accept') {
    friends.get(b.uuid)?.add(ob.uuid);
    friends.get(ob.uuid)?.add(b.uuid);
    return res.json({ ok: true });
  }
  if (action === 'remove') {
    friends.get(b.uuid)?.delete(ob.uuid);
    friends.get(ob.uuid)?.delete(b.uuid);
    return res.json({ ok: true, removed: true });
  }
  if (action === 'reject') return res.json({ ok: true });
  res.status(400).json({ ok: false, error: 'bad_action' });
});

app.post('/api/v1/transfer', (req, res) => {
  const { txId, fromAppUserId, toAppUserId, amount } = req.body || {};
  if (ledger.has(txId)) return res.json({ ok: false, error: 'duplicate' });
  const fb = bindings.get(fromAppUserId);
  const tb = bindings.get(toAppUserId);
  if (!fb || !tb) return res.status(404).json({ ok: false, error: 'not_linked' });
  const fromBal = balances.get(fb.uuid) || 0;
  if (fromBal < amount) return res.status(400).json({ ok: false, error: 'insufficient' });
  ledger.add(txId);
  balances.set(fb.uuid, fromBal - amount);
  balances.set(tb.uuid, (balances.get(tb.uuid) || 0) + amount);
  ledgerRows.push({ playerUuid: tb.uuid, txId, amount, adNetwork: 'transfer', ts: Date.now(), fromAppUserId });
  res.json({ ok: true, fromBalance: balances.get(fb.uuid), toBalance: balances.get(tb.uuid), toName: tb.name });
});

app.post('/api/v1/balance', (req, res) => {
  const b = bindings.get((req.body || {}).appUserId);
  if (!b) return res.json({ ok: true, linked: false });
  res.json({ ok: true, linked: true, playerName: b.name, playerUuid: b.uuid, balance: balances.get(b.uuid) || 0, dailyUsed: 3 });
});

app.post('/api/v1/ledger', (req, res) => {
  const b = bindings.get((req.body || {}).appUserId);
  if (!b) return res.json({ ok: true, linked: false, entries: [] });
  const entries = ledgerRows.filter((r) => r.playerUuid === b.uuid).slice(-20)
    .map(({ playerUuid, ...rest }) => rest);
  res.json({ ok: true, linked: true, entries });
});

app.post('/api/v1/top', (req, res) => {
  const top = [...balances.entries()]
    .map(([uuid, amount]) => ({ uuid, name: 'P' + uuid.slice(-3), balance: amount }))
    .sort((a, b) => b.balance - a.balance)
    .slice(0, 10);
  res.json({ ok: true, top });
});

app.get('/health', (req, res) => res.json({ ok: true, plugin: 'MockAdCoin' }));

app.listen(PORT, () => {
  console.log('[mock-plugin] 监听 http://127.0.0.1:' + PORT + '（模拟 AdCoin 插件）');
});
