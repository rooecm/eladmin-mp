package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.dto.*;
import fun.rooe.admin.modules.system.service.RoleService;
import fun.rooe.admin.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * HTTP endpoints for role administration and role-menu assignments.
 */
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：角色管理")
@RequestMapping("/api/roles")
public class RoleController {
    private final RoleService roleService;

    @Operation(summary = "获取单个角色")
    @GetMapping("/{id}")
    @PreAuthorize("@el.check('roles:list')")
    public RoleDto findRoleById(@PathVariable @Positive Long id) {
        return roleService.findDtoById(id);
    }

    @Operation(summary = "返回全部角色")
    @GetMapping("/all")
    @PreAuthorize("@el.check('roles:list','user:add','user:edit')")
    public List<RoleDto> queryAllRole() {
        return roleService.queryAll();
    }

    @Operation(summary = "查询角色")
    @GetMapping
    @PreAuthorize("@el.check('roles:list')")
    public PageResult<RoleDto> queryRole(@Valid RoleQueryRequest request) {
        return roleService.queryAll(request);
    }

    @Operation(summary = "获取用户级别")
    @GetMapping("/level")
    public Map<String, Integer> getRoleLevel() {
        return Map.of("level", roleService.getCurrentUserLevel());
    }

    @Log("新增角色")
    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("@el.check('roles:add')")
    public void createRole(@Valid @RequestBody RoleCreateRequest request) {
        roleService.create(request);
    }

    @Log("修改角色")
    @Operation(summary = "修改角色")
    @PutMapping
    @PreAuthorize("@el.check('roles:edit')")
    public void updateRole(@Valid @RequestBody RoleUpdateRequest request) {
        roleService.update(request);
    }

    @Log("修改角色菜单")
    @Operation(summary = "修改角色菜单")
    @PutMapping("/menu")
    @PreAuthorize("@el.check('roles:edit')")
    public void updateRoleMenu(@Valid @RequestBody RoleMenuUpdateRequest request) {
        roleService.updateMenu(request);
    }

    @Log("删除角色")
    @Operation(summary = "删除角色")
    @DeleteMapping
    @PreAuthorize("@el.check('roles:del')")
    public void deleteRole(@Valid @RequestBody RoleDeleteRequest request) {
        roleService.delete(request);
    }
}
