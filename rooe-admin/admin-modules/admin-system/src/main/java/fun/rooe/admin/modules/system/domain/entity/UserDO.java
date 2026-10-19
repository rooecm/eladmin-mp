package fun.rooe.admin.modules.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Persistence model for sys_user.
 */
@TableName("sys_user")
public class UserDO implements Serializable {
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long authVersion = 0L;
    private Boolean mustChangePassword = false;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;

    public Long getAuthVersion() {
        return authVersion;
    }

    public void setAuthVersion(Long value) {
        this.authVersion = value;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean value) {
        this.mustChangePassword = value;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime value) {
        this.lastLoginAt = value;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String value) {
        this.lastLoginIp = value;
    }

    /**
     * Primary key.
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Department ID.
     */
    @TableField("dept_id")
    private Long deptId;

    /**
     * Login name.
     */
    private String username;

    /**
     * Display name.
     */
    private String nickName;

    /**
     * Gender.
     */
    private String gender;

    /**
     * Mobile phone number.
     */
    private String phone;

    /**
     * Email address.
     */
    private String email;

    /**
     * Avatar file name.
     */
    private String avatarName;

    /**
     * Avatar storage path.
     */
    private String avatarPath;

    /**
     * Encoded password.
     */
    private String password;

    /**
     * Whether the account is an administrator.
     */
    private Boolean isAdmin = false;

    /**
     * Whether the account is enabled.
     */
    private Boolean enabled;

    /**
     * Creator username.
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * Last updater username.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * Time of the last password reset.
     */
    private LocalDateTime pwdResetTime;

    /**
     * Creation time.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * Last update time.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarName() {
        return avatarName;
    }

    public void setAvatarName(String avatarName) {
        this.avatarName = avatarName;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getPwdResetTime() {
        return pwdResetTime;
    }

    public void setPwdResetTime(LocalDateTime pwdResetTime) {
        this.pwdResetTime = pwdResetTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
