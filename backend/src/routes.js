import { Router } from 'express';
import crypto from 'node:crypto';
import { db } from './db.js';
import { config } from './config.js';
import { register, login, requireAuth, publicUser, err } from './auth.js';
import { pluginApi } from './plugin.js';
import { verifyAdClaim, handleAdmobSsv } from './ad-validator.js';

export const router = Router();

// ============================================================ 用户

router.post('/api/auth/register', (req, res) => {
  try {
    const { username, password } = req.body || {};
    const out = register(username, password);
    res.status(201).json({ ok: true, ...out });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

router.post('/api/auth/login', (req, res) => {
  try {
    const { username, password } = req.body || {};
    const out = login(username, password);
    res.json({ ok: true, ...out });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

router.get('/api/me', requireAuth, async (req, res) => {
  try {
    const user = req.user;
    const pluginRes = await pluginApi.balance(user.appUserId);
    const linked = pluginRes.json.ok && pluginRes.json.linked;
    res.json({
      ok: true,
      user: publicUser(user),
      balance: linked ? pluginRes.json.balance : 0,
      dailyUsed: linked ? (pluginRes.json.dailyUsed ?? 0) : 0,
      dailyLimit: linked ? (pluginRes.json.dailyLimit ?? 20) : 20,
      adReward: config.adRewardAmount,
      linked: Boolean(user.linkedPlayerUuid),
      hasToken: Boolean(user.longToken),
    });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

router.post('/api/auth/logout', requireAuth, (req, res) => {
  db.deleteSession(req.token);
  res.json({ ok: true });
});

// ============================================================ 广告

router.post('/api/ad/claim', requireAuth, async (req, res) => {
  try {
    const { platform, adUnitId, transactionId } = req.body || {};
    if (!platform || !transactionId) {
      throw err(400, 'bad_request', '缺少 platform / transactionId');
    }
    await verifyAdClaim({ platform, adUnitId, transactionId });
    const txId = `ad-${platform}-${transactionId}`; // 幂等：同一次广告重试同 txId
    const amount = config.adRewardAmount;
    const pluginRes = await pluginApi.reward(txId, req.user.appUserId, amount, platform, adUnitId);
    if (!pluginRes.json.ok) {
      res.status(pluginRes.status || 502).json(pluginRes.json);
      return;
    }
    res.json({ ok: true, credited: pluginRes.json.credited, duplicate: !!pluginRes.json.duplicate, balance: pluginRes.json.balance });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

/** AdMob SSV 回调端点（真实模式，广告平台直连；无签名要求——由广告平台签名内容验签）。 */
router.post('/ssv/admob', async (req, res) => {
  try {
    const v = await handleAdmobSsv(req.query || {});
    const txId = `ad-admob-${v.transactionId}`;
    const pluginRes = await pluginApi.reward(txId, 'app_' + v.transactionId, v.rewardAmount, 'admob', v.adUnitId);
    res.json({ ok: pluginRes.json.ok, duplicate: !!pluginRes.json.duplicate });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

// ============================================================ 绑定

router.post('/api/link/bind', requireAuth, async (req, res) => {
  try {
    const { code } = req.body || {};
    if (!code) throw err(400, 'bad_request', '缺少 code');
    if (req.user.linkedPlayerUuid) {
      throw err(409, 'already_linked', '已绑定 ' + req.user.linkedPlayerName);
    }
    const pluginRes = await pluginApi.link(code, req.user.appUserId);
    if (!pluginRes.json.ok) {
      res.status(pluginRes.status || 502).json(pluginRes.json);
      return;
    }
    const user = db.updateUser(req.user.id, {
      linkedPlayerUuid: pluginRes.json.playerUuid,
      linkedPlayerName: pluginRes.json.playerName,
      linkedAt: Date.now(),
      longToken: pluginRes.json.longToken || null, // 短码校验通过 → 保存长期令牌
    });
    res.json({ ok: true, user: publicUser(user), longToken: user.longToken });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

// 长期令牌直接（重）绑定：换设备/重装后恢复
router.post('/api/link/bind-long', requireAuth, async (req, res) => {
  try {
    if (!req.user.longToken) throw err(404, 'no_token', '还没有可用的长期令牌（需先用短码绑定过）');
    const pluginRes = await pluginApi.linkLong(req.user.appUserId, req.user.longToken);
    if (!pluginRes.json.ok) {
      res.status(pluginRes.status || 502).json(pluginRes.json);
      return;
    }
    const user = db.updateUser(req.user.id, {
      linkedPlayerUuid: pluginRes.json.playerUuid,
      linkedPlayerName: pluginRes.json.playerName,
      linkedAt: Date.now(),
      longToken: pluginRes.json.longToken || req.user.longToken, // 轮换后的令牌
    });
    res.json({ ok: true, user: publicUser(user), longToken: user.longToken });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

router.post('/api/link/unbind', requireAuth, async (req, res) => {
  try {
    // 解绑必须出示长期令牌
    const pluginRes = await pluginApi.unlink(req.user.appUserId, req.user.longToken || '');
    if (!pluginRes.json.ok) {
      res.status(pluginRes.status || 502).json(pluginRes.json);
      return;
    }
    db.updateUser(req.user.id, { linkedPlayerUuid: null, linkedPlayerName: null, linkedAt: null, longToken: null });
    res.json({ ok: true, unlinked: pluginRes.json.unlinked });
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

// ============================================================ 好友

router.get('/api/friend/search', requireAuth, (req, res) => {
  const q = String(req.query.q || '').trim();
  if (q.length < 1) return res.json({ ok: true, results: [] });
  const results = db.searchByUsername(q)
    .filter((u) => u.id !== req.user.id && u.linkedPlayerUuid)
    .map((u) => ({ username: u.username, appUserId: u.appUserId, playerName: u.linkedPlayerName }));
  res.json({ ok: true, results });
});

router.post('/api/friend/:action', requireAuth, async (req, res) => {
  try {
    const action = String(req.params.action).toLowerCase();
    const allowed = ['request', 'accept', 'reject', 'remove', 'list', 'pending'];
    if (!allowed.includes(action)) throw err(400, 'bad_action');
    const otherAppUserId = req.body?.otherAppUserId || undefined;
    const pluginRes = await pluginApi.friend(action, req.user.appUserId, otherAppUserId);
    res.status(pluginRes.status || 200).json(pluginRes.json);
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

// ============================================================ 排行榜

router.get('/api/leaderboard', requireAuth, async (req, res) => {
  try {
    const pluginRes = await pluginApi.top();
    res.status(pluginRes.status || 200).json(pluginRes.json);
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

// ============================================================ 转账

router.get('/api/transactions', requireAuth, async (req, res) => {
  try {
    const pluginRes = await pluginApi.ledger(req.user.appUserId);
    res.status(pluginRes.status || 200).json(pluginRes.json);
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});

router.post('/api/transfer', requireAuth, async (req, res) => {
  try {
    const { toAppUserId, amount, clientTxId } = req.body || {};
    if (!toAppUserId || !amount) throw err(400, 'bad_request', '缺少 toAppUserId / amount');
    if (toAppUserId === req.user.appUserId) throw err(400, 'self');
    const txId = `tr-${req.user.id}-${clientTxId || crypto.randomBytes(8).toString('hex')}`;
    const pluginRes = await pluginApi.transfer(txId, req.user.appUserId, toAppUserId, Number(amount));
    res.status(pluginRes.status || 200).json(pluginRes.json);
  } catch (e) {
    res.status(e.status || 500).json({ ok: false, error: e.code || 'internal' });
  }
});
