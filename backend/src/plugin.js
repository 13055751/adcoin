import { callPlugin, canonical } from './signer.js';

/** 插件 API 转发封装。 */
export const pluginApi = {
  reward(txId, appUserId, amount, adNetwork, adUnitId) {
    return callPlugin('/api/v1/reward', { txId, appUserId, amount, adNetwork, adUnitId },
      (ts) => canonical.reward(txId, appUserId, ts, amount, adNetwork, adUnitId));
  },

  link(code, appUserId) {
    return callPlugin('/api/v1/link', { code, appUserId },
      (ts) => canonical.link(code, appUserId, ts));
  },

  linkLong(appUserId, longToken) {
    return callPlugin('/api/v1/link-long', { appUserId, longToken },
      (ts) => canonical.linkLong(appUserId, longToken, ts));
  },

  unlink(appUserId, longToken) {
    return callPlugin('/api/v1/unlink', { appUserId, longToken: longToken ?? '' },
      (ts) => canonical.unlink(appUserId, longToken, ts));
  },

  friend(action, appUserId, otherAppUserId) {
    return callPlugin('/api/v1/friend', { action, appUserId, otherAppUserId },
      (ts) => canonical.friend(action, appUserId, otherAppUserId, ts));
  },

  transfer(txId, fromAppUserId, toAppUserId, amount) {
    return callPlugin('/api/v1/transfer', { txId, fromAppUserId, toAppUserId, amount },
      (ts) => canonical.transfer(txId, fromAppUserId, toAppUserId, ts, amount));
  },

  balance(appUserId) {
    return callPlugin('/api/v1/balance', { appUserId },
      (ts) => canonical.balance(appUserId, ts));
  },

  top() {
    return callPlugin('/api/v1/top', {},
      (ts) => canonical.top(ts));
  },

  ledger(appUserId) {
    return callPlugin('/api/v1/ledger', { appUserId },
      (ts) => canonical.ledger(appUserId, ts));
  },
};
