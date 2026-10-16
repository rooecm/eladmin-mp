package fun.rooe.admin.utils;

import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessAssertTest {

    @Test
    void acceptsValidBusinessConditions() {
        assertDoesNotThrow(() -> BusinessAssert.isTrue(true, AuthErrorCode.CAPTCHA_INVALID));
        assertDoesNotThrow(() -> BusinessAssert.isFalse(false, AuthErrorCode.CAPTCHA_INVALID));
        assertEquals("captcha", BusinessAssert.isBlank("captcha", AuthErrorCode.CAPTCHA_INVALID));
        Object value = new Object();
        assertSame(value, BusinessAssert.notNull(value, AuthErrorCode.CAPTCHA_INVALID));
    }

    @Test
    void rejectsInvalidBusinessConditionsWithTheConfiguredErrorCode() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> BusinessAssert.isTrue(false, AuthErrorCode.CAPTCHA_INVALID));

        assertSame(AuthErrorCode.CAPTCHA_INVALID, exception.getErrorCode());
        assertEquals(AuthErrorCode.CAPTCHA_INVALID.getMessage(), exception.getMessage());
    }

    @Test
    void rejectsNullAndBlankValues() {
        assertThrows(BusinessException.class,
                () -> BusinessAssert.notNull(null, AuthErrorCode.CAPTCHA_INVALID));
        assertThrows(BusinessException.class,
                () -> BusinessAssert.isBlank("  ", AuthErrorCode.CAPTCHA_INVALID));
    }

    @Test
    void supportsCustomPublicMessages() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> BusinessAssert.isTrue(false, AuthErrorCode.CAPTCHA_INVALID, "验证码错误"));

        assertSame(AuthErrorCode.CAPTCHA_INVALID, exception.getErrorCode());
        assertEquals("验证码错误", exception.getMessage());
    }
}
