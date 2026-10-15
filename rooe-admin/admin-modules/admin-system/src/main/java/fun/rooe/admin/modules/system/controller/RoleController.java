/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package fun.rooe.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.Role;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.modules.system.service.RoleService;
import fun.rooe.admin.modules.system.domain.dto.RoleQueryCriteria;
import fun.rooe.admin.response.ApiResult;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.framework.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;

/**
 * @author Zheng Jie
 * @date 2018-12-03
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：角色管理")
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    private static final String ENTITY_NAME = "role";

    @Operation(summary = "获取单个role")
    @GetMapping(value = "/{id}")
    @PreAuthorize("@el.check('roles:list')")
    public ApiResult<Role> findRoleById(@PathVariable Long id){
        return ApiResult.success(roleService.findById(id));
    }

    @Operation(summary = "导出角色数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check('role:list')")
    public void exportRole(HttpServletResponse response, RoleQueryCriteria criteria) throws IOException {
        roleService.download(roleService.queryAll(criteria), response);
    }

    @Operation(summary = "返回全部的角色")
    @GetMapping(value = "/all")
    @PreAuthorize("@el.check('roles:list','user:add','user:edit')")
    public ApiResult<List<Role>> queryAllRole(){
        return ApiResult.success(roleService.queryAll());
    }

    @Operation(summary = "查询角色")
    @GetMapping
    @PreAuthorize("@el.check('roles:list')")
    public ApiResult<PageResult<Role>> queryRole(RoleQueryCriteria criteria){
        Page<Object> page = new Page<>(criteria.getPage(), criteria.getSize());
        return ApiResult.success(roleService.queryAll(criteria, page));
    }

    @Operation(summary = "获取用户级别")
    @GetMapping(value = "/level")
    public ApiResult<Map<String, Integer>> getRoleLevel(){
        return ApiResult.success(Map.of("level", getLevels()));
    }

    @Log("新增角色")
    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("@el.check('roles:add')")
    public ApiResult<Void> createRole(@Validated @RequestBody Role resources){
        if (resources.getId() != null) {
            throw new BadRequestException("A new "+ ENTITY_NAME +" cannot already have an ID");
        }
        checkLevel(resources.getLevel());
        roleService.create(resources);
        return ApiResult.success();
    }

    @Log("修改角色")
    @Operation(summary = "修改角色")
    @PutMapping
    @PreAuthorize("@el.check('roles:edit')")
    public ApiResult<Void> updateRole(@Validated(Role.Update.class) @RequestBody Role resources){
        Role role = roleService.getById(resources.getId());
        checkLevel(role.getLevel());
        checkLevel(resources.getLevel());
        roleService.update(resources);
        return ApiResult.success();
    }

    @Log("修改角色菜单")
    @Operation(summary = "修改角色菜单")
    @PutMapping(value = "/menu")
    @PreAuthorize("@el.check('roles:edit')")
    public ApiResult<Void> updateRoleMenu(@RequestBody Role resources){
        Role role = roleService.getById(resources.getId());
        checkLevel(role.getLevel());
        roleService.updateMenu(resources);
        return ApiResult.success();
    }

    @Log("删除角色")
    @Operation(summary = "删除角色")
    @DeleteMapping
    @PreAuthorize("@el.check('roles:del')")
    public ApiResult<Void> deleteRole(@RequestBody Set<Long> ids){
        for (Long id : ids) {
            Role role = roleService.getById(id);
            checkLevel(role.getLevel());
        }
        // 验证是否被用户关联
        roleService.verification(ids);
        roleService.delete(ids);
        return ApiResult.success();
    }

    /**
     * 获取用户的角色级别
     * @return /
     */
    private int getLevels(){
        List<Integer> levels = roleService.findByUsersId(SecurityUtils.getCurrentUserId()).stream().map(Role::getLevel).collect(Collectors.toList());
        return Collections.min(levels);
    }

    private void checkLevel(Integer level){
        int min = getLevels();
        if(level == null || level < min){
            throw new BadRequestException("权限不足，你的角色级别：" + min + "，低于操作的角色级别：" + level);
        }
    }
}
