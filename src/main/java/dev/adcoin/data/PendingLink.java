package dev.adcoin.data;

/**
 * 游戏内生成、等待 App 端输入的一次性绑定码。
 *
 * @param playerUuid 生成该码的玩家 UUID
 * @param playerName 生成该码时的玩家名（快照）
 * @param expiresAt  过期时间戳（毫秒）
 */
public record PendingLink(String playerUuid, String playerName, long expiresAt) {
}