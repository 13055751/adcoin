import crypto from 'node:crypto';
import { config } from './config.js';

/**
 * 广告完成真实性验证。
 *
 * - mock  ：直接通过（联调用，App 里选"模拟广告"）
 * - admob ：真实 AdMob：广告平台把 SSV 回调 POST 到 /ssv/admob（需 AdMob 开发者账号 + 公网 URL）
 * - pangle：真实 Pangle：App 上报 transaction_id，后端调 Pangle 服务器验证接口
 *
 * 真实模式下未配置密钥 → 返回 501 并提示（申请到账号后填入 .env 即启用）。
 */

/** 校验一次广告 claim；返回 {ok} 或抛错误。 */
export async function verifyAdClaim({ platform, adUnitId, transactionId }) {
  switch (platform) {
    case 'mock':
      return { ok: true };
    case 'admob':
      throw notConfigured('AdMob SSV', 'ADMOB_SSV_SECRET');
    case 'pangle':
      throw notConfigured('Pangle', 'PANGLE_APP_ID / PANGLE_SDK_SECRET');
    default:
      throw err(400, 'unknown_platform', '未知广告平台: ' + platform);
  }
}

/**
 * AdMob SSV 回调（广告平台 POST /ssv/admob?key_id=..&signature=..&reward_amount=..&transaction_id=..）。
 * 真实实现需从 Google 拉取公钥做 RSA 验签 + 校验 transaction_id 幂等后调插件发币。
 * 骨架代码：拿到密钥后补全 `verifySignature` 即可。
 */
export async function handleAdmobSsv(query) {
  if (config.adMode !== 'admob') {
    throw err(404, 'not_enabled', 'AD_MODE 不是 admob，SSV 端点未启用');
  }
  if (!config.admobSsvSecret) {
    throw notConfigured('AdMob SSV', 'ADMOB_SSV_SECRET');
  }
  // 真实场景：
  //  1) 用 https://www.gstatic.com/admob/reward/verifier-keys.json 的公钥按 key_id 验签
  //  2) 校验 query.reward_amount / transaction_id 一致性
  //  3) 返回 { transactionId, adUnitId, rewardAmount }
  // 此处以"密钥相等"做最小占位，防止误发币。
  const expected = crypto.createHmac('sha256', config.admobSsvSecret)
    .update(String(query.transaction_id || '')).digest('hex');
  if (query.signature !== expected) {
    throw err(401, 'bad_signature', 'AdMob SSV 验签失败');
  }
  return {
    transactionId: String(query.transaction_id),
    adUnitId: String(query.ad_unit_id || ''),
    rewardAmount: Number(query.reward_amount || config.adRewardAmount),
  };
}

/**
 * Pangle 服务端验证（真实）：App 上报 transaction_id，后端调 Pangle 验证接口。
 * 官方接口（域名/鉴权以 Pangle 文档为准）：
 *   GET https://analytics.pangle.io/.../verify?transaction_id=..&app_id=..
 */
export async function verifyPangle(transactionId) {
  if (config.adMode !== 'pangle') {
    throw notConfigured('Pangle', 'AD_MODE=pangle');
  }
  if (!config.pangleAppId || !config.pangleSdkSecret) {
    throw notConfigured('Pangle', 'PANGLE_APP_ID / PANGLE_SDK_SECRET');
  }
  // 真实实现：带鉴权调 Pangle 验证接口确认该 transaction_id 有效且未被消耗，
  // 返回 { ok: true } 或抛错误。此处保留骨架。
  throw err(501, 'not_configured', 'Pangle 服务端验证接口待接入（见 docs/ad-network-guide.md）');
}

function notConfigured(name, keys) {
  return err(501, 'ad_not_configured', `${name} 未配置（需 ${keys}）。测试阶段请用 mock 模式。`);
}

function err(status, code, message) {
  const e = new Error(message || code);
  e.status = status;
  e.code = code;
  return e;
}
