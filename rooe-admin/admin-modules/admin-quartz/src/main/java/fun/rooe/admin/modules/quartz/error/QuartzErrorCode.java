package fun.rooe.admin.modules.quartz.error;

import fun.rooe.admin.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuartzErrorCode implements ErrorCode {
    OPERATION_FAILED("QUARTZ_1005", "定时任务操作失败，请稍后重试");

    private final String code;
    private final String message;
}
