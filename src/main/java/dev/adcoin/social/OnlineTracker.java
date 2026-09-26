package dev.adcoin.social;

import dev.adcoin.data.DataStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线状态追踪：join/quit 时维护 UUID 集合，供 HTTP 工作线程安全查询；
 * 顺带在加入时把玩家名写入名册（保持名字最新）。
 */
public final class OnlineTracker implements Listener {

    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final DataStore store;

    public OnlineTracker(DataStore store) {
        this.store = store;
    }

    public void register(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isOnline(UUID player) {
        return online.contains(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        online.add(p.getUniqueId());
        store.rememberName(p.getUniqueId(), p.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        online.remove(e.getPlayer().getUniqueId());
    }
}