package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.Binding;
import dev.adcoin.security.Signer;

import java.util.Map;
import java.util.Optional;

/**
 * POST /api/v1/balance —— 查询某 App 账号绑定玩家的余额与绑定状态。
 * <p>
 * 请求体：appUserId, ts, sig
 */
public final class BalanceHandler extends BaseHandler {

    protected BalanceHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.balanceCanonical(requiredString(body, "appUserId"), requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return "balance:" + requiredString(body, "appUserId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        String appUserId = requiredString(body, "appUserId");
        Optional<Binding> binding = ctx.plugin().dataStore().bindingByAppUser(appUserId);
        if (binding.isEmpty()) {
            respond(ex, 200, Map.of("ok", true, "linked", false));
            return;
        }
        Binding b = binding.get();
        double balance = ctx.currency().balance(b.uuid());
        int dailyUsed = ctx.plugin().dataStore().dailyCount(b.uuid(), java.time.LocalDate.now().toString());
        respond(ex, 200, Map.of(
                "ok", true,
                "linked", true,
                "playerName", b.playerName(),
                "playerUuid", b.playerUuid(),
                "balance", balance,
                "dailyUsed", dailyUsed,
                "dailyLimit", ctx.cfg().dailyPerPlayer()));
    }
}