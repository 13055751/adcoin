package dev.adcoin.econ;

import dev.adcoin.config.PluginConfig;
import dev.adcoin.data.DataStore;
import dev.adcoin.data.LedgerEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 广告币业务逻辑（无任何 Bukkit 依赖，可在 HTTP 工作线程安全执行）。
 * <p>
 * 组合写操作统一在 {@code store.mutex()} 上排队，保证幂等/限额/入账原子。
 */
public final class CurrencyService {

    /** 奖励发放结果。 */
    public record RewardOutcome(Status status, double balance, int dailyCount) {

        public enum Status {CREDITED, DUPLICATE, DAILY_LIMIT, INVALID_AMOUNT}

        public static RewardOutcome credited(double balance, int dailyCount) {
            return new RewardOutcome(Status.CREDITED, balance, dailyCount);
        }

        public static RewardOutcome duplicate(double balance) {
            return new RewardOutcome(Status.DUPLICATE, balance, 0);
        }

        public static RewardOutcome dailyLimit(double balance) {
            return new RewardOutcome(Status.DAILY_LIMIT, balance, 0);
        }

        public static RewardOutcome invalidAmount() {
            return new RewardOutcome(Status.INVALID_AMOUNT, -1, 0);
        }
    }

    /** 好友转币结果。 */
    public record TransferResult(boolean ok, String error,
                                 double fromBalance, double toBalance, String toName) {

        public static TransferResult ok(double from, double to, String toName) {
            return new TransferResult(true, null, from, to, toName);
        }

        public static TransferResult fail(String error) {
            return new TransferResult(false, error, -1, -1, null);
        }
    }

    private final DataStore store;

    public CurrencyService(DataStore store) {
        this.store = store;
    }

    public double balance(UUID player) {
        return store.balance(player);
    }

    public List<Map.Entry<UUID, DataStore.BalanceEntry>> top(int n) {
        return store.topBalances(n);
    }

    // ------------------------------------------------------------ 管理操作（主线程）

    public double give(UUID player, String name, double amount) {
        synchronized (store.mutex()) {
            double nb = store.balance(player) + amount;
            store.setBalance(player, name, nb);
            return nb;
        }
    }

    public double take(UUID player, String name, double amount) {
        synchronized (store.mutex()) {
            double nb = Math.max(0, store.balance(player) - amount);
            store.setBalance(player, name, nb);
            return nb;
        }
    }

    public double set(UUID player, String name, double amount) {
        synchronized (store.mutex()) {
            store.setBalance(player, name, Math.max(0, amount));
            return store.balance(player);
        }
    }

    // ------------------------------------------------------------ 好友转币

    /**
     * 好友转币（命令与 HTTP 共用）。txId 用于账本幂等；
     * 转给离线好友也可以（好友关系与余额都在服务器侧）。 */
    public TransferResult transfer(String txId, UUID from, String fromName, String fromAppUserId,
                                   UUID to, double amount, PluginConfig cfg) {
        synchronized (store.mutex()) {
            if (store.ledgerHas(txId)) {
                return TransferResult.fail("duplicate");
            }
            if (from.equals(to)) {
                return TransferResult.fail("self");
            }
            if (!(amount > 0)) {
                return TransferResult.fail("invalid");
            }
            if (amount < cfg.socialTransferMin()) {
                return TransferResult.fail("min");
            }
            double fromBalance = store.balance(from);
            if (fromBalance < amount) {
                return TransferResult.fail("insufficient");
            }
            String toName = store.knownName(to);
            if (toName == null) {
                toName = to.toString().substring(0, 8);
            }
            store.rememberName(from, fromName);
            double fromNew = fromBalance - amount;
            store.setBalance(from, fromName, fromNew);
            double toNew = store.balance(to) + amount;
            store.setBalance(to, toName, toNew);
            store.addLedger(new LedgerEntry(
                    txId, to.toString(), fromAppUserId, amount, "transfer", null, System.currentTimeMillis()));
            return TransferResult.ok(fromNew, toNew, toName);
        }
    }

    // ------------------------------------------------------------ 奖励入账（HTTP 工作线程）

    /**
     * 处理一条广告奖励。调用方保证玩家已绑定、签名与防重放已通过。
     * 全流程：幂等检查 → 金额解析（ad-units 覆盖）→ 限额计数 → 入账 → 记账本。
     */
    public RewardOutcome creditReward(String txId, UUID player, String playerName, String appUserId,
                                      String adNetwork, String adUnitId, double rawAmount, PluginConfig cfg) {
        synchronized (store.mutex()) {
            if (store.ledgerHas(txId)) {
                return RewardOutcome.duplicate(store.balance(player));
            }
            double amount = cfg.adUnitAmount(adUnitId).orElse(rawAmount);
            if (!(amount > 0) || amount > cfg.maxAmount()) {
                return RewardOutcome.invalidAmount();
            }
            int count = store.incrementDailyCount(player, LocalDate.now().toString(), cfg.dailyPerPlayer());
            if (count < 0) {
                return RewardOutcome.dailyLimit(store.balance(player));
            }
            boolean added = store.addLedger(new LedgerEntry(
                    txId, player.toString(), appUserId, amount, adNetwork, adUnitId, System.currentTimeMillis()));
            if (!added) {
                return RewardOutcome.duplicate(store.balance(player));
            }
            double nb = store.balance(player) + amount;
            store.setBalance(player, playerName, nb);
            return RewardOutcome.credited(nb, count);
        }
    }
}