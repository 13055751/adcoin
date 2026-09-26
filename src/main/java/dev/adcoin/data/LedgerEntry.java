package dev.adcoin.data;

/**
 * 奖励账本条目（以 txId 唯一，用于幂等与审计）。
 *
 * @param txId      后端会话 ID（防重放，全局唯一）
 * @param playerUuid 受益玩家
 * @param appUserId  完成广告的 App 账号
 * @param amount     实际发放金额（ad-units 覆盖后）
 * @param adNetwork  广告网络，如 admob/pangle/unity
 * @param adUnitId   广告单元 ID（可为空）
 * @param ts         服务器记账时间戳（毫秒）
 */
public record LedgerEntry(String txId, String playerUuid, String appUserId,
                          double amount, String adNetwork, String adUnitId, long ts) {
}