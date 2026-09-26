package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.DataStore;
import dev.adcoin.security.Signer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * POST /api/v1/top —— 余额排行榜（供 App 展示）。
 * <p>
 * 请求体：ts, sig
 */
public final class TopHandler extends BaseHandler {

    protected TopHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.topCanonical(requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return "top:" + requiredLong(body, "ts");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        List<Map.Entry<UUID, DataStore.BalanceEntry>> top = ctx.currency().top(10);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<UUID, DataStore.BalanceEntry> e : top) {
            Map<String, Object> item = new HashMap<>();
            item.put("uuid", e.getKey().toString());
            item.put("name", e.getValue().name());
            item.put("balance", e.getValue().amount());
            list.add(item);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("top", list);
        respond(ex, 200, resp);
    }
}