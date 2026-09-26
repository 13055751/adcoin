package dev.adcoin.data;

import java.util.UUID;

/**
 * App 账号 ↔ 游戏账号 的绑定关系。
 *
 * @param playerUuid 游戏玩家 UUID
 * @param playerName 生成绑定码时的玩家名（快照，用于展示）
 * @param linkedAt   绑定时间戳（毫秒）
 */
public record Binding(String playerUuid, String playerName, long linkedAt) {

    public UUID uuid() {
        return UUID.fromString(playerUuid);
    }
}