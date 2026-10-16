package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

/**
 * Personal activity response, limited to fields shown in the user center.
 */
@Data
@Schema(description = "当前用户操作日志")
public class SysLogUserDTO {
    @Schema(description = "日志 ID")
    private Long id;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "请求 IP")
    private String requestIp;

    @Schema(description = "请求耗时，单位毫秒")
    private Long time;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "创建日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Timestamp createTime;
}
