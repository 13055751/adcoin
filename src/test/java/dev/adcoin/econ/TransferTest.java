package dev.adcoin.econ;

import dev.adcoin.config.PluginConfig;
import dev.adcoin.data.DataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferTest {

    @TempDir
    Path dir;

    private DataStore store(Path p) {
        return new DataStore(p, 100);
    }

    private PluginConfig cfg() {
        return PluginConfig.builder().socialTransferMin(1.0).build();
    }

    @Test
    void transfersBetweenAccounts() {
        DataStore s = store(dir.resolve("t1.json"));
        CurrencyService c = new CurrencyService(s);
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        s.rememberName(from, "Steve");
        s.rememberName(to, "Alex");
        c.give(from, "Steve", 100.0);

        CurrencyService.TransferResult r = c.transfer("t-1", from, "Steve", null, to, 30.0, cfg());
        assertTrue(r.ok());
        assertEquals(70.0, r.fromBalance());
        assertEquals(30.0, r.toBalance());
        assertEquals(70.0, c.balance(from));
        assertEquals(30.0, c.balance(to));
        assertTrue(s.ledgerHas("t-1"));
    }

    @Test
    void duplicateTxRejectedWithoutDoubleSpend() {
        DataStore s = store(dir.resolve("t2.json"));
        CurrencyService c = new CurrencyService(s);
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        s.rememberName(to, "Alex");
        c.give(from, "Steve", 100.0);
        c.transfer("t-dup", from, "Steve", null, to, 30.0, cfg());
        CurrencyService.TransferResult dup = c.transfer("t-dup", from, "Steve", null, to, 30.0, cfg());
        assertFalse(dup.ok());
        assertEquals("duplicate", dup.error());
        assertEquals(70.0, c.balance(from));
        assertEquals(30.0, c.balance(to));
    }

    @Test
    void insufficientFundsRejected() {
        DataStore s = store(dir.resolve("t3.json"));
        CurrencyService c = new CurrencyService(s);
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        s.rememberName(to, "Alex");
        c.give(from, "Steve", 10.0);
        CurrencyService.TransferResult r = c.transfer("t-1", from, "Steve", null, to, 20.0, cfg());
        assertFalse(r.ok());
        assertEquals("insufficient", r.error());
        assertEquals(10.0, c.balance(from));
    }

    @Test
    void minAndSelfChecks() {
        DataStore s = store(dir.resolve("t4.json"));
        PluginConfig cfg = PluginConfig.builder().socialTransferMin(5.0).build();
        CurrencyService c = new CurrencyService(s);
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        c.give(from, "Steve", 100.0);
        s.rememberName(to, "Alex");
        assertEquals("min", c.transfer("t-1", from, "Steve", null, to, 3.0, cfg).error());
        assertEquals("self", c.transfer("t-2", from, "Steve", null, from, 10.0, cfg).error());
        assertEquals("invalid", c.transfer("t-3", from, "Steve", null, to, -1.0, cfg).error());
    }

    @Test
    void offlineRecipientWorks() {
        DataStore s = store(dir.resolve("t5.json"));
        CurrencyService c = new CurrencyService(s);
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        c.give(from, "Steve", 50.0);
        // to 从未上线/从未记账：仍可收款（名册未知名 → 短 UUID 兜底）
        CurrencyService.TransferResult r = c.transfer("t-1", from, "Steve", null, to, 5.0, cfg());
        assertTrue(r.ok());
        assertEquals(5.0, c.balance(to));
        assertEquals(45.0, c.balance(from));
    }
}