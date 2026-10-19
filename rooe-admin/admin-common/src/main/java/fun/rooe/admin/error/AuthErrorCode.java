package fun.rooe.admin.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    BAD_CREDENTIALS("AUTH_1001", "用户名或密码错误"),
    ACCOUNT_DISABLED("AUTH_1002", "账号未激活或已禁用"),
    SESSION_INVALID("AUTH_1005", "登录状态无效或已失效，请重新登录"),
    CAPTCHA_INVALID("AUTH_1007", "验证码无效或已过期"),
    PASSWORD_CHANGE_REQUIRED("AUTH_PASSWORD_CHANGE_REQUIRED", "请先修改密码"),
    OLD_PASSWORD_INCORRECT("AUTH_OLD_PASSWORD_INCORRECT", "旧密码错误"),
    PRIVILEGE_ESCALATION("AUTH_PRIVILEGE_ESCALATION", "不能授予或修改超出自身范围的权限"),
    PROTECTED_RESOURCE("AUTH_PROTECTED_RESOURCE", "受保护的管理员资源不能执行此操作"),
    UNAVAILABLE("AUTH_UNAVAILABLE", "认证服务暂不可用，请稍后重试");

    private final String code;
    private final String message;
}
