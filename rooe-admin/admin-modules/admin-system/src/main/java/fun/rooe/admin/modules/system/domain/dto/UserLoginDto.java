package fun.rooe.admin.modules.system.domain.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Security principal data. The password is never part of an HTTP response.
 */
public class UserLoginDto extends UserDto {
    @JsonIgnore
    @JSONField(serialize = false)
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
