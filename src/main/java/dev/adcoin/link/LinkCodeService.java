package dev.adcoin.link;

import dev.adcoin.config.PluginConfig;
import dev.adcoin.data.Binding;
import dev.adcoin.data.DataStore;
import dev.adcoin.data.PendingLink;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 绑定码生命周期：生成（含冷却与旧码失效）、校验与消费。
 * 纯逻辑，无 Bukkit 依赖；数据落在 {@link DataStore}。
 */
public final class LinkCodeService {

    /** 剔除易混淆字符 0/O/1/I 的 32 字符字母表。 */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public record GenResult(Type type, String code, long ttlSeconds) {

        public enum Type {NEW, REUSED, COOLDOWN}
    }

    public record ResolveResult(Type type, Binding binding) {

        public enum Type {OK, INVALID, EXPIRED, APP_ALREADY_BOUND, PLAYER_ALREADY_BOUND}

        public static ResolveResult ok(Binding b) {
            return new ResolveResult(Type.OK, b);
        }

        public static ResolveResult of(Type t) {
            return new ResolveResult(t, null);
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final DataStore store;
    private final PluginConfig cfg;
    private final ConcurrentMap<UUID, Long> lastGenerated = new ConcurrentHashMap<>();

    public LinkCodeService(DataStore store, PluginConfig cfg) {
        this.store = store;
        this.cfg = cfg;
    }

    /** 生成绑定码。已有未过期码 → 复用并提示剩余时间；冷却期内 → COOLDOWN。 */
    public GenResult generate(UUID player, String playerName, long nowMillis) {
        Optional<String> existing = store.unexpiredPendingCodeFor(player, nowMillis);
        if (existing.isPresent()) {
            PendingLink p = store.pendingLink(existing.get()).orElseThrow();
            return new GenResult(GenResult.Type.REUSED, existing.get(), remainingSeconds(p.expiresAt(), nowMillis));
        }
        Long last = lastGenerated.get(player);
        if (last != null && nowMillis - last < cfg.codeCooldownSeconds() * 1000L) {
            long wait = cfg.codeCooldownSeconds() - (nowMillis - last) / 1000L;
            return new GenResult(GenResult.Type.COOLDOWN, null, Math.max(1, wait));
        }
        String code = randomCode(cfg.codeLength());
        store.removePendingLinksForPlayer(player);
        store.putPendingLink(code, new PendingLink(player.toString(), playerName,
                nowMillis + cfg.codeTtlSeconds() * 1000L));
        store.save();
        lastGenerated.put(player, nowMillis);
        return new GenResult(GenResult.Type.NEW, code, cfg.codeTtlSeconds());
    }

    /** App 端提交绑定码：校验、消费并建立绑定。 */
    public ResolveResult resolve(String code, String appUserId, long nowMillis) {
        String key = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        PendingLink pending = store.pendingLink(key).orElse(null);
        if (pending == null) {
            return ResolveResult.of(ResolveResult.Type.INVALID);
        }
        if (pending.expiresAt() <= nowMillis) {
            store.removePendingLink(key);
            store.save();
            return ResolveResult.of(ResolveResult.Type.EXPIRED);
        }
        UUID player = UUID.fromString(pending.playerUuid());
        Optional<Binding> appBound = store.bindingByAppUser(appUserId);
        if (appBound.isPresent()) {
            return ResolveResult.of(ResolveResult.Type.APP_ALREADY_BOUND);
        }
        Optional<String> playerApp = store.appUserByPlayer(player);
        if (playerApp.isPresent() && !playerApp.get().equals(appUserId)) {
            return ResolveResult.of(ResolveResult.Type.PLAYER_ALREADY_BOUND);
        }
        Binding binding = new Binding(pending.playerUuid(), pending.playerName(), nowMillis);
        store.bind(appUserId, binding);
        store.removePendingLink(key);
        store.save();
        return ResolveResult.ok(binding);
    }

    /** 查当前未过期绑定码（/adlink status 用）。 */
    public Optional<String> activeCodeFor(UUID player, long nowMillis) {
        return store.unexpiredPendingCodeFor(player, nowMillis);
    }

    /** 已绑定的 App 账号（/adlink status 用）。 */
    public Optional<String> boundAppFor(UUID player) {
        return store.appUserByPlayer(player);
    }

    private static long remainingSeconds(long expiresAtMillis, long nowMillis) {
        long s = (expiresAtMillis - nowMillis + 999) / 1000;
        return Math.max(1, s);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}