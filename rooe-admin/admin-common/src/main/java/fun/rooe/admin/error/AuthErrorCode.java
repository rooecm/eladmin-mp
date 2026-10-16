package fun.rooe.admin.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    BAD_CREDENTIALS("AUTH_1001", "用户名或密码错误"),
    ACCOUNT_DISABLED("AUTH_1002", "账号未激活或已禁用"),
    SESSION_INVALID("AUTH_1005", "登录状态无效或已失效，请重新登录"),
    CAPTCHA_INVALID("AUTH_1007", "验证码无效或已过期");

    private final String code;
    private final String message;
}
