package fun.rooe.admin.modules.system.error;

import fun.rooe.admin.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    PASSWORD_INCORRECT("USER_1004", "密码错误");

    private final String code;
    private final String message;
}
