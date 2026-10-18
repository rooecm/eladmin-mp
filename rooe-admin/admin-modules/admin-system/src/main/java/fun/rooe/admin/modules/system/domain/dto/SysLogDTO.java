package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Administrative log response, excluding exception details.
 */
@Data
@Schema(description = "管理端日志")
public class SysLogDTO {
    @Schema(description = "日志 ID")
    private Long id;

    @Schema(description = "操作用户")
    private String username;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "方法名")
    private String method;

    @Schema(description = "参数")
    private String params;

    @Schema(description = "请求 IP")
    private String requestIp;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "请求耗时，单位毫秒")
    private Long time;

    @Schema(description = "创建日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
