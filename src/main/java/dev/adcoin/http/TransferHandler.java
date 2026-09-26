package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.Binding;
import dev.adcoin.econ.CurrencyService;
import dev.adcoin.msg.Messages;
import dev.adcoin.security.Signer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * POST /api/v1/transfer —— App 端好友转币（幂等：txId 唯一）。
 * <p>
 * 请求体：txId, fromAppUserId, toAppUserId, amount, ts, sig
 * 默认要求双方已是好友（social.require-friend 可关）。
 */
public final class TransferHandler extends BaseHandler {

    protected TransferHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.transferCanonical(
                requiredString(body, "txId"),
                requiredString(body, "fromAppUserId"),
                requiredString(body, "toAppUserId"),
                requiredLong(body, "ts"),
                requiredDouble(body, "amount"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return requiredString(body, "txId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        String txId = requiredString(body, "txId");
        String fromApp = requiredString(body, "fromAppUserId");
        String toApp = requiredString(body, "toAppUserId");
        double amount = requiredDouble(body, "amount");

        Optional<Binding> from = ctx.plugin().dataStore().bindingByAppUser(fromApp);
        Optional<Binding> to = ctx.plugin().dataStore().bindingByAppUser(toApp);
        if (from.isEmpty() || to.isEmpty()) {
            respond(ex, 404, error("not_linked"));
            return;
        }
        UUID fromUuid = from.get().uuid();
        UUID toUuid = to.get().uuid();
        if (fromUuid.equals(toUuid)) {
            respond(ex, 400, error("self"));
            return;
        }
        if (ctx.cfg().socialRequireFriend() && !ctx.plugin().dataStore().isFriend(fromUuid, toUuid)) {
            respond(ex, 403, error("not_friends"));
            return;
        }

        CurrencyService.TransferResult r = ctx.currency().transfer(
                txId, fromUuid, from.get().playerName(), fromApp, toUuid, amount, ctx.cfg());
        if (!r.ok()) {
            int status = "duplicate".equals(r.error()) ? 200 : 400;
            respond(ex, status, Map.of("ok", false, "error", r.error()));
            return;
        }
        respond(ex, 200, Map.of("ok", true, "fromBalance", r.fromBalance(),
                "toBalance", r.toBalance(), "toName", r.toName()));
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            Player p = Bukkit.getPlayer(toUuid);
            if (p != null && p.isOnline()) {
                new Messages(ctx.cfg()).send(p, "friend-transfer-received", Map.of(
                        "player", from.get().playerName(),
                        "amount", Signer.formatAmount(amount),
                        "currency", ctx.cfg().currencyName(),
                        "balance", Signer.formatAmount(r.toBalance())));
            }
        });
    }
}