package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.Binding;
import dev.adcoin.econ.CurrencyService;
import dev.adcoin.event.AdRewardEvent;
import dev.adcoin.msg.Messages;
import dev.adcoin.security.Signer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * POST /api/v1/reward —— 后端广告 SSV 验真通过后，上报一次广告奖励。
 * <p>
 * 请求体（JSON）：txId, appUserId, adNetwork, adUnitId(可选), amount, ts, sig
 * 流程：签名 → 防重放 → 绑定点查 → 幂等/限额/入账（工作线程）→ 回包；
 * 主线程副作用：AdRewardEvent + Vault 镜像 + 在线玩家消息。
 */
public final class RewardHandler extends BaseHandler {

    protected RewardHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.rewardCanonical(
                requiredString(body, "txId"),
                requiredString(body, "appUserId"),
                requiredLong(body, "ts"),
                requiredDouble(body, "amount"),
                optionalString(body, "adNetwork"),
                optionalString(body, "adUnitId"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return requiredString(body, "txId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) throws Exception {
        String txId = requiredString(body, "txId");
        String appUserId = requiredString(body, "appUserId");
        String adNetwork = optionalString(body, "adNetwork");
        String adUnitId = optionalString(body, "adUnitId");
        double rawAmount = requiredDouble(body, "amount");

        Optional<Binding> binding = ctx.plugin().dataStore().bindingByAppUser(appUserId);
        if (binding.isEmpty()) {
            respond(ex, 404, error("not_linked"));
            return;
        }
        Binding b = binding.get();
        UUID player = b.uuid();

        CurrencyService.RewardOutcome out = ctx.currency().creditReward(
                txId, player, b.playerName(), appUserId, adNetwork, adUnitId, rawAmount, ctx.cfg());

        switch (out.status()) {
            case CREDITED -> {
                respond(ex, 200, Map.of(
                        "ok", true,
                        "credited", true,
                        "playerName", b.playerName(),
                        "balance", out.balance(),
                        "dailyUsed", out.dailyCount()));
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    ctx.plugin().getServer().getPluginManager()
                            .callEvent(new AdRewardEvent(player, appUserId, txId, adNetwork, adUnitId,
                                    ctx.cfg().adUnitAmount(adUnitId).orElse(rawAmount), out.balance()));
                    ctx.plugin().vaultBridge().mirror(player,
                            ctx.cfg().adUnitAmount(adUnitId).orElse(rawAmount), ctx.cfg());
                    Player p = Bukkit.getPlayer(player);
                    if (p != null && p.isOnline()) {
                        new Messages(ctx.cfg()).send(p, "reward-received", Map.of(
                                "amount", Signer.formatAmount(ctx.cfg().adUnitAmount(adUnitId).orElse(rawAmount)),
                                "currency", ctx.cfg().currencyName(),
                                "balance", Signer.formatAmount(out.balance())));
                    }
                });
            }
            case DUPLICATE -> respond(ex, 200, Map.of(
                    "ok", true, "credited", false, "duplicate", true,
                    "playerName", b.playerName(), "balance", out.balance()));
            case DAILY_LIMIT -> respond(ex, 429, Map.of(
                    "ok", false, "error", "daily_limit",
                    "playerName", b.playerName(), "balance", out.balance()));
            case INVALID_AMOUNT -> respond(ex, 400, error("amount_invalid"));
        }
    }
}