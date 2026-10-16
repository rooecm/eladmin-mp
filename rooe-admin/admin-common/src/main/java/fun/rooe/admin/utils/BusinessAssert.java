package fun.rooe.admin.utils;

import fun.rooe.admin.error.ErrorCode;
import fun.rooe.admin.exception.BusinessException;

/**
 * Assertions for business rules that must return a stable error code.
 */
public final class BusinessAssert {

    private BusinessAssert() {
    }

    public static void isTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            fail(errorCode);
        }
    }

    public static void isTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            fail(errorCode, message);
        }
    }

    public static void isFalse(boolean condition, ErrorCode errorCode) {
        isTrue(!condition, errorCode);
    }

    public static void isFalse(boolean condition, ErrorCode errorCode, String message) {
        isTrue(!condition, errorCode, message);
    }

    public static <T> T notNull(T value, ErrorCode errorCode) {
        if (value == null) {
            fail(errorCode);
        }
        return value;
    }

    public static <T> T notNull(T value, ErrorCode errorCode, String message) {
        if (value == null) {
            fail(errorCode, message);
        }
        return value;
    }

    public static String isBlank(String value, ErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            fail(errorCode);
        }
        return value;
    }

    public static String isBlank(String value, ErrorCode errorCode, String message) {
        if (value == null || value.isBlank()) {
            fail(errorCode, message);
        }
        return value;
    }

    public static void fail(ErrorCode errorCode) {
        throw new BusinessException(errorCode);
    }

    public static void fail(ErrorCode errorCode, String message) {
        throw new BusinessException(errorCode, message);
    }
}
