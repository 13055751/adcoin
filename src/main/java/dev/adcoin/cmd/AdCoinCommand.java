package dev.adcoin.cmd;

import dev.adcoin.AdCoinPlugin;
import dev.adcoin.data.DataStore;
import dev.adcoin.msg.Messages;
import dev.adcoin.security.Signer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /adcoin —— 余额查询与管理员管理命令。
 */
public final class AdCoinCommand implements CommandExecutor, TabCompleter {

    private final AdCoinPlugin plugin;

    public AdCoinCommand(AdCoinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages m = plugin.messages();
        if (args.length == 0) {
            m.send(sender instanceof Player p ? p : null, "usage-adcoin", null);
            if (!(sender instanceof Player)) {
                sender.sendMessage("/adcoin <balance|give|take|set|top|unlink|reload>");
            }
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);

        switch (sub) {
            case "balance" -> balance(sender, m, args);
            case "give" -> mutate(sender, m, args, Mutate.GIVE);
            case "take" -> mutate(sender, m, args, Mutate.TAKE);
            case "set" -> mutate(sender, m, args, Mutate.SET);
            case "top" -> top(sender, m, args);
            case "unlink" -> unlink(sender, m, args);
            case "reload" -> {
                plugin.reloadAll();
                if (sender instanceof Player p) {
                    m.send(p, "admin-reloaded", null);
                } else {
                    sender.sendMessage("[AdCoin] 配置已重载。");
                }
            }
            default -> m.send(sender instanceof Player p ? p : null, "usage-adcoin", null);
        }
        return true;
    }

    // ------------------------------------------------------------ 子命令

    private void balance(CommandSender sender, Messages m, String[] args) {
        if (args.length >= 2) {
            OfflinePlayer target = resolve(args[1]);
            if (target == null) {
                m.send(sender instanceof Player p ? p : null, "player-not-found", Map.of("player", args[1]));
                return;
            }
            double bal = plugin.currency().balance(target.getUniqueId());
            if (sender instanceof Player p) {
                m.send(p, "balance-other", Map.of("player", nameOf(target),
                        "currency", plugin.config().currencyName(), "balance", Signer.formatAmount(bal)));
            } else {
                sender.sendMessage(nameOf(target) + " 的 " + plugin.config().currencyName() + " 余额: " + Signer.formatAmount(bal));
            }
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("用法: /adcoin balance <玩家>");
            return;
        }
        double bal = plugin.currency().balance(p.getUniqueId());
        m.send(p, "balance", Map.of("currency", plugin.config().currencyName(), "balance", Signer.formatAmount(bal)));
    }

    private enum Mutate {GIVE, TAKE, SET}

    private void mutate(CommandSender sender, Messages m, String[] args, Mutate op) {
        if (args.length < 3) {
            if (sender instanceof Player p) {
                m.send(p, "usage-adcoin", null);
            }
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            m.send(sender instanceof Player p ? p : null, "player-not-found", Map.of("player", args[1]));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            m.send(sender instanceof Player p ? p : null, "invalid-number", Map.of("reason", args[2]));
            return;
        }
        if (amount <= 0) {
            m.send(sender instanceof Player p ? p : null, "invalid-number", Map.of("reason", args[2]));
            return;
        }
        String name = nameOf(target);
        UUID id = target.getUniqueId();
        plugin.currency().give(id, name, 0); // 确保余额条目存在
        double newBalance = switch (op) {
            case GIVE -> plugin.currency().give(id, name, amount);
            case TAKE -> plugin.currency().take(id, name, amount);
            case SET -> plugin.currency().set(id, name, amount);
        };
        String key = switch (op) {
            case GIVE -> "admin-give";
            case TAKE -> "admin-take";
            case SET -> "admin-set";
        };
        if (sender instanceof Player p) {
            m.send(p, key, Map.of("player", name, "amount", Signer.formatAmount(amount),
                    "currency", plugin.config().currencyName()));
            if (!p.getUniqueId().equals(id) && target.isOnline()) {
                m.send(target.getPlayer(), "balance", Map.of("currency", plugin.config().currencyName(),
                        "balance", Signer.formatAmount(newBalance)));
            }
        } else {
            sender.sendMessage("[AdCoin] OK -> " + name + " 余额: " + Signer.formatAmount(newBalance));
        }
    }

    private void top(CommandSender sender, Messages m, String[] args) {
        int n = 10;
        if (args.length >= 2) {
            try {
                n = Math.min(100, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        List<Map.Entry<UUID, DataStore.BalanceEntry>> top = plugin.currency().top(n);
        if (sender instanceof Player p) {
            m.send(p, "top-header", Map.of("currency", plugin.config().currencyName()));
            int rank = 1;
            for (Map.Entry<UUID, DataStore.BalanceEntry> e : top) {
                m.send(p, "top-line", Map.of("n", String.valueOf(rank++),
                        "player", e.getValue().name() == null ? shortUuid(e.getKey()) : e.getValue().name(),
                        "balance", Signer.formatAmount(e.getValue().amount())));
            }
        } else {
            sender.sendMessage("—— " + plugin.config().currencyName() + " 排行榜 ——");
            int rank = 1;
            for (Map.Entry<UUID, DataStore.BalanceEntry> e : top) {
                sender.sendMessage(rank++ + ". " + e.getValue().name() + ": " + Signer.formatAmount(e.getValue().amount()));
            }
        }
    }

    private void unlink(CommandSender sender, Messages m, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player p) {
                m.send(p, "usage-adcoin", null);
            }
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            m.send(sender instanceof Player p ? p : null, "player-not-found", Map.of("player", args[1]));
            return;
        }
        if (plugin.dataStore().unbindByPlayer(target.getUniqueId())) {
            m.send(sender instanceof Player p ? p : null, "admin-unlinked", Map.of("player", nameOf(target)));
        } else if (sender instanceof Player p) {
            m.send(p, "link-not-bound", null);
        } else {
            sender.sendMessage("[AdCoin] " + nameOf(target) + " 没有绑定。");
        }
    }

    // ------------------------------------------------------------ 工具

    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    private static String nameOf(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : p.getUniqueId().toString().substring(0, 8);
    }

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("balance", "give", "take", "set", "top", "unlink", "reload");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take")
                || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("balance")
                || args[0].equalsIgnoreCase("unlink"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names.stream().filter(s -> s.toLowerCase(java.util.Locale.ROOT)
                    .startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        return List.of();
    }
}