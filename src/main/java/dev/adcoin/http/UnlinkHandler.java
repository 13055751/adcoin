package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.security.Signer;

import java.util.Map;

/**
 * POST /api/v1/unlink —— App 账号与游戏账号解绑（**必须出示长期令牌**）。
 * <p>
 * 请求体（JSON）：appUserId, longToken, ts, sig<br>
 * 旧数据（无长期令牌）跳过校验兼容；令牌不匹配 → 403 token_mismatch。
 */
public final class UnlinkHandler extends BaseHandler {

    protected UnlinkHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.unlinkCanonical(requiredString(body, "appUserId"),
                optionalString(body, "longToken") == null ? "" : optionalString(body, "longToken"),
                requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return "unlink:" + requiredString(body, "appUserId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        String appUserId = requiredString(body, "appUserId");
        String longToken = optionalString(body, "longToken");

        var binding = ctx.plugin().dataStore().bindingByAppUser(appUserId);
        if (binding.isPresent()) {
            String stored = binding.get().longToken();
            boolean legacy = stored == null || stored.isEmpty();
            if (!legacy && (longToken == null || !stored.equals(longToken))) {
                respond(ex, 403, error("token_mismatch"));
                return;
            }
        }
        boolean unlinked = ctx.plugin().dataStore().unbindByAppUser(appUserId);
        respond(ex, 200, Map.of("ok", true, "unlinked", unlinked));
    }
}