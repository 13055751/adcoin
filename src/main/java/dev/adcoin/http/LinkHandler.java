package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.link.LinkCodeService;
import dev.adcoin.msg.Messages;
import dev.adcoin.security.Signer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * POST /api/v1/link —— App 端提交游戏内生成的绑定码，完成账号绑定。
 * <p>
 * 请求体（JSON）：code, appUserId, ts, sig
 */
public final class LinkHandler extends BaseHandler {

    protected LinkHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.linkCanonical(requiredString(body, "code"), requiredString(body, "appUserId"),
                requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return requiredString(body, "appUserId") + ":" + requiredString(body, "code");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) throws Exception {
        String code = requiredString(body, "code");
        String appUserId = requiredString(body, "appUserId");

        LinkCodeService.ResolveResult res =
                ctx.linkService().resolve(code, appUserId, System.currentTimeMillis());

        switch (res.type()) {
            case OK -> {
                UUID player = res.binding().uuid();
                respond(ex, 200, Map.of(
                        "ok", true,
                        "playerName", res.binding().playerName(),
                        "playerUuid", res.binding().playerUuid()));
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    Player p = Bukkit.getPlayer(player);
                    if (p != null && p.isOnline()) {
                        new Messages(ctx.cfg()).send(p, "link-success-in-game", Map.of("app", appUserId));
                    }
                });
            }
            case INVALID -> respond(ex, 404, error("code_invalid"));
            case EXPIRED -> respond(ex, 410, error("code_expired"));
            case APP_ALREADY_BOUND -> respond(ex, 409, error("app_already_bound"));
            case PLAYER_ALREADY_BOUND -> respond(ex, 409, error("player_already_bound"));
        }
    }
}