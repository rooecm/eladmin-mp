package fun.rooe.admin;

import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.error.ErrorCode;
import fun.rooe.admin.infrastructure.error.EmailErrorCode;
import fun.rooe.admin.infrastructure.error.StorageErrorCode;
import fun.rooe.admin.modules.system.error.UserErrorCode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeRegistryTest {
    @Test
    void everyDeclaredErrorHasAUniqueCodeAndMessage() {
        Set<String> codes = new HashSet<>();
        for (Class<?> type : List.of(CommonErrorCode.class, AuthErrorCode.class, StorageErrorCode.class,
                EmailErrorCode.class, UserErrorCode.class)) {
            for (Object constant : type.getEnumConstants()) {
                ErrorCode error = (ErrorCode) constant;
                assertTrue(error.getCode().matches("[A-Z]+_[0-9]{4}"), error.getCode());
                assertFalse(error.getMessage().isBlank(), error.getCode());
                assertTrue(codes.add(error.getCode()), "Duplicate error code: " + error.getCode());
            }
        }
    }

}
