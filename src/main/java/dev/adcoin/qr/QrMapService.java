package dev.adcoin.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * /adlink 绑定码二维码 → **地图物品**显示。
 * 内容格式：{@code ADCOIN:<短码>}（App 扫码后取短码走同一条绑定链路）。
 * 玩家在游戏里把地图拿在手上，屏幕上即出现可扫码的二维码。
 */
public final class QrMapService {

    private static final int MAP_SIZE = 128;

    private final Plugin plugin;
    private final NamespacedKey markerKey;

    public QrMapService(Plugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "bind_qr");
    }

    /** 生成/刷新玩家的绑定二维码地图。返回 false 表示地图创建失败（聊天码仍可用）。 */
    public boolean giveOrRefresh(Player player, String shortCode) {
        String content = "ADCOIN:" + shortCode;
        try {
            World world = player.getWorld();
            MapView view = Bukkit.createMap(world);
            for (MapRenderer r : List.copyOf(view.getRenderers())) {
                view.removeRenderer(r);
            }
            final BufferedImage qr = renderQr(content);
            view.addRenderer(new MapRenderer() {
                private boolean drawn;

                @Override
                public void render(MapView map, MapCanvas canvas, Player p) {
                    if (drawn) {
                        return;
                    }
                    canvas.drawImage(0, 0, qr);
                    drawn = true;
                }
            });

            ItemStack item = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) item.getItemMeta();
            meta.setMapId(view.getId());
            meta.displayName(Component.text("AdCoin 绑定码 · " + shortCode));
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, content);
            item.setItemMeta(meta);

            // 已带同内容二维码则不重复发
            ItemStack[] contents = player.getInventory().getContents();
            for (ItemStack it : contents) {
                if (it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta()) {
                    String held = it.getItemMeta().getPersistentDataContainer()
                            .get(markerKey, PersistentDataType.STRING);
                    if (content.equals(held)) {
                        return true;
                    }
                }
            }
            player.getInventory().addItem(item);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("绑定二维码地图生成失败: " + t.getMessage());
            return false;
        }
    }

    /** 二维码图像：白底黑块，居中于 128x128 地图（含静区，手机易扫）。 */
    private BufferedImage renderQr(String content) throws Exception {
        BitMatrix m = new QRCodeWriter()
                .encode(content, BarcodeFormat.QR_CODE, 0, 0); // 0x0 = 按内容自适应最小模块
        // 目标：整体缩放到 ~104px，留 12px 静区
        int scale = Math.max(1, 104 / Math.max(m.getWidth(), m.getHeight()));
        int side = Math.min(m.getWidth() * scale, m.getHeight() * scale);
        int offset = (MAP_SIZE - side) / 2;

        BufferedImage out = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
        g.setColor(Color.BLACK);
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (m.get(x, y)) {
                    g.fillRect(offset + x * scale, offset + y * scale, scale, scale);
                }
            }
        }
        g.dispose();
        return out;
    }
}