package dev.adcoin.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayGuardTest {

    @Test
    void acceptsFreshIdOnceAndRejectsReplay() {
        ReplayGuard guard = new ReplayGuard(300);
        long now = System.currentTimeMillis();
        assertTrue(guard.accept("tx-1", now));
        assertFalse(guard.accept("tx-1", now));
        assertTrue(guard.accept("tx-2", now));
    }

    @Test
    void rejectsStaleTimestamp() {
        ReplayGuard guard = new ReplayGuard(300);
        long now = System.currentTimeMillis();
        assertFalse(guard.accept("tx-1", now - 301_000));
        assertFalse(guard.accept("tx-2", now + 301_000));
    }

    @Test
    void passesFreshTimestampAtWindowEdge() {
        ReplayGuard guard = new ReplayGuard(300);
        long now = System.currentTimeMillis();
        assertTrue(guard.accept("tx-1", now - 299_000));
        assertTrue(guard.accept("tx-2", now + 299_000));
    }
}