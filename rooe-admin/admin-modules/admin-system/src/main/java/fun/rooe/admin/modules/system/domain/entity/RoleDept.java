package fun.rooe.admin.modules.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_roles_depts")
public class RoleDept {
    private Long roleId;
    private Long deptId;

    public RoleDept() {
    }

    public RoleDept(Long roleId, Long deptId) {
        this.roleId = roleId;
        this.deptId = deptId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
}
