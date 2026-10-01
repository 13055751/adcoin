package dev.adcoin.data;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * App 账号 ↔ 游戏账号 的绑定关系。
 *
 * @param playerUuid 游戏玩家 UUID
 * @param playerName 生成绑定码时的玩家名（快照，用于展示）
 * @param linkedAt   绑定时间戳（毫秒）
 * @param longToken  绑定成功后签发的长期令牌（高熵，解绑/长期重绑用；旧数据为 null）
 */
public record Binding(String playerUuid, String playerName, long linkedAt, String longToken) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** 旧数据兼容：无长期令牌。 */
    public Binding(String playerUuid, String playerName, long linkedAt) {
        this(playerUuid, playerName, linkedAt, null);
    }

    /** 高熵长期令牌：48 字节 = 96 个 hex 字符（384 bit）。 */
    public static String newLongToken() {
        byte[] buf = new byte[48];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) {
            sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return sb.toString();
    }

    public UUID uuid() {
        return UUID.fromString(playerUuid);
    }
}