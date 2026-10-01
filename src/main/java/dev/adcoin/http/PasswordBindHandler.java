package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.Binding;
import dev.adcoin.msg.Messages;
import dev.adcoin.security.Signer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * POST /api/v1/bind-by-password —— 离线服（装有 AuthMe）用 MC 账号密码直接绑定。
 * <p>
 * 请求体（JSON）：mcUsername, mcPassword, appUserId, ts, sig<br>
 * 校验：反射调用 fr.xephi.authme.api.v3.AuthMeApi#isRegistered/#checkPassword（软依赖）。<br>
 * 防撞库：同一 appUserId 连续 10 次密码错误 → 锁 15 分钟（429 password_lock）。<br>
 * 未安装 AuthMe → 400 authme_missing。
 */
public final class PasswordBindHandler extends BaseHandler {

    private static final int MAX_FAILS = 10;
    private static final long WINDOW_MS = 15 * 60_000L;

    private record Attempts(int count, long firstTs) {
    }

    private final ConcurrentHashMap<String, Attempts> fails = new ConcurrentHashMap<>();

    protected PasswordBindHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.bindPasswordCanonical(requiredString(body, "mcUsername"),
                requiredString(body, "mcPassword"), requiredString(body, "appUserId"),
                requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return "bind-pw:" + requiredString(body, "appUserId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) throws Exception {
        String mcUsername = requiredString(body, "mcUsername");
        String mcPassword = requiredString(body, "mcPassword");
        String appUserId = requiredString(body, "appUserId");

        if (Bukkit.getPluginManager().getPlugin("AuthMe") == null) {
            respond(ex, 400, error("authme_missing"));
            return;
        }

        long now = System.currentTimeMillis();
        Attempts a = fails.get(appUserId);
        if (a != null && now - a.firstTs() < WINDOW_MS && a.count() >= MAX_FAILS) {
            respond(ex, 429, error("password_lock"));
            return;
        }

        // ---- 反射调用 AuthMe API v3（避免编译期硬依赖）----
        Object api;
        try {
            Class<?> cls = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Method getInstance = cls.getMethod("getInstance");
            api = getInstance.invoke(null);
        } catch (ClassNotFoundException e) {
            respond(ex, 400, error("authme_api_missing"));
            return;
        }
        try {
            Method isRegistered = api.getClass().getMethod("isRegistered", String.class);
            Method checkPassword = api.getClass().getMethod("checkPassword", String.class, String.class);

            if (!Boolean.TRUE.equals(isRegistered.invoke(api, mcUsername))) {
                respond(ex, 404, error("account_not_registered"));
                return;
            }
            if (!Boolean.TRUE.equals(checkPassword.invoke(api, mcUsername, mcPassword))) {
                fails.compute(appUserId, (k, v) ->
                        v == null || now - v.firstTs() >= WINDOW_MS
                                ? new Attempts(1, now)
                                : new Attempts(v.count() + 1, v.firstTs()));
                respond(ex, 401, error("bad_password"));
                return;
            }
        } catch (Exception e) {
            ctx.log().severe("AuthMe 反射调用失败: " + e);
            respond(ex, 500, error("authme_internal"));
            return;
        }

        // ---- 密码校验通过 → 绑定（与短码绑定同规则）----
        var store = ctx.plugin().dataStore();
        var offlineId = Bukkit.getOfflinePlayer(mcUsername).getUniqueId();
        synchronized (store.mutex()) {
            Optional<Binding> existingApp = store.bindingByAppUser(appUserId);
            if (existingApp.isPresent()) {
                respond(ex, 409, error("app_already_bound"));
                return;
            }
            Optional<String> playerApp = store.appUserByPlayer(offlineId);
            if (playerApp.isPresent() && !playerApp.get().equals(appUserId)) {
                respond(ex, 409, error("player_already_bound"));
                return;
            }
            fails.remove(appUserId);
            Binding binding = new Binding(offlineId.toString(), mcUsername, now, Binding.newLongToken());
            store.bind(appUserId, binding);
            store.save();
            respond(ex, 200, Map.of(
                    "ok", true,
                    "playerName", binding.playerName(),
                    "playerUuid", binding.playerUuid(),
                    "longToken", binding.longToken()));
            notifyInGame(offlineId, appUserId);
        }
    }

    private void notifyInGame(java.util.UUID uuid, String appUserId) {
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                new Messages(ctx.cfg()).send(p, "link-success-in-game", Map.of("app", appUserId));
            }
        });
    }
}