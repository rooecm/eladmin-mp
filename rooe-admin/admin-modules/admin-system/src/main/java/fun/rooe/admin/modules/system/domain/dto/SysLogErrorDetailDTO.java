package fun.rooe.admin.modules.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Exception detail response, preserving the existing exception property.
 */
@Data
@AllArgsConstructor
public class SysLogErrorDetailDTO {
    @Schema(description = "异常堆栈详情")
    private String exception;
}
