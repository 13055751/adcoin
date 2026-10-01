package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.Binding;
import dev.adcoin.security.Signer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * POST /api/v1/link-long —— 凭**长期令牌**直接（重）绑定，无需游戏内短码。
 * <p>
 * 用途：换设备 / 重装 App 后恢复绑定（服务端存有 longToken 时由后端代为调用）。
 * 请求体（JSON）：appUserId, longToken, ts, sig
 * 持有令牌 = 曾经完成过游戏侧短码验证，允许把该玩家重绑到此 appUserId。
 */
public final class LinkLongHandler extends BaseHandler {

    protected LinkLongHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.linkLongCanonical(requiredString(body, "appUserId"),
                requiredString(body, "longToken"), requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return "link-long:" + requiredString(body, "appUserId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        String appUserId = requiredString(body, "appUserId");
        String longToken = requiredString(body, "longToken");

        Optional<Binding> found = ctx.plugin().dataStore().bindingByLongToken(longToken);
        if (found.isEmpty()) {
            respond(ex, 401, error("token_invalid"));
            return;
        }
        Binding b = found.get();
        synchronized (ctx.plugin().dataStore().mutex()) {
            // 目标 app 已绑别的玩家 → 拒绝（防止令牌抢走他人绑定）
            Optional<Binding> existing = ctx.plugin().dataStore().bindingByAppUser(appUserId);
            if (existing.isPresent() && !existing.get().playerUuid().equals(b.playerUuid())) {
                respond(ex, 409, error("app_already_bound"));
                return;
            }
            // 玩家若绑在别的 app 上，先解除旧绑定
            Optional<String> oldApp = ctx.plugin().dataStore().appUserByPlayer(b.uuid());
            if (oldApp.isPresent() && !oldApp.get().equals(appUserId)) {
                ctx.plugin().dataStore().unbindByPlayer(b.uuid());
            }
            // 刷新绑定（轮换长期令牌，旧令牌作废）
            Binding fresh = new Binding(b.playerUuid(), b.playerName(),
                    System.currentTimeMillis(), Binding.newLongToken());
            ctx.plugin().dataStore().bind(appUserId, fresh);
            Map<String, Object> resp = new HashMap<>();
            resp.put("ok", true);
            resp.put("playerName", fresh.playerName());
            resp.put("playerUuid", fresh.playerUuid());
            resp.put("longToken", fresh.longToken());
            respond(ex, 200, resp);
        }
    }
}