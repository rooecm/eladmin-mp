package fun.rooe.admin.modules.security.security;

import fun.rooe.admin.config.security.security.TokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import fun.rooe.admin.config.security.config.SecurityProperties;
import fun.rooe.admin.utils.RedisUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TokenProviderTest {

    private TokenProvider tokenProvider(byte keyByte) {
        byte[] key = new byte[64];
        java.util.Arrays.fill(key, keyByte);
        SecurityProperties properties = new SecurityProperties();
        properties.setBase64Secret(Base64.getEncoder().encodeToString(key));
        properties.setOnlineKey("online:");
        TokenProvider provider = new TokenProvider(mock(RedisUtils.class), properties);
        provider.afterPropertiesSet();
        return provider;
    }

    @Test
    void preservesUserClaimsAndCreatesUniqueSessions() {
        TokenProvider provider = tokenProvider((byte) 1);
        String token = provider.createToken(42L, "admin");
        Claims claims = provider.getClaims(token);

        assertEquals("admin", claims.getSubject());
        assertEquals(42L, ((Number) claims.get(TokenProvider.AUTHORITIES_UID_KEY)).longValue());
        assertNotNull(claims.get(TokenProvider.AUTHORITIES_UUID_KEY));
        assertNull(claims.getExpiration());
        assertEquals("online:admin:" + provider.getId(token), provider.loginKey(token));
        assertEquals("admin", provider.getAuthentication(token).getName());
        assertNotEquals(provider.getId(token), provider.getId(provider.createToken(42L, "admin")));
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        TokenProvider provider = tokenProvider((byte) 1);
        String token = tokenProvider((byte) 2).createToken(42L, "admin");

        assertThrows(SignatureException.class, () -> provider.getClaims(token));
    }
}
