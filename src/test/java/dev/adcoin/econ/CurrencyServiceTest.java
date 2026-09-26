package dev.adcoin.econ;

import dev.adcoin.config.PluginConfig;
import dev.adcoin.data.DataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyServiceTest {

    @TempDir
    Path dir;

    private DataStore store(Path p) {
        return new DataStore(p, 100);
    }

    private PluginConfig cfg() {
        return PluginConfig.builder().dailyPerPlayer(20).maxAmount(100_000).build();
    }

    @Test
    void creditsAndPersistsBalance() {
        DataStore s = store(dir.resolve("c1.json"));
        CurrencyService c = new CurrencyService(s);
        UUID player = UUID.randomUUID();
        CurrencyService.RewardOutcome out = c.creditReward(
                "tx-1", player, "Steve", "app-1", "admob", "unit-1", 50.0, cfg());
        assertEquals(CurrencyService.RewardOutcome.Status.CREDITED, out.status());
        assertEquals(50.0, out.balance());
        assertEquals(50.0, c.balance(player));
    }

    @Test
    void duplicateTxIsIdempotent() {
        DataStore s = store(dir.resolve("c2.json"));
        CurrencyService c = new CurrencyService(s);
        UUID player = UUID.randomUUID();
        c.creditReward("tx-1", player, "Steve", "app-1", "admob", null, 50.0, cfg());
        CurrencyService.RewardOutcome dup = c.creditReward(
                "tx-1", player, "Steve", "app-1", "admob", null, 999.0, cfg());
        assertEquals(CurrencyService.RewardOutcome.Status.DUPLICATE, dup.status());
        assertEquals(50.0, c.balance(player));
    }

    @Test
    void dailyLimitStopsFurtherCredits() {
        DataStore s = store(dir.resolve("c3.json"));
        PluginConfig cfg = PluginConfig.builder().dailyPerPlayer(1).build();
        CurrencyService c = new CurrencyService(s);
        UUID player = UUID.randomUUID();
        assertEquals(CurrencyService.RewardOutcome.Status.CREDITED,
                c.creditReward("tx-1", player, "Steve", "app-1", "admob", null, 10.0, cfg).status());
        CurrencyService.RewardOutcome second = c.creditReward(
                "tx-2", player, "Steve", "app-1", "admob", null, 10.0, cfg);
        assertEquals(CurrencyService.RewardOutcome.Status.DAILY_LIMIT, second.status());
        assertEquals(10.0, c.balance(player));
    }

    @Test
    void invalidAmountsRejected() {
        DataStore s = store(dir.resolve("c4.json"));
        PluginConfig cfg = PluginConfig.builder().maxAmount(100).build();
        CurrencyService c = new CurrencyService(s);
        UUID player = UUID.randomUUID();
        assertEquals(CurrencyService.RewardOutcome.Status.INVALID_AMOUNT,
                c.creditReward("tx-1", player, "Steve", "app-1", "admob", null, 0.0, cfg).status());
        assertEquals(CurrencyService.RewardOutcome.Status.INVALID_AMOUNT,
                c.creditReward("tx-2", player, "Steve", "app-1", "admob", null, -5.0, cfg).status());
        assertEquals(CurrencyService.RewardOutcome.Status.INVALID_AMOUNT,
                c.creditReward("tx-3", player, "Steve", "app-1", "admob", null, 101.0, cfg).status());
        assertEquals(0.0, c.balance(player));
    }

    @Test
    void adUnitsOverrideRequestAmount() {
        DataStore s = store(dir.resolve("c5.json"));
        PluginConfig cfg = PluginConfig.builder().adUnit("unit-77", 77.0).build();
        CurrencyService c = new CurrencyService(s);
        UUID player = UUID.randomUUID();
        CurrencyService.RewardOutcome out = c.creditReward(
                "tx-1", player, "Steve", "app-1", "admob", "unit-77", 5.0, cfg);
        assertEquals(CurrencyService.RewardOutcome.Status.CREDITED, out.status());
        assertEquals(77.0, out.balance());
    }

    @Test
    void adminMutationsWork() {
        DataStore s = store(dir.resolve("c6.json"));
        CurrencyService c = new CurrencyService(s);
        UUID player = UUID.randomUUID();
        c.give(player, "Steve", 100.0);
        assertEquals(100.0, c.balance(player));
        c.take(player, "Steve", 30.0);
        assertEquals(70.0, c.balance(player));
        c.set(player, "Steve", 5.0);
        assertEquals(5.0, c.balance(player));
        c.take(player, "Steve", 999.0);
        assertEquals(0.0, c.balance(player));
    }
}