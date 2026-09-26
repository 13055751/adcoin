package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.security.Signer;

import java.util.Map;

/**
 * POST /api/v1/unlink —— App 账号与游戏账号解绑。
 * <p>
 * 请求体（JSON）：appUserId, ts, sig
 */
public final class UnlinkHandler extends BaseHandler {

    protected UnlinkHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.unlinkCanonical(requiredString(body, "appUserId"), requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return "unlink:" + requiredString(body, "appUserId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        String appUserId = requiredString(body, "appUserId");
        boolean unlinked = ctx.plugin().dataStore().unbindByAppUser(appUserId);
        respond(ex, 200, Map.of("ok", true, "unlinked", unlinked));
    }
}