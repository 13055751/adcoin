package dev.adcoin.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 数据存储：绑定关系 / 待绑定码 / 余额 / 奖励账本 / 每日计数 / 玩家名册 / 好友关系 / 好友请求。
 * <p>
 * 全部以单个 JSON 文件持久化（低频写入 + 原子替换），所有方法以单一监视器
 * {@link #mutex()} 保证线程安全：HTTP 工作线程与主线程（命令）都会访问。
 */
public final class DataStore {

    public record BalanceEntry(String name, double amount) {
    }

    /** 磁盘文件结构（序列化用） */
    @SuppressWarnings("unused")
    private record StoreFile(int version,
                             Map<String, Binding> bindings,
                             Map<String, String> playerBindings,
                             Map<String, PendingLink> pendingLinks,
                             Map<String, BalanceEntry> balances,
                             List<LedgerEntry> ledger,
                             Map<String, Map<String, Integer>> dailyCounts,
                             Map<String, String> names,
                             Map<String, Set<String>> friendships,
                             Map<String, Map<String, Long>> friendRequests) {
    }

    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Object lock = new Object();
    private final Path file;
    private final int ledgerMax;

    private final Map<String, Binding> bindings = new HashMap<>();            // appUserId -> binding
    private final Map<String, String> playerBindings = new HashMap<>();       // playerUuid -> appUserId
    private final Map<String, PendingLink> pendingLinks = new HashMap<>();    // code -> pending
    private final Map<String, BalanceEntry> balances = new HashMap<>();       // playerUuid -> entry
    private final Map<String, LedgerEntry> ledger = new LinkedHashMap<>();    // txId -> entry（插入序）
    private final Map<String, Map<String, Integer>> dailyCounts = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();                // playerUuid -> 最近已知名字
    private final Map<String, Set<String>> friendships = new HashMap<>();     // playerUuid -> 好友uuid集合（对称）
    private final Map<String, Map<String, Long>> friendRequests = new HashMap<>(); // 收件人 -> (请求人 -> 时间戳)

    public DataStore(Path file, int ledgerMax) {
        this.file = file;
        this.ledgerMax = Math.max(1, ledgerMax);
    }

    /** 全部状态共享的监视器，供组合操作原子化使用（creditReward 等）。 */
    public Object mutex() {
        return lock;
    }

    // ---------------------------------------------------------------- 读

    public Optional<Binding> bindingByAppUser(String appUserId) {
        synchronized (lock) {
            return Optional.ofNullable(bindings.get(appUserId));
        }
    }

    public Optional<String> appUserByPlayer(UUID player) {
        synchronized (lock) {
            return Optional.ofNullable(playerBindings.get(player.toString()));
        }
    }

    public Optional<PendingLink> pendingLink(String code) {
        synchronized (lock) {
            return Optional.ofNullable(pendingLinks.get(code));
        }
    }

    /** 找出该玩家当前未过期的绑定码（若有）。 */
    public Optional<String> unexpiredPendingCodeFor(UUID player, long nowMillis) {
        synchronized (lock) {
            String key = player.toString();
            for (Map.Entry<String, PendingLink> e : pendingLinks.entrySet()) {
                if (e.getValue().playerUuid().equals(key) && e.getValue().expiresAt() > nowMillis) {
                    return Optional.of(e.getKey());
                }
            }
            return Optional.empty();
        }
    }

    public double balance(UUID player) {
        synchronized (lock) {
            BalanceEntry e = balances.get(player.toString());
            return e == null ? 0.0 : e.amount();
        }
    }

    public Optional<BalanceEntry> balanceEntry(UUID player) {
        synchronized (lock) {
            return Optional.ofNullable(balances.get(player.toString()));
        }
    }

    public boolean ledgerHas(String txId) {
        synchronized (lock) {
            return ledger.containsKey(txId);
        }
    }

    /** 排行榜：按余额降序取前 n 名。 */
    public List<Map.Entry<UUID, BalanceEntry>> topBalances(int n) {
        synchronized (lock) {
            List<Map.Entry<UUID, BalanceEntry>> all = new ArrayList<>();
            for (Map.Entry<String, BalanceEntry> e : balances.entrySet()) {
                if (e.getValue().amount() > 0) {
                    all.add(Map.entry(UUID.fromString(e.getKey()), e.getValue()));
                }
            }
            all.sort(Map.Entry.<UUID, BalanceEntry>comparingByValue(
                    Comparator.comparingDouble(BalanceEntry::amount)).reversed());
            return all.subList(0, Math.min(n, all.size()));
        }
    }

    /** 某玩家的账本记录（时间正序，取最近 limit 条）。 */
    public List<LedgerEntry> ledgerFor(UUID player, int limit) {
        synchronized (lock) {
            String key = player.toString();
            List<LedgerEntry> all = new ArrayList<>();
            for (LedgerEntry e : ledger.values()) {
                if (key.equals(e.playerUuid())) {
                    all.add(e);
                }
            }
            if (all.size() > limit) {
                return new ArrayList<>(all.subList(all.size() - limit, all.size()));
            }
            return all;
        }
    }

    public int dailyCount(UUID player, String date) {
        synchronized (lock) {
            Map<String, Integer> m = dailyCounts.get(player.toString());
            return m == null ? 0 : m.getOrDefault(date, 0);
        }
    }

    // ---------------------------------------------------------------- 名册与好友（读）

    /** 最近已知的玩家名；未知时回落为余额条目里的名字，再不行为 null。 */
    public String knownName(UUID player) {
        synchronized (lock) {
            String key = player.toString();
            String n = names.get(key);
            if (n != null) {
                return n;
            }
            BalanceEntry e = balances.get(key);
            return e == null ? null : e.name();
        }
    }

    public boolean isFriend(UUID a, UUID b) {
        synchronized (lock) {
            Set<String> set = friendships.get(a.toString());
            return set != null && set.contains(b.toString());
        }
    }

    public Set<String> friendsOf(UUID player) {
        synchronized (lock) {
            Set<String> set = friendships.get(player.toString());
            return set == null ? Set.of() : Set.copyOf(set);
        }
    }

    /** 收件人视角的待处理好友请求：请求人UUID -> 时间戳。 */
    public Map<String, Long> friendRequestsFor(UUID recipient) {
        synchronized (lock) {
            Map<String, Long> m = friendRequests.get(recipient.toString());
            return m == null ? Map.of() : Map.copyOf(m);
        }
    }

    public boolean pendingFriendRequest(UUID from, UUID to) {
        synchronized (lock) {
            Map<String, Long> m = friendRequests.get(to.toString());
            return m != null && m.containsKey(from.toString());
        }
    }

    /** 按玩家名反查 UUID（先查名册，再查余额条目；忽略大小写）。 */
    public Optional<UUID> resolveName(String name) {
        synchronized (lock) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> e : names.entrySet()) {
                if (e.getValue().toLowerCase(Locale.ROOT).equals(lower)) {
                    return Optional.of(UUID.fromString(e.getKey()));
                }
            }
            for (Map.Entry<String, BalanceEntry> e : balances.entrySet()) {
                String n = e.getValue().name();
                if (n != null && n.toLowerCase(Locale.ROOT).equals(lower)) {
                    return Optional.of(UUID.fromString(e.getKey()));
                }
            }
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- 名册与好友（写）

    public void rememberName(UUID player, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        synchronized (lock) {
            names.put(player.toString(), name);
            saveLocked();
        }
    }

    /** 建立双向好友关系（幂等）。 */
    public void addFriend(UUID a, UUID b) {
        synchronized (lock) {
            friendships.computeIfAbsent(a.toString(), k -> new HashSet<>()).add(b.toString());
            friendships.computeIfAbsent(b.toString(), k -> new HashSet<>()).add(a.toString());
            saveLocked();
        }
    }

    public void removeFriend(UUID a, UUID b) {
        synchronized (lock) {
            Set<String> sa = friendships.get(a.toString());
            if (sa != null) {
                sa.remove(b.toString());
                if (sa.isEmpty()) {
                    friendships.remove(a.toString());
                }
            }
            Set<String> sb = friendships.get(b.toString());
            if (sb != null) {
                sb.remove(a.toString());
                if (sb.isEmpty()) {
                    friendships.remove(b.toString());
                }
            }
            saveLocked();
        }
    }

    /** 记录一条好友请求；重复请求或已是好友时返回 false。 */
    public boolean addFriendRequest(UUID from, UUID to, long ts) {
        synchronized (lock) {
            if (isFriend(from, to)) {
                return false;
            }
            Map<String, Long> m = friendRequests.computeIfAbsent(to.toString(), k -> new HashMap<>());
            if (m.containsKey(from.toString())) {
                return false;
            }
            m.put(from.toString(), ts);
            saveLocked();
            return true;
        }
    }

    public void removeFriendRequest(UUID from, UUID to) {
        synchronized (lock) {
            Map<String, Long> m = friendRequests.get(to.toString());
            if (m != null) {
                m.remove(from.toString());
                if (m.isEmpty()) {
                    friendRequests.remove(to.toString());
                }
                saveLocked();
            }
        }
    }

    // ---------------------------------------------------------------- 写

    public void putPendingLink(String code, PendingLink p) {
        synchronized (lock) {
            pendingLinks.put(code, p);
            saveLocked();
        }
    }

    public void removePendingLink(String code) {
        synchronized (lock) {
            pendingLinks.remove(code);
            saveLocked();
        }
    }

    public void removePendingLinksForPlayer(UUID player) {
        synchronized (lock) {
            String key = player.toString();
            pendingLinks.entrySet().removeIf(e -> e.getValue().playerUuid().equals(key));
            saveLocked();
        }
    }

    public void bind(String appUserId, Binding b) {
        synchronized (lock) {
            bindings.put(appUserId, b);
            playerBindings.put(b.playerUuid(), appUserId);
            saveLocked();
        }
    }

    public boolean unbindByAppUser(String appUserId) {
        synchronized (lock) {
            Binding b = bindings.remove(appUserId);
            if (b != null) {
                playerBindings.remove(b.playerUuid());
                saveLocked();
                return true;
            }
            return false;
        }
    }

    public boolean unbindByPlayer(UUID player) {
        synchronized (lock) {
            String key = player.toString();
            String appUserId = playerBindings.remove(key);
            if (appUserId != null) {
                bindings.remove(appUserId);
                saveLocked();
                return true;
            }
            return false;
        }
    }

    public void setBalance(UUID player, String name, double amount) {
        synchronized (lock) {
            balances.put(player.toString(), new BalanceEntry(name, amount));
            saveLocked();
        }
    }

    /** 追加账本条目；txId 已存在返回 false（幂等）。成功后按上限裁剪旧记录。 */
    public boolean addLedger(LedgerEntry entry) {
        synchronized (lock) {
            if (ledger.containsKey(entry.txId())) {
                return false;
            }
            ledger.put(entry.txId(), entry);
            while (ledger.size() > ledgerMax) {
                ledger.remove(ledger.keySet().iterator().next());
            }
            saveLocked();
            return true;
        }
    }

    /**
     * 每日限额：计数并返回累计数；超限返回 -1。max <= 0 表示不限（仍计数）。 */
    public int incrementDailyCount(UUID player, String date, int max) {
        synchronized (lock) {
            Map<String, Integer> m = dailyCounts.computeIfAbsent(player.toString(), k -> new HashMap<>());
            int count = m.getOrDefault(date, 0) + 1;
            if (max > 0 && count > max) {
                return -1;
            }
            m.put(date, count);
            saveLocked();
            return count;
        }
    }

    // ---------------------------------------------------------------- 持久化

    @SuppressWarnings("unused")
    private static StoreFile snapshot(DataStore s) {
        Map<String, Set<String>> fs = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : s.friendships.entrySet()) {
            fs.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        Map<String, Map<String, Long>> fr = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> e : s.friendRequests.entrySet()) {
            fr.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return new StoreFile(VERSION, new HashMap<>(s.bindings), new HashMap<>(s.playerBindings),
                new HashMap<>(s.pendingLinks), new HashMap<>(s.balances),
                new ArrayList<>(s.ledger.values()), new HashMap<>(s.dailyCounts),
                new HashMap<>(s.names), fs, fr);
    }

    private void saveLocked() {
        if (file == null) {
            return;
        }
        try {
            Path dir = file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            String json = GSON.toJson(snapshot(this));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("无法保存数据文件 " + file, e);
        }
    }

    /** 从磁盘加载；文件不存在则为空库。 */
    public void load() {
        synchronized (lock) {
            if (file == null || !Files.exists(file)) {
                return;
            }
            try {
                StoreFile f = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), StoreFile.class);
                if (f == null) {
                    return;
                }
                bindings.clear();
                if (f.bindings() != null) bindings.putAll(f.bindings());
                playerBindings.clear();
                if (f.playerBindings() != null) playerBindings.putAll(f.playerBindings());
                pendingLinks.clear();
                if (f.pendingLinks() != null) pendingLinks.putAll(f.pendingLinks());
                balances.clear();
                if (f.balances() != null) balances.putAll(f.balances());
                ledger.clear();
                if (f.ledger() != null) {
                    for (LedgerEntry e : f.ledger()) {
                        ledger.put(e.txId(), e);
                    }
                }
                dailyCounts.clear();
                if (f.dailyCounts() != null) {
                    for (Map.Entry<String, Map<String, Integer>> e : f.dailyCounts().entrySet()) {
                        dailyCounts.put(e.getKey(), new HashMap<>(e.getValue()));
                    }
                }
                names.clear();
                if (f.names() != null) names.putAll(f.names());
                friendships.clear();
                if (f.friendships() != null) {
                    for (Map.Entry<String, Set<String>> e : f.friendships().entrySet()) {
                        friendships.put(e.getKey(), new HashSet<>(e.getValue()));
                    }
                }
                friendRequests.clear();
                if (f.friendRequests() != null) {
                    for (Map.Entry<String, Map<String, Long>> e : f.friendRequests().entrySet()) {
                        friendRequests.put(e.getKey(), new HashMap<>(e.getValue()));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("无法读取数据文件 " + file, e);
            }
        }
    }

    public void save() {
        synchronized (lock) {
            saveLocked();
        }
    }
}