package dev.adcoin.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import dev.adcoin.data.Binding;
import dev.adcoin.msg.Messages;
import dev.adcoin.security.Signer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * POST /api/v1/friend —— 好友关系管理（App 端发起/确认，双向）。
 * <p>
 * 请求体 action: request | accept | reject | remove | list
 * 统一字段：action, appUserId, otherAppUserId(list 不需要), ts, sig
 */
public final class FriendHandler extends BaseHandler {

    protected FriendHandler(ApiContext ctx) {
        super(ctx);
    }

    @Override
    protected String canonical(JsonObject body) {
        return Signer.friendCanonical(
                requiredString(body, "action"),
                requiredString(body, "appUserId"),
                optionalString(body, "otherAppUserId"),
                requiredLong(body, "ts"));
    }

    @Override
    protected String replayId(JsonObject body) {
        String other = optionalString(body, "otherAppUserId");
        return "friend:" + requiredString(body, "action") + ":"
                + requiredString(body, "appUserId") + ":" + (other == null ? "" : other);
    }

    @Override
    protected void handleValid(HttpExchange ex, JsonObject body) {
        String action = requiredString(body, "action").toLowerCase(Locale.ROOT);
        String appUserId = requiredString(body, "appUserId");
        String otherAppUserId = optionalString(body, "otherAppUserId");

        Optional<Binding> me = ctx.plugin().dataStore().bindingByAppUser(appUserId);
        if (me.isEmpty()) {
            respond(ex, 404, error("not_linked"));
            return;
        }
        UUID myUuid = me.get().uuid();

        switch (action) {
            case "list" -> handleList(ex, myUuid);
            case "pending" -> handlePending(ex, myUuid);
            case "request" -> handleRequest(ex, myUuid, otherAppUserId);
            case "accept" -> handleAccept(ex, myUuid, otherAppUserId);
            case "reject" -> handleReject(ex, myUuid, otherAppUserId);
            case "remove" -> handleRemove(ex, myUuid, otherAppUserId);
            default -> respond(ex, 400, error("bad_action"));
        }
    }

    private void handlePending(HttpExchange ex, UUID myUuid) {
        Map<String, Long> reqs = ctx.plugin().dataStore().friendRequestsFor(myUuid);
        List<Map<String, Object>> list = new ArrayList<>();
        for (String from : reqs.keySet()) {
            UUID fromUuid = UUID.fromString(from);
            Map<String, Object> item = new HashMap<>();
            item.put("uuid", from);
            item.put("name", ctx.plugin().dataStore().knownName(fromUuid));
            ctx.plugin().dataStore().appUserByPlayer(fromUuid)
                    .ifPresent(a -> item.put("appUserId", a));
            list.add(item);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("requests", list);
        respond(ex, 200, resp);
    }

    private void handleList(HttpExchange ex, UUID myUuid) {
        Set<String> friends = ctx.plugin().dataStore().friendsOf(myUuid);
        List<Map<String, Object>> list = new ArrayList<>();
        for (String s : friends) {
            UUID f = UUID.fromString(s);
            Map<String, Object> item = new HashMap<>();
            item.put("uuid", f.toString());
            item.put("name", ctx.plugin().dataStore().knownName(f));
            item.put("online", ctx.plugin().onlineTracker().isOnline(f));
            ctx.plugin().dataStore().appUserByPlayer(f).ifPresent(a -> item.put("appUserId", a));
            list.add(item);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("friends", list);
        respond(ex, 200, resp);
    }

    private void handleRequest(HttpExchange ex, UUID myUuid, String otherAppUserId) {
        if (otherAppUserId == null) {
            respond(ex, 400, error("missing_field:otherAppUserId"));
            return;
        }
        Optional<Binding> target = ctx.plugin().dataStore().bindingByAppUser(otherAppUserId);
        if (target.isEmpty()) {
            respond(ex, 404, error("not_linked"));
            return;
        }
        UUID targetUuid = target.get().uuid();
        if (myUuid.equals(targetUuid)) {
            respond(ex, 400, error("self"));
            return;
        }
        if (ctx.plugin().dataStore().isFriend(myUuid, targetUuid)) {
            respond(ex, 200, Map.of("ok", true, "alreadyFriends", true));
            return;
        }
        boolean added = ctx.plugin().dataStore().addFriendRequest(myUuid, targetUuid, System.currentTimeMillis());
        respond(ex, 200, Map.of("ok", true, "pending", added, "alreadyPending", !added));
        if (added) {
            notify(targetUuid, "friend-request-incoming", Map.of("player", nameOf(myUuid)));
        }
    }

    private void handleAccept(HttpExchange ex, UUID myUuid, String otherAppUserId) {
        if (otherAppUserId == null) {
            respond(ex, 400, error("missing_field:otherAppUserId"));
            return;
        }
        Optional<Binding> requestor = ctx.plugin().dataStore().bindingByAppUser(otherAppUserId);
        if (requestor.isEmpty()) {
            respond(ex, 404, error("not_linked"));
            return;
        }
        UUID requestorUuid = requestor.get().uuid();
        if (!ctx.plugin().dataStore().pendingFriendRequest(requestorUuid, myUuid)) {
            respond(ex, 404, error("no_request"));
            return;
        }
        ctx.plugin().dataStore().addFriend(requestorUuid, myUuid);
        ctx.plugin().dataStore().removeFriendRequest(requestorUuid, myUuid);
        respond(ex, 200, Map.of("ok", true));
        notify(requestorUuid, "friend-added-both", Map.of("player", nameOf(myUuid)));
        notify(myUuid, "friend-added-both", Map.of("player", nameOf(requestorUuid)));
    }

    private void handleReject(HttpExchange ex, UUID myUuid, String otherAppUserId) {
        if (otherAppUserId == null) {
            respond(ex, 400, error("missing_field:otherAppUserId"));
            return;
        }
        Optional<Binding> requestor = ctx.plugin().dataStore().bindingByAppUser(otherAppUserId);
        if (requestor.isEmpty()) {
            respond(ex, 404, error("not_linked"));
            return;
        }
        ctx.plugin().dataStore().removeFriendRequest(requestor.get().uuid(), myUuid);
        respond(ex, 200, Map.of("ok", true));
    }

    private void handleRemove(HttpExchange ex, UUID myUuid, String otherAppUserId) {
        if (otherAppUserId == null) {
            respond(ex, 400, error("missing_field:otherAppUserId"));
            return;
        }
        Optional<Binding> other = ctx.plugin().dataStore().bindingByAppUser(otherAppUserId);
        if (other.isEmpty()) {
            respond(ex, 404, error("not_linked"));
            return;
        }
        boolean wasFriend = ctx.plugin().dataStore().isFriend(myUuid, other.get().uuid());
        ctx.plugin().dataStore().removeFriend(myUuid, other.get().uuid());
        respond(ex, 200, Map.of("ok", true, "removed", wasFriend));
    }

    private void notify(UUID player, String key, Map<String, String> vars) {
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            Player p = Bukkit.getPlayer(player);
            if (p != null && p.isOnline()) {
                new Messages(ctx.cfg()).send(p, key, vars);
            }
        });
    }

    private String nameOf(UUID uuid) {
        String n = ctx.plugin().dataStore().knownName(uuid);
        return n == null ? uuid.toString().substring(0, 8) : n;
    }
}