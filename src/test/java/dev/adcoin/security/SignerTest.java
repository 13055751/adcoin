package dev.adcoin.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignerTest {

    /** RFC 4231 Case 2：标准 HMAC-SHA256 测试向量。 */
    @Test
    void rfc4231Case2() {
        Signer signer = new Signer("Jefe");
        String hex = signer.hmacHex("what do ya want for nothing?");
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", hex);
    }

    @Test
    void verifyAcceptsCorrectAndRejectsWrongOrMissing() {
        Signer signer = new Signer("secret");
        String canonical = "reward\ntx-1\nuser-1\n1730000000000\n50\nadmob\nunit-1";
        String sig = signer.hmacHex(canonical);
        assertTrue(signer.verify(canonical, sig));
        assertTrue(signer.verify(canonical, sig.toUpperCase()));
        assertFalse(signer.verify(canonical, sig.replaceFirst(".", "0")));
        assertFalse(signer.verify(canonical, null));
        assertFalse(signer.verify(canonical, ""));
    }

    @Test
    void formatAmountNormalizesTrailingZeros() {
        assertEquals("50", Signer.formatAmount(50.0));
        assertEquals("50.5", Signer.formatAmount(50.5));
        assertEquals("0.5", Signer.formatAmount(0.5));
        assertEquals("100000", Signer.formatAmount(100000.0));
        assertEquals("1.25", Signer.formatAmount(1.25));
    }

    @Test
    void rewardCanonicalExactShape() {
        String expected = "reward\ntx-1\nuser-1\n1730000000000\n50\nadmob\nunit-1";
        assertEquals(expected, Signer.rewardCanonical("tx-1", "user-1", 1730000000000L, 50.0, "admob", "unit-1"));
        // adNetwork / adUnitId 可选，缺省为空串
        assertEquals("reward\ntx-1\nuser-1\n1730000000000\n50\n\n",
                Signer.rewardCanonical("tx-1", "user-1", 1730000000000L, 50.0, null, null));
    }

    @Test
    void linkCanonicalNormalizesCodeUpper() {
        assertEquals("link\nABC123\nuser-1\n1730000000000",
                Signer.linkCanonical("abc123", "user-1", 1730000000000L));
    }
}