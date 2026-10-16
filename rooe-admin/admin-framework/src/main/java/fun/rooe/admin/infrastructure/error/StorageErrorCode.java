package fun.rooe.admin.infrastructure.error;

import fun.rooe.admin.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StorageErrorCode implements ErrorCode {
    OPERATION_FAILED("STORAGE_1003", "文件操作失败，请稍后重试"),
    BUCKET_UNAVAILABLE("STORAGE_1005", "存储桶不可用，请检查配置或权限");

    private final String code;
    private final String message;
}
