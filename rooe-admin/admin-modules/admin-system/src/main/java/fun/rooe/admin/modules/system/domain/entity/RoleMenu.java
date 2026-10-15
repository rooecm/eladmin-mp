package fun.rooe.admin.modules.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_roles_menus")
public class RoleMenu {
    private Long roleId;
    private Long menuId;

    public RoleMenu() {
    }

    public RoleMenu(Long roleId, Long menuId) {
        this.roleId = roleId;
        this.menuId = menuId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }
}
