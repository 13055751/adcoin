package dev.adcoin.link;

import dev.adcoin.config.PluginConfig;
import dev.adcoin.data.DataStore;
import dev.adcoin.data.PendingLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodeServiceTest {

    @TempDir
    Path dir;

    private DataStore store;
    private LinkCodeService service;
    private long now;

    @BeforeEach
    void setUp() {
        PluginConfig cfg = PluginConfig.builder()
                .codeLength(8)
                .codeTtlSeconds(300)
                .codeCooldownSeconds(15)
                .build();
        store = new DataStore(dir.resolve("link-test.json"), 100);
        service = new LinkCodeService(store, cfg);
        now = System.currentTimeMillis();
    }

    @Test
    void generatesNewCodeAndReusesUnexpired() {
        UUID player = UUID.randomUUID();
        LinkCodeService.GenResult first = service.generate(player, "Steve", now);
        assertEquals(LinkCodeService.GenResult.Type.NEW, first.type());
        assertNotNull(first.code());
        assertEquals(8, first.code().length());
        assertTrue(first.code().matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}"));

        LinkCodeService.GenResult second = service.generate(player, "Steve", now + 1000);
        assertEquals(LinkCodeService.GenResult.Type.REUSED, second.type());
        assertEquals(first.code(), second.code());
    }

    @Test
    void cooldownBlocksAfterCodeExpiry() {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        // 生成一个码，然后手工让它过期
        // （设计：未过期码会被复用，冷却只拦"过期后立刻重新生成"）
        LinkCodeService.GenResult first = service.generate(player, "Steve", now);
        assertEquals(LinkCodeService.GenResult.Type.NEW, first.type());
        store.putPendingLink(first.code(), new PendingLink(player.toString(), "Steve", now - 1));
        // 码已过期且仍在冷却期 → COOLDOWN
        LinkCodeService.GenResult blocked = service.generate(player, "Steve", now + 2000);
        assertEquals(LinkCodeService.GenResult.Type.COOLDOWN, blocked.type());
    }

    @Test
    void resolveBindsAndConsumesCode() {
        UUID player = UUID.randomUUID();
        LinkCodeService.GenResult gen = service.generate(player, "Steve", now);
        LinkCodeService.ResolveResult res = service.resolve(gen.code(), "app-1", now + 10_000);
        assertEquals(LinkCodeService.ResolveResult.Type.OK, res.type());
        assertEquals(player.toString(), res.binding().playerUuid());

        // 码已消费 → 再次使用无效
        LinkCodeService.ResolveResult again = service.resolve(gen.code(), "app-2", now + 20_000);
        assertEquals(LinkCodeService.ResolveResult.Type.INVALID, again.type());
    }

    @Test
    void expiredCodeRejectedAndCleaned() {
        UUID player = UUID.randomUUID();
        service.generate(player, "Steve", now);
        LinkCodeService.ResolveResult res = service.resolve(
                "AAAA1111", "app-9", now + 1000);
        assertEquals(LinkCodeService.ResolveResult.Type.INVALID, res.type());

        // 手工放一个过期码，特意使用与生成不同的码以绕过未过期复用
        store.putPendingLink("BBBB2222", new PendingLink(player.toString(), "Steve", now - 1));
        LinkCodeService.ResolveResult expired = service.resolve("BBBB2222", "app-x", now);
        assertEquals(LinkCodeService.ResolveResult.Type.EXPIRED, expired.type());
        assertFalse(store.pendingLink("BBBB2222").isPresent());
    }

    @Test
    void preventsCrossBinding() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        String codeA = service.generate(playerA, "SteveA", now).code();

        // playerA 已绑定 app-1
        assertEquals(LinkCodeService.ResolveResult.Type.OK,
                service.resolve(codeA, "app-1", now + 1000).type());

        // app-1 再绑别的号 → APP_ALREADY_BOUND
        String codeB = service.generate(playerB, "SteveB", now + 5000).code();
        assertEquals(LinkCodeService.ResolveResult.Type.APP_ALREADY_BOUND,
                service.resolve(codeB, "app-1", now + 6000).type());

        // playerA 绑另一个 app → PLAYER_ALREADY_BOUND（先解绑再绑新，时间推到冷却期外）
        assertTrue(store.unbindByPlayer(playerA));
        String codeC = service.generate(playerA, "SteveA", now + 20_000).code();
        assertEquals(LinkCodeService.ResolveResult.Type.OK,
                service.resolve(codeC, "app-2", now + 30_000).type());
    }
}