package dev.adcoin.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataStoreTest {

    @TempDir
    Path dir;

    private DataStore newStore() {
        return new DataStore(dir.resolve("test-data.json"), 100);
    }

    @Test
    void roundTripPersistsEverything() {
        UUID player = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        DataStore store = newStore();
        store.bind("app-1", new Binding(player.toString(), "Steve", 1000L));
        store.setBalance(player, "Steve", 42.5);
        store.putPendingLink("CODE1", new PendingLink(player.toString(), "Steve", 2000L));
        assertTrue(store.addLedger(new LedgerEntry("tx-1", player.toString(), "app-1", 10.0, "admob", null, 1000L)));

        DataStore loaded = newStore();
        loaded.load();

        assertEquals("Steve", loaded.bindingByAppUser("app-1").orElseThrow().playerName());
        assertEquals("app-1", loaded.appUserByPlayer(player).orElseThrow());
        assertEquals(42.5, loaded.balance(player));
        assertTrue(loaded.pendingLink("CODE1").isPresent());
        assertTrue(loaded.ledgerHas("tx-1"));
        // 排行榜
        loaded.setBalance(player2, "Alex", 99.0);
        var top = loaded.topBalances(1);
        assertEquals(1, top.size());
        assertEquals(player2, top.get(0).getKey());
    }

    @Test
    void ledgerRejectsDuplicateTxId() {
        DataStore store = newStore();
        UUID player = UUID.randomUUID();
        assertTrue(store.addLedger(new LedgerEntry("tx-1", player.toString(), "app-1", 5.0, "admob", null, 1L)));
        assertFalse(store.addLedger(new LedgerEntry("tx-1", player.toString(), "app-1", 5.0, "admob", null, 2L)));
    }

    @Test
    void dailyLimitEnforced() {
        DataStore store = newStore();
        UUID player = UUID.randomUUID();
        String date = "2025-09-05";
        assertEquals(1, store.incrementDailyCount(player, date, 2));
        assertEquals(2, store.incrementDailyCount(player, date, 2));
        assertEquals(-1, store.incrementDailyCount(player, date, 2));
        assertEquals(2, store.dailyCount(player, date));
    }

    @Test
    void unexpiredPendingCodeForFiltersExpired() {
        DataStore store = newStore();
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        store.putPendingLink("OLD", new PendingLink(player.toString(), "Steve", now - 1000));
        store.putPendingLink("NEW", new PendingLink(player.toString(), "Steve", now + 60_000));
        assertEquals(Optional.of("NEW"), store.unexpiredPendingCodeFor(player, now));
    }

    @Test
    void unbindRemovesBothDirections() {
        DataStore store = newStore();
        UUID player = UUID.randomUUID();
        store.bind("app-1", new Binding(player.toString(), "Steve", 1L));
        assertTrue(store.unbindByAppUser("app-1"));
        assertTrue(store.bindingByAppUser("app-1").isEmpty());
        assertTrue(store.appUserByPlayer(player).isEmpty());
        assertFalse(store.unbindByAppUser("app-1"));
    }
}