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
import java.util.concurrent.ConcurrentHashMap;

/**
 * POST /api/v1/link —— App 端提交游戏内生成的短码（或二维码内容），完成绑定并**签发长期令牌**。
 * <p>
 * 请求体（JSON）：code, appUserId, ts, sig<br>
 * 响应：ok, playerName, playerUuid, longToken<br>
 * 防撞库：同一 appUserId 连续 10 次无效码 → 锁 15 分钟（429 code_lock）。
 */
public final class LinkHandler extends BaseHandler {

    private static final int MAX_FAILS = 10;
    private static final long WINDOW_MS = 15 * 60_000L;

    private record Attempts(int count, long firstTs) {
    }

    private final ConcurrentHashMap<String, Attempts> fails = new ConcurrentHashMap<>();

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

        // 防撞库闸门
        long now = System.currentTimeMillis();
        Attempts a = fails.get(appUserId);
        if (a != null && now - a.firstTs() < WINDOW_MS && a.count() >= MAX_FAILS) {
            respond(ex, 429, error("code_lock"));
            return;
        }

        LinkCodeService.ResolveResult res =
                ctx.linkService().resolve(code, appUserId, now);

        switch (res.type()) {
            case OK -> {
                fails.remove(appUserId); // 成功即清零
                UUID player = res.binding().uuid();
                String longToken = res.binding().longToken() == null ? "" : res.binding().longToken();
                respond(ex, 200, Map.of(
                        "ok", true,
                        "playerName", res.binding().playerName(),
                        "playerUuid", res.binding().playerUuid(),
                        "longToken", longToken));
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    Player p = Bukkit.getPlayer(player);
                    if (p != null && p.isOnline()) {
                        new Messages(ctx.cfg()).send(p, "link-success-in-game", Map.of("app", appUserId));
                    }
                });
            }
            case INVALID -> {
                fails.compute(appUserId, (k, v) ->
                        v == null || now - v.firstTs() >= WINDOW_MS
                                ? new Attempts(1, now)
                                : new Attempts(v.count() + 1, v.firstTs()));
                respond(ex, 404, error("code_invalid"));
            }
            case EXPIRED -> respond(ex, 410, error("code_expired"));
            case APP_ALREADY_BOUND -> respond(ex, 409, error("app_already_bound"));
            case PLAYER_ALREADY_BOUND -> respond(ex, 409, error("player_already_bound"));
        }
    }
}