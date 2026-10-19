package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.dto.MenuDTO;
import fun.rooe.admin.modules.system.domain.dto.MenuRouterDTO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * HTTP endpoints for menu administration, tree queries, and router generation.
 */
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：菜单管理")
@RequestMapping("/api/menus")
public class MenuController {
    private final MenuService menuService;

    @GetMapping("/build")
    @Operation(summary = "获取前端所需菜单")
    public List<MenuRouterDTO> buildMenus() {
        return menuService.buildCurrentUserMenus();
    }

    @Operation(summary = "懒加载菜单")
    @GetMapping("/lazy")
    @PreAuthorize("@perm.has('menu:list') or @perm.has('roles:list')")
    public List<MenuDTO> queryAllMenu(@RequestParam Long pid) {
        return menuService.getMenus(pid);
    }

    @Operation(summary = "获取菜单及全部子节点 ID")
    @GetMapping("/child")
    @PreAuthorize("@perm.has('menu:list') or @perm.has('roles:list')")
    public Set<Long> childMenu(@RequestParam @Positive Long id) {
        return menuService.getChildIds(id);
    }

    @GetMapping
    @Operation(summary = "查询菜单")
    @PreAuthorize("@perm.has('menu:list')")
    public List<MenuDTO> queryMenu(@Valid MenuQueryRequest request) {
        return menuService.queryAll(request);
    }

    @Operation(summary = "查询菜单同级与上级数据")
    @PostMapping("/superior")
    @PreAuthorize("@perm.has('menu:list')")
    public List<MenuDTO> getMenuSuperior(@Valid @RequestBody MenuSuperiorRequest request) {
        return menuService.getSuperior(request);
    }

    @Log("新增菜单")
    @Operation(summary = "新增菜单")
    @PostMapping
    @PreAuthorize("@perm.has('menu:add')")
    public void createMenu(@Valid @RequestBody MenuCreateRequest request) {
        menuService.create(request);
    }

    @Log("修改菜单")
    @Operation(summary = "修改菜单")
    @PutMapping
    @PreAuthorize("@perm.has('menu:edit')")
    public void updateMenu(@Valid @RequestBody MenuUpdateRequest request) {
        menuService.update(request);
    }

    @Log("删除菜单")
    @Operation(summary = "删除菜单")
    @DeleteMapping
    @PreAuthorize("@perm.has('menu:del')")
    public void deleteMenu(@Valid @RequestBody MenuDeleteRequest request) {
        menuService.delete(request);
    }
}
