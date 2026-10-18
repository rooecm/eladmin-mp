package fun.rooe.admin.modules.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Persistence model for sys_menu.
 */
@TableName("sys_menu")
public class MenuDO implements Serializable {
    /**
     * Primary key.
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Parent menu ID.
     */
    private Long pid;

    /**
     * Number of direct child menus.
     */
    private Integer subCount = 0;

    /**
     * Menu type.
     */
    private Integer type;

    /**
     * Menu title.
     */
    private String title;

    /**
     * Frontend component name.
     */
    @TableField("name")
    private String componentName;

    /**
     * Frontend component path.
     */
    private String component;

    /**
     * Display order.
     */
    private Integer menuSort = 999;

    /**
     * Icon name.
     */
    private String icon;

    /**
     * Route or external link path.
     */
    private String path;

    /**
     * Whether the path is rendered in an iframe.
     */
    private Boolean iFrame;

    /**
     * Whether the route is cached.
     */
    private Boolean cache;

    /**
     * Whether the menu is hidden.
     */
    private Boolean hidden;

    /**
     * Permission identifier.
     */
    private String permission;

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

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public Integer getSubCount() {
        return subCount;
    }

    public void setSubCount(Integer subCount) {
        this.subCount = subCount;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public Integer getMenuSort() {
        return menuSort;
    }

    public void setMenuSort(Integer menuSort) {
        this.menuSort = menuSort;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Boolean getIFrame() {
        return iFrame;
    }

    public void setIFrame(Boolean iFrame) {
        this.iFrame = iFrame;
    }

    public Boolean getCache() {
        return cache;
    }

    public void setCache(Boolean cache) {
        this.cache = cache;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
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
