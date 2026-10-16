package fun.rooe.admin.infrastructure.error;

import fun.rooe.admin.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailErrorCode implements ErrorCode {
    NOT_CONFIGURED("EMAIL_1001", "请先配置邮件服务，再操作"),
    SEND_FAILED("EMAIL_1002", "邮件发送失败，请检查配置或稍后重试");

    private final String code;
    private final String message;
}
