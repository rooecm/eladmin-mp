package fun.rooe.admin.modules.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Persistence model for sys_log.
 */
@TableName("sys_log")
public class SysLogDO implements Serializable {
    /**
     * Primary key.
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Operation description.
     */
    private String description;

    /**
     * Log type (INFO or ERROR).
     */
    private String logType;

    /**
     * Invoked method path.
     */
    private String method;

    /**
     * Request parameters.
     */
    private String params;

    /**
     * Request IP address.
     */
    private String requestIp;

    /**
     * Request elapsed time in milliseconds.
     */
    private Long time;

    /**
     * Operation user.
     */
    private String username;

    /**
     * IP origin region.
     */
    private String address;

    /**
     * Browser description.
     */
    private String browser;

    /**
     * Exception stack trace detail.
     */
    private String exceptionDetail;

    /**
     * Creation time.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public SysLogDO() {
    }

    public SysLogDO(String logType, Long time) {
        this.logType = logType;
        this.time = time;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getExceptionDetail() {
        return exceptionDetail;
    }

    public void setExceptionDetail(String exceptionDetail) {
        this.exceptionDetail = exceptionDetail;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
