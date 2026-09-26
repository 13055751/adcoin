package dev.adcoin.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 广告奖励入账后（主线程）触发的领域事件，供其他插件扩展观察/统计。
 * 不可取消：账本与余额已在事件触发前提交。
 */
public final class AdRewardEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String appUserId;
    private final String txId;
    private final String adNetwork;
    private final String adUnitId;
    private final double amount;
    private final double newBalance;

    public AdRewardEvent(UUID playerId, String appUserId, String txId,
                         String adNetwork, String adUnitId, double amount, double newBalance) {
        this.playerId = playerId;
        this.appUserId = appUserId;
        this.txId = txId;
        this.adNetwork = adNetwork;
        this.adUnitId = adUnitId;
        this.amount = amount;
        this.newBalance = newBalance;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public String getTxId() {
        return txId;
    }

    public String getAdNetwork() {
        return adNetwork;
    }

    public String getAdUnitId() {
        return adUnitId;
    }

    public double getAmount() {
        return amount;
    }

    public double getNewBalance() {
        return newBalance;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}