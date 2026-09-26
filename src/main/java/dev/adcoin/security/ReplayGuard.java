package dev.adcoin.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 防重放：内存中记录最近见过的请求 ID（txId / code），过期窗口外自动清理。
 * 反馈信号 = 拒绝重复 ID + 拒绝过期时间戳。持久化的账本才是最终幂等保证。
 */
public final class ReplayGuard {

    private final long windowMillis;
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final AtomicLong ops = new AtomicLong();

    public ReplayGuard(int tsWindowSeconds) {
        this.windowMillis = tsWindowSeconds * 1000L;
    }

    /**
     * @return true = 时间戳新鲜且 ID 未见过，本次请求可继续处理；
     *         false = 过期（时间戳偏差超出窗口）或重复（已处理过）。
     */
    public boolean accept(String id, long tsMillis) {
        if (Math.abs(System.currentTimeMillis() - tsMillis) > windowMillis) {
            return false;
        }
        Long prev = seen.putIfAbsent(id, tsMillis);
        if (prev != null) {
            return false;
        }
        maybePurge();
        return true;
    }

    private void maybePurge() {
        if (ops.incrementAndGet() % 4096 != 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - windowMillis * 2;
        seen.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}