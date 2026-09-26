package dev.adcoin.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignerSocialCanonicalTest {

    @Test
    void transferCanonicalExactShape() {
        String expected = "transfer\ntx-9\nuser-a\nuser-b\n1730000000000\n30.5";
        assertEquals(expected,
                Signer.transferCanonical("tx-9", "user-a", "user-b", 1730000000000L, 30.5));
        assertEquals("transfer\ntx-9\nuser-a\nuser-b\n1730000000000\n30",
                Signer.transferCanonical("tx-9", "user-a", "user-b", 1730000000000L, 30.0));
    }

    @Test
    void friendCanonicalPerAction() {
        assertEquals("friend\nrequest\nuser-a\nuser-b\n1730000000000",
                Signer.friendCanonical("request", "user-a", "user-b", 1730000000000L));
        assertEquals("friend\nlist\nuser-a\n\n1730000000000",
                Signer.friendCanonical("list", "user-a", null, 1730000000000L));
    }

    @Test
    void signerVerifiesSocialCanonicals() {
        Signer signer = new Signer("social-secret");
        String canonical = Signer.friendCanonical("accept", "user-a", "user-b", 1730000000000L);
        assertTrue(signer.verify(canonical, signer.hmacHex(canonical)));
    }

    @Test
    void balanceCanonicalExactShape() {
        assertEquals("balance\nuser-a\n1730000000000",
                Signer.balanceCanonical("user-a", 1730000000000L));
    }
}