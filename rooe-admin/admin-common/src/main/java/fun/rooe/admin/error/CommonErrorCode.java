package fun.rooe.admin.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    INVALID_ARGUMENT("COMMON_1001", "请求参数或格式不正确"),
    RESOURCE_NOT_FOUND("COMMON_1002", "请求资源不存在"),
    DATA_ALREADY_EXISTS("COMMON_1003", "数据已存在"),
    TOO_MANY_REQUESTS("COMMON_1004", "请求过于频繁，请稍后重试"),
    BUSINESS_REJECTED("COMMON_1005", "当前操作无法完成"),
    FILE_TOO_LARGE("COMMON_1008", "文件超出规定大小"),
    DATA_IN_USE("COMMON_1010", "数据存在关联，请解除关联后再试"),
    ACCESS_DENIED("PERMISSION_1001", "没有权限执行此操作"),
    INTERNAL_ERROR("SYS_1001", "系统内部异常，请稍后重试");

    private final String code;
    private final String message;
}
