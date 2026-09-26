package dev.adcoin.cmd;

import dev.adcoin.AdCoinPlugin;
import dev.adcoin.link.LinkCodeService;
import dev.adcoin.msg.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * /adlink —— 生成/查看/解除 App 绑定码。
 */
public final class AdLinkCommand implements CommandExecutor, TabCompleter {

    private final AdCoinPlugin plugin;

    public AdLinkCommand(AdCoinPlugin plugin) {
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
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "generate";

        switch (sub) {
            case "generate", "get" -> doGenerate(player, m);
            case "status", "info" -> doStatus(player, m);
            case "unlink", "unbind" -> doUnlink(player, m);
            default -> {
                m.send(player, "usage-adlink", null);
                return true;
            }
        }
        return true;
    }

    private void doGenerate(Player player, Messages m) {
        LinkCodeService.GenResult r = plugin.linkService()
                .generate(player.getUniqueId(), player.getName(), System.currentTimeMillis());
        switch (r.type()) {
            case NEW -> m.send(player, "link-created", Map.of(
                    "code", r.code(), "ttl", String.valueOf(r.ttlSeconds())));
            case REUSED -> m.send(player, "link-reused", Map.of(
                    "code", r.code(), "ttl", String.valueOf(r.ttlSeconds())));
            case COOLDOWN -> m.send(player, "link-cooldown", Map.of(
                    "seconds", String.valueOf(r.ttlSeconds())));
        }
    }

    private void doStatus(Player player, Messages m) {
        Optional<String> app = plugin.linkService().boundAppFor(player.getUniqueId());
        if (app.isPresent()) {
            m.send(player, "link-bound", Map.of("app", app.get()));
            return;
        }
        Optional<String> code = plugin.linkService().activeCodeFor(player.getUniqueId(), System.currentTimeMillis());
        if (code.isPresent()) {
            long remaining = (plugin.dataStore().pendingLink(code.get()).orElseThrow().expiresAt()
                    - System.currentTimeMillis() + 999) / 1000;
            m.send(player, "link-reused", Map.of("code", code.get(), "ttl", String.valueOf(Math.max(1, remaining))));
            return;
        }
        m.send(player, "link-not-bound", null);
    }

    private void doUnlink(Player player, Messages m) {
        if (plugin.dataStore().unbindByPlayer(player.getUniqueId())) {
            m.send(player, "link-unlinked", null);
        } else {
            m.send(player, "link-not-bound", null);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("generate", "status", "unlink").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}