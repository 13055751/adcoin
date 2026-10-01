package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.Binding;
import dev.adcoin.data.LedgerEntry;
import dev.adcoin.security.Signer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * POST /api/v1/ledger —— 查询某 App 账号绑定玩家的最近账本记录（App"最近动态"用）。
 * <p>
 * 请求体：appUserId, ts, sig
 * 响应：{ ok, entries: [{txId, amount, adNetwork, adUnitId, ts}] }（时间正序）
 */
public final class LedgerHandler extends BaseHandler {

    private static final int LIMIT = 20;

    protected LedgerHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.ledgerCanonical(requiredString(body, "appUserId"), requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        return "ledger:" + requiredString(body, "appUserId");
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        String appUserId = requiredString(body, "appUserId");
        Optional<Binding> binding = ctx.plugin().dataStore().bindingByAppUser(appUserId);
        if (binding.isEmpty()) {
            respond(ex, 200, Map.of("ok", true, "linked", false, "entries", new ArrayList<>()));
            return;
        }
        List<LedgerEntry> rows = ctx.plugin().dataStore().ledgerFor(binding.get().uuid(), LIMIT);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (LedgerEntry e : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("txId", e.txId());
            item.put("amount", e.amount());
            item.put("adNetwork", e.adNetwork());
            item.put("adUnitId", e.adUnitId());
            item.put("ts", e.ts());
            if (e.appUserId() != null && !e.appUserId().isEmpty()) {
                item.put("fromAppUserId", e.appUserId());
            }
            entries.add(item);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("linked", true);
        resp.put("entries", entries);
        respond(ex, 200, resp);
    }
}