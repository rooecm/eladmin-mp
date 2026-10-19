package fun.rooe.admin.framework.security.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 生成不可解析的随机 Token，使用 SHA-256 摘要定位服务端会话。
 */
@Service
public class TokenService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final SecureRandom random = new SecureRandom();

    public String generateToken() {
        // 32 字节提供 256 位随机性，去掉 Base64URL 填充后固定为 43 个字符。
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 仅检查格式；是否有效及是否过期必须查询服务端会话。
     */
    public boolean isValid(String token) {
        return token != null && TOKEN_PATTERN.matcher(token).matches();
    }

    public String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
