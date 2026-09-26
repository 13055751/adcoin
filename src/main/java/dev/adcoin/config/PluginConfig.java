package dev.adcoin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 配置快照：从 config.yml 一次性读取，此后只读。
 * HTTP 工作线程读取的都是这个不可变对象（{@code volatile} 引用随重载替换），天然线程安全。
 */
public final class PluginConfig {

    // server
    private final String host;
    private final int port;
    private final String apiKey;
    private final int tsWindowSeconds;
    private final Set<String> allowedIps;
    private final int threads;

    // currency
    private final String currencyName;
    private final double maxAmount;
    private final Map<String, Double> adUnits;

    // limits
    private final int dailyPerPlayer;
    private final int codeLength;
    private final int codeTtlSeconds;
    private final int codeCooldownSeconds;
    private final int ledgerMaxEntries;

    // economy
    private final boolean vaultEnabled;
    private final int vaultPercent;

    // social
    private final boolean socialRequireFriend;
    private final double socialTransferMin;

    // messages
    private final Map<String, String> messages;

    private PluginConfig(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.apiKey = b.apiKey;
        this.tsWindowSeconds = b.tsWindowSeconds;
        this.allowedIps = Set.copyOf(b.allowedIps);
        this.threads = b.threads;
        this.currencyName = b.currencyName;
        this.maxAmount = b.maxAmount;
        this.adUnits = Map.copyOf(b.adUnits);
        this.dailyPerPlayer = b.dailyPerPlayer;
        this.codeLength = b.codeLength;
        this.codeTtlSeconds = b.codeTtlSeconds;
        this.codeCooldownSeconds = b.codeCooldownSeconds;
        this.ledgerMaxEntries = b.ledgerMaxEntries;
        this.vaultEnabled = b.vaultEnabled;
        this.vaultPercent = Math.max(0, Math.min(100, b.vaultPercent));
        this.socialRequireFriend = b.socialRequireFriend;
        this.socialTransferMin = Math.max(0, b.socialTransferMin);
        this.messages = Map.copyOf(b.messages);
    }

    public String host() { return host; }
    public int port() { return port; }
    public String apiKey() { return apiKey; }
    public int tsWindowSeconds() { return tsWindowSeconds; }
    public Set<String> allowedIps() { return allowedIps; }
    public int threads() { return threads; }

    public String currencyName() { return currencyName; }
    public double maxAmount() { return maxAmount; }

    /** ad-unit 固定金额覆盖：命中则忽略请求内 amount。 */
    public Optional<Double> adUnitAmount(String adUnitId) {
        if (adUnitId == null || adUnitId.isEmpty()) {
            return Optional.empty();
        }
        Double v = adUnits.get(adUnitId);
        return v == null ? Optional.empty() : Optional.of(v);
    }

    public int dailyPerPlayer() { return dailyPerPlayer; }
    public int codeLength() { return codeLength; }
    public int codeTtlSeconds() { return codeTtlSeconds; }
    public int codeCooldownSeconds() { return codeCooldownSeconds; }
    public int ledgerMaxEntries() { return ledgerMaxEntries; }

    public boolean vaultEnabled() { return vaultEnabled; }
    public int vaultPercent() { return vaultPercent; }

    public boolean socialRequireFriend() { return socialRequireFriend; }
    public double socialTransferMin() { return socialTransferMin; }

    public String message(String key) {
        return messages.getOrDefault(key, key);
    }

    // ---------------------------------------------------------------- 工厂

    /** 从 Bukkit FileConfiguration 读取。 */
    public static PluginConfig from(FileConfiguration y) {
        Builder b = builder();
        b.host(y.getString("server.host", "0.0.0.0"));
        b.port(y.getInt("server.port", 17890));
        b.apiKey(y.getString("server.api-key", "CHANGE-ME-PLEASE-USE-A-LONG-RANDOM-SECRET-STRING"));
        b.tsWindowSeconds(y.getInt("server.ts-window-seconds", 300));
        b.allowedIps(y.getStringList("server.allowed-ips"));
        b.threads(y.getInt("server.threads", 4));

        b.currencyName(y.getString("currency.name", "adcoins"));
        b.maxAmount(y.getDouble("currency.max-amount", 100_000.0));
        ConfigurationSection units = y.getConfigurationSection("currency.ad-units");
        if (units != null) {
            for (String k : units.getKeys(false)) {
                b.adUnit(k, units.getDouble(k));
            }
        }

        b.dailyPerPlayer(y.getInt("limits.daily-per-player", 20));
        b.codeLength(y.getInt("limits.code-length", 8));
        b.codeTtlSeconds(y.getInt("limits.code-ttl-seconds", 300));
        b.codeCooldownSeconds(y.getInt("limits.code-cooldown-seconds", 15));
        b.ledgerMaxEntries(y.getInt("limits.ledger-max-entries", 5000));

        b.vaultEnabled(y.getBoolean("economy.vault.enabled", false));
        b.vaultPercent(y.getInt("economy.vault.percent", 0));

        b.socialRequireFriend(y.getBoolean("social.require-friend", true));
        b.socialTransferMin(y.getDouble("social.transfer-min", 1.0));

        ConfigurationSection ms = y.getConfigurationSection("messages");
        if (ms != null) {
            b.messages(ms.getValues(false));
        }
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 测试与默认值构造用。 */
    public static final class Builder {
        private String host = "0.0.0.0";
        private int port = 17890;
        private String apiKey = "test-secret-key";
        private int tsWindowSeconds = 300;
        private Set<String> allowedIps = Set.of();
        private int threads = 4;
        private String currencyName = "adcoins";
        private double maxAmount = 100_000.0;
        private final Map<String, Double> adUnits = new LinkedHashMap<>();
        private int dailyPerPlayer = 20;
        private int codeLength = 8;
        private int codeTtlSeconds = 300;
        private int codeCooldownSeconds = 15;
        private int ledgerMaxEntries = 5000;
        private boolean vaultEnabled = false;
        private int vaultPercent = 0;
        private boolean socialRequireFriend = true;
        private double socialTransferMin = 1.0;
        private final Map<String, String> messages = new LinkedHashMap<>();

        public Builder host(String v) { this.host = v; return this; }
        public Builder port(int v) { this.port = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder tsWindowSeconds(int v) { this.tsWindowSeconds = v; return this; }
        public Builder allowedIps(java.util.List<String> v) { this.allowedIps = new HashSet<>(v); return this; }
        public Builder threads(int v) { this.threads = v; return this; }
        public Builder currencyName(String v) { this.currencyName = v; return this; }
        public Builder maxAmount(double v) { this.maxAmount = v; return this; }
        public Builder adUnit(String id, double amount) { this.adUnits.put(id, amount); return this; }
        public Builder dailyPerPlayer(int v) { this.dailyPerPlayer = v; return this; }
        public Builder codeLength(int v) { this.codeLength = v; return this; }
        public Builder codeTtlSeconds(int v) { this.codeTtlSeconds = v; return this; }
        public Builder codeCooldownSeconds(int v) { this.codeCooldownSeconds = v; return this; }
        public Builder ledgerMaxEntries(int v) { this.ledgerMaxEntries = v; return this; }
        public Builder vaultEnabled(boolean v) { this.vaultEnabled = v; return this; }
        public Builder vaultPercent(int v) { this.vaultPercent = v; return this; }
        public Builder socialRequireFriend(boolean v) { this.socialRequireFriend = v; return this; }
        public Builder socialTransferMin(double v) { this.socialTransferMin = v; return this; }
        public Builder messages(Map<String, Object> v) {
            for (Map.Entry<String, Object> e : v.entrySet()) {
                if (e.getValue() != null) {
                    messages.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
            return this;
        }
        public Builder message(String key, String v) { this.messages.put(key, v); return this; }

        public PluginConfig build() {
            return new PluginConfig(this);
        }
    }
}