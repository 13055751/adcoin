#!/usr/bin/env node
/**
 * AdCoin 后端调用示例（Node 18+，零依赖）。
 *
 * 真实流程：
 *   1) App 上报广告完成 → 你的后端去广告 SDK 做 SSV 验真（签名/请求真实性）
 *   2) 验真通过后，用本文件的 signAndPost 调插件 API 发奖励 / 好友 / 转币
 *
 * 环境变量：
 *   ADCOIN_API_KEY  与插件 config.yml server.api-key 一致
 *   ADCOIN_BASE    如 http://127.0.0.1:17890
 *
 * 运行演示：ADCOIN_API_KEY=xxx ADCOIN_BASE=http://127.0.0.1:17890 node backend-node.mjs
 */

const crypto = require('node:crypto');

const API_KEY = process.env.ADCOIN_API_KEY || 'CHANGE-ME-PLEASE-USE-A-LONG-RANDOM-SECRET-STRING';
const BASE = process.env.ADCOIN_BASE || 'http://127.0.0.1:17890';

/** 金额规范化：JSON 数值 → 无尾零十进制字符串（与服务端 formatAmount 一致） */
function amountCanon(amount) {
  return String(Number(amount));
}

/**
 * 对请求体签名并 POST。
 * @param {string} path  '/api/v1/reward'
 * @param {object} body  除 sig 外全部字段（ts 未给则自动填 Date.now()）
 * @param {string} canonical  canonical 串（服务端文档逐字构造）
 */
async function signAndPost(path, body, canonical) {
  const payload = { ...body, ts: body.ts ?? Date.now() };
  const sig = crypto.createHmac('sha256', API_KEY).update(canonical).digest('hex');
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...payload, sig }),
  });
  const json = await res.json().catch(() => ({ ok: false, error: 'non-json' }));
  console.log(`[${res.status}] ${path} ->`, JSON.stringify(json));
  return { status: res.status, json };
}

// ---------------------------------------------------------------- canonical 构造（与 docs/api.md 一致）

const link = (code, appUserId, ts) => `link\n${code.toUpperCase()}\n${appUserId}\n${ts}`;
const unlink = (appUserId, ts) => `unlink\n${appUserId}\n${ts}`;
const reward = (txId, appUserId, ts, amount, adNetwork, adUnitId) =>
  `reward\n${txId}\n${appUserId}\n${ts}\n${amountCanon(amount)}\n${adNetwork ?? ''}\n${adUnitId ?? ''}`;
const friend = (action, appUserId, otherAppUserId, ts) =>
  `friend\n${action}\n${appUserId}\n${otherAppUserId ?? ''}\n${ts}`;
const transfer = (txId, fromApp, toApp, ts, amount) =>
  `transfer\n${txId}\n${fromApp}\n${toApp}\n${ts}\n${amountCanon(amount)}`;

// ---------------------------------------------------------------- 便捷封装

async function apiLink(code, appUserId) {
  const ts = Date.now();
  return signAndPost('/api/v1/link', { code, appUserId }, link(code, appUserId, ts));
}

async function apiReward(txId, appUserId, amount, adNetwork, adUnitId) {
  const ts = Date.now();
  return signAndPost('/api/v1/reward', { txId, appUserId, amount, adNetwork, adUnitId },
    reward(txId, appUserId, ts, amount, adNetwork, adUnitId));
}

async function apiFriend(action, appUserId, otherAppUserId) {
  const ts = Date.now();
  return signAndPost('/api/v1/friend', { action, appUserId, otherAppUserId },
    friend(action, appUserId, otherAppUserId, ts));
}

async function apiTransfer(txId, fromAppUserId, toAppUserId, amount) {
  const ts = Date.now();
  return signAndPost('/api/v1/transfer', { txId, fromAppUserId, toAppUserId, amount },
    transfer(txId, fromAppUserId, toAppUserId, ts, amount));
}

// ---------------------------------------------------------------- 演示（按需放开）

async function demo() {
  console.log('健康检查:', await (await fetch(`${BASE}/health`)).json());

  // 1. 绑定（code 为游戏内 /adlink 生成）
  // await apiLink('ABC12345', 'user_123');

  // 2. 广告奖励（txId 必须全局唯一，重发同 txId 不会重复发币）
  // await apiReward('ssv-20260925-001', 'user_123', 50, 'admob', 'ca-app-pub-xxx/yyy');

  // 3. 好友
  // await apiFriend('request', 'user_123', 'user_456');
  // await apiFriend('accept', 'user_456', 'user_123');
  // await apiFriend('list', 'user_123');

  // 4. 转币（双方须为好友）
  // await apiTransfer('transfer-20260925-001', 'user_123', 'user_456', 30);
}

if (require.main === module) {
  demo().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}

module.exports = { apiLink, apiReward, apiFriend, apiTransfer, signAndPost };
