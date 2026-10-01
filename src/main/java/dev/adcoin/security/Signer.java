package dev.adcoin.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * HMAC-SHA256 签名：在插件与后端之间验证请求真实性（配合时间戳防重放）。
 * <p>
 * canonical 串是纯文本换行拼接，规则必须与 docs/api.md 保持一致——
 * 后端照文档实现相同的拼接后计算 HMAC。
 */
public final class Signer {

    private final Mac mac;

    public Signer(String apiKey) {
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法初始化 HMAC-SHA256", e);
        }
    }

    public String hmacHex(String canonical) {
        synchronized (mac) {
            byte[] out = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return hex(out);
        }
    }

    public boolean verify(String canonical, String providedHex) {
        if (providedHex == null || providedHex.isEmpty()) {
            return false;
        }
        String expected = hmacHex(canonical);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedHex.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------ canonical 构造

    /** /api/v1/reward 的 canonical 串。字段顺序即文档顺序，勿改。 */
    public static String rewardCanonical(String txId, String appUserId, long ts,
                                         double amount, String adNetwork, String adUnitId) {
        return "reward\n" + txId
                + "\n" + appUserId
                + "\n" + ts
                + "\n" + formatAmount(amount)
                + "\n" + nvl(adNetwork)
                + "\n" + nvl(adUnitId);
    }

    /** /api/v1/link 的 canonical 串。 */
    public static String linkCanonical(String code, String appUserId, long ts) {
        return "link\n" + code.trim().toUpperCase(Locale.ROOT) + "\n" + appUserId + "\n" + ts;
    }

    /** /api/v1/link-long 的 canonical 串（长期令牌直接重绑）。 */
    public static String linkLongCanonical(String appUserId, String longToken, long ts) {
        return "link-long\n" + appUserId + "\n" + longToken + "\n" + ts;
    }

    /** /api/v1/unlink 的 canonical 串（必须出示长期令牌，旧数据 token 为空串）。 */
    public static String unlinkCanonical(String appUserId, String longToken, long ts) {
        return "unlink\n" + appUserId + "\n" + nvl(longToken) + "\n" + ts;
    }

    /** /api/v1/transfer 的 canonical 串。 */
    public static String transferCanonical(String txId, String fromAppUserId, String toAppUserId,
                                           long ts, double amount) {
        return "transfer\n" + txId
                + "\n" + fromAppUserId
                + "\n" + toAppUserId
                + "\n" + ts
                + "\n" + formatAmount(amount);
    }

    /** /api/v1/friend 的 canonical 串。action ∈ request|accept|reject|remove|list。 */
    public static String friendCanonical(String action, String appUserId, String otherAppUserId, long ts) {
        return "friend\n" + action + "\n" + appUserId + "\n" + nvl(otherAppUserId) + "\n" + ts;
    }

    /** /api/v1/balance 的 canonical 串。 */
    public static String balanceCanonical(String appUserId, long ts) {
        return "balance\n" + appUserId + "\n" + ts;
    }

    /** /api/v1/top 的 canonical 串。 */
    public static String topCanonical(long ts) {
        return "top\n" + ts;
    }

    /** /api/v1/ledger 的 canonical 串。 */
    public static String ledgerCanonical(String appUserId, long ts) {
        return "ledger\n" + appUserId + "\n" + ts;
    }

    /**
     * 金额规范化：去尾零的十进制字面量。50.0 -> "50"；50.5 -> "50.5"。
     * 与后端约定：canonical 里的金额 = JSON 数值解析后的十进制字符串（JS 侧 String(number) 即无尾零）。
     */
    public static String formatAmount(double amount) {
        if (amount == Math.rint(amount) && !Double.isInfinite(amount)) {
            return String.valueOf((long) amount);
        }
        return new BigDecimal(Double.toString(amount)).stripTrailingZeros().toPlainString();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}