import crypto from 'node:crypto';
import { config } from './config.js';

/**
 * 调用插件 API：HMAC-SHA256 签名（与插件 Signer / docs/api.md 完全一致）。
 * callPlugin 统一生成 ts 并签名，避免 canonical 与 body 时间戳不一致。
 */

export function formatAmount(v) {
  return String(Number(v));
}

export const canonical = {
  reward: (txId, appUserId, ts, amount, adNetwork, adUnitId) =>
    `reward\n${txId}\n${appUserId}\n${ts}\n${formatAmount(amount)}\n${adNetwork ?? ''}\n${adUnitId ?? ''}`,
  link: (code, appUserId, ts) => `link\n${String(code).toUpperCase()}\n${appUserId}\n${ts}`,
  unlink: (appUserId, ts) => `unlink\n${appUserId}\n${ts}`,
  friend: (action, appUserId, other, ts) =>
    `friend\n${action}\n${appUserId}\n${other ?? ''}\n${ts}`,
  transfer: (txId, fromApp, toApp, ts, amount) =>
    `transfer\n${txId}\n${fromApp}\n${toApp}\n${ts}\n${formatAmount(amount)}`,
  balance: (appUserId, ts) => `balance\n${appUserId}\n${ts}`,
  top: (ts) => `top\n${ts}`,
};

/**
 * @param {string} path 插件端点
 * @param {object} body 请求体（不含 ts/sig）
 * @param {(ts:number)=>string} buildCanonical canonical 构造器
 */
export async function callPlugin(path, body, buildCanonical) {
  const ts = Date.now();
  const payload = { ...body, ts };
  payload.sig = crypto.createHmac('sha256', config.pluginApiKey)
    .update(buildCanonical(ts))
    .digest('hex');
  let res;
  try {
    res = await fetch(config.pluginBaseUrl + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  } catch (e) {
    return { status: 0, json: { ok: false, error: 'plugin_unreachable', message: String(e) } };
  }
  const json = await res.json().catch(() => ({ ok: false, error: 'non_json' }));
  return { status: res.status, json };
}
