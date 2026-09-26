package dev.adcoin.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialStoreTest {

    @TempDir
    Path dir;

    private DataStore newStore() {
        return new DataStore(dir.resolve("social-test.json"), 100);
    }

    @Test
    void friendshipIsSymmetric() {
        DataStore s = newStore();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        s.addFriend(a, b);
        assertTrue(s.isFriend(a, b));
        assertTrue(s.isFriend(b, a));
        assertEquals(1, s.friendsOf(a).size());
        s.removeFriend(a, b);
        assertFalse(s.isFriend(a, b));
        assertFalse(s.isFriend(b, a));
        assertTrue(s.friendsOf(a).isEmpty());
    }

    @Test
    void friendRequestLifecycle() {
        DataStore s = newStore();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        assertTrue(s.addFriendRequest(from, to, 1000L));
        assertFalse(s.addFriendRequest(from, to, 2000L)); // 重复请求
        assertTrue(s.pendingFriendRequest(from, to));
        assertEquals(1, s.friendRequestsFor(to).size());
        s.removeFriendRequest(from, to);
        assertFalse(s.pendingFriendRequest(from, to));
        assertTrue(s.friendRequestsFor(to).isEmpty());
    }

    @Test
    void addFriendRequestRejectedWhenAlreadyFriends() {
        DataStore s = newStore();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        s.addFriend(a, b);
        assertFalse(s.addFriendRequest(a, b, 1L));
    }

    @Test
    void namesRememberedAndResolvable() {
        DataStore s = newStore();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        s.rememberName(a, "Steve");
        s.setBalance(b, "Alex", 5.0);
        assertEquals("Steve", s.knownName(a));
        assertEquals("Alex", s.knownName(b));
        assertEquals(Optional.of(a), s.resolveName("steve")); // 忽略大小写
        assertEquals(Optional.of(b), s.resolveName("ALEX"));
        assertTrue(s.resolveName("nobody").isEmpty());
    }
}