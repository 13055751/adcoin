package dev.adcoin.cmd;

import dev.adcoin.AdCoinPlugin;
import dev.adcoin.econ.CurrencyService;
import dev.adcoin.msg.Messages;
import dev.adcoin.security.Signer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /friend —— 好友社交（列表/待处理请求/私聊/转币）。好友关系在 App 端建立。
 */
public final class FriendCommand implements CommandExecutor, TabCompleter {

    private final AdCoinPlugin plugin;

    public FriendCommand(AdCoinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家在游戏内执行。");
            return true;
        }
        Messages m = plugin.messages();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "list" -> list(player, m);
            case "pending", "requests" -> pending(player, m);
            case "msg", "message", "tell" -> msg(player, m, args);
            case "transfer", "pay" -> transfer(player, m, args);
            default -> m.send(player, "usage-friend", null);
        }
        return true;
    }

    private void list(Player player, Messages m) {
        Set<String> friends = plugin.dataStore().friendsOf(player.getUniqueId());
        if (friends.isEmpty()) {
            m.send(player, "friend-no-friends", null);
            return;
        }
        m.send(player, "friend-list-header", Map.of("n", String.valueOf(friends.size())));
        for (String s : friends) {
            UUID f = UUID.fromString(s);
            boolean online = plugin.onlineTracker().isOnline(f);
            m.send(player, "friend-line", Map.of(
                    "player", nameOf(f),
                    "status", online ? "<green>● 在线</green>" : "<gray>○ 离线</gray>",
                    "balance", Signer.formatAmount(plugin.currency().balance(f))));
        }
    }

    private void pending(Player player, Messages m) {
        Map<String, Long> reqs = plugin.dataStore().friendRequestsFor(player.getUniqueId());
        if (reqs.isEmpty()) {
            m.send(player, "friend-pending-none", null);
            return;
        }
        m.send(player, "friend-pending-header", Map.of("n", String.valueOf(reqs.size())));
        for (String s : reqs.keySet()) {
            m.send(player, "friend-pending-line", Map.of("player", nameOf(UUID.fromString(s))));
        }
    }

    private void msg(Player player, Messages m, String[] args) {
        if (args.length < 3) {
            m.send(player, "usage-friend", null);
            return;
        }
        Optional<UUID> target = resolve(args[1]);
        if (target.isEmpty()) {
            m.send(player, "friend-not-found", Map.of("player", args[1]));
            return;
        }
        if (target.get().equals(player.getUniqueId())) {
            m.send(player, "friend-to-self", null);
            return;
        }
        if (plugin.config().socialRequireFriend()
                && !plugin.dataStore().isFriend(player.getUniqueId(), target.get())) {
            m.send(player, "friend-not-friend", Map.of("player", nameOf(target.get())));
            return;
        }
        if (!plugin.onlineTracker().isOnline(target.get())) {
            m.send(player, "friend-not-online", Map.of("player", nameOf(target.get())));
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            text.append(args[i]).append(' ');
        }
        String message = text.toString().trim();
        m.send(player, "friend-msg-sent", Map.of("player", nameOf(target.get()), "msg", message));
        Player tp = Bukkit.getPlayer(target.get());
        if (tp != null) {
            m.send(tp, "friend-msg-incoming", Map.of("player", player.getName(), "msg", message));
        }
    }

    private void transfer(Player player, Messages m, String[] args) {
        if (args.length < 3) {
            m.send(player, "usage-friend", null);
            return;
        }
        Optional<UUID> target = resolve(args[1]);
        if (target.isEmpty()) {
            m.send(player, "friend-not-found", Map.of("player", args[1]));
            return;
        }
        if (target.get().equals(player.getUniqueId())) {
            m.send(player, "friend-to-self", null);
            return;
        }
        if (plugin.config().socialRequireFriend()
                && !plugin.dataStore().isFriend(player.getUniqueId(), target.get())) {
            m.send(player, "friend-not-friend", Map.of("player", nameOf(target.get())));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            m.send(player, "invalid-number", Map.of("reason", args[2]));
            return;
        }
        String txId = "t-" + UUID.randomUUID();
        CurrencyService.TransferResult r = plugin.currency().transfer(
                txId, player.getUniqueId(), player.getName(), null, target.get(), amount, plugin.config());
        if (!r.ok()) {
            switch (r.error()) {
                case "insufficient" -> m.send(player, "transfer-not-enough", Map.of(
                        "balance", Signer.formatAmount(plugin.currency().balance(player.getUniqueId()))));
                case "min" -> m.send(player, "transfer-min", Map.of(
                        "amount", Signer.formatAmount(plugin.config().socialTransferMin())));
                case "invalid" -> m.send(player, "invalid-number", Map.of("reason", args[2]));
                default -> m.send(player, "transfer-min", null);
            }
            return;
        }
        m.send(player, "friend-transfer-ok", Map.of(
                "player", r.toName(),
                "amount", Signer.formatAmount(amount),
                "currency", plugin.config().currencyName(),
                "balance", Signer.formatAmount(r.fromBalance())));
        Player tp = Bukkit.getPlayer(target.get());
        if (tp != null) {
            m.send(tp, "friend-transfer-received", Map.of(
                    "player", player.getName(),
                    "amount", Signer.formatAmount(amount),
                    "currency", plugin.config().currencyName(),
                    "balance", Signer.formatAmount(r.toBalance())));
        }
    }

    private Optional<UUID> resolve(String name) {
        return plugin.dataStore().resolveName(name);
    }

    private String nameOf(UUID uuid) {
        String n = plugin.dataStore().knownName(uuid);
        return n == null ? uuid.toString().substring(0, 8) : n;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("list", "pending", "msg", "transfer", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2) {
            return plugin.dataStore().friendsOf(
                            sender instanceof Player p ? p.getUniqueId() : UUID.randomUUID())
                    .stream().map(uuid -> nameOf(UUID.fromString(uuid)))
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}