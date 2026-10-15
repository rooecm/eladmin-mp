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

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.Menu;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.modules.system.domain.dto.MenuVo;
import fun.rooe.admin.modules.system.service.MenuService;
import fun.rooe.admin.modules.system.domain.dto.MenuQueryCriteria;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.infrastructure.utils.PageUtil;
import fun.rooe.admin.framework.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

/**
 * @author Zheng Jie
 * @date 2018-12-03
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：菜单管理")
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;
    private static final String ENTITY_NAME = "menu";

    @Operation(summary = "导出菜单数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check('menu:list')")
    public void exportMenu(HttpServletResponse response, MenuQueryCriteria criteria) throws Exception {
        menuService.download(menuService.queryAll(criteria, false), response);
    }

    @GetMapping(value = "/build")
    @Operation(summary = "获取前端所需菜单")
    public List<MenuVo> buildMenus(){
        List<Menu> menuList = menuService.findByUser(SecurityUtils.getCurrentUserId());
        List<Menu> menus = menuService.buildTree(menuList);
        return menuService.buildMenus(menus);
    }

    @Operation(summary = "返回全部的菜单")
    @GetMapping(value = "/lazy")
    @PreAuthorize("@el.check('menu:list','roles:list')")
    public List<Menu> queryAllMenu(@RequestParam Long pid){
        return menuService.getMenus(pid);
    }

    @Operation(summary = "根据菜单ID返回所有子节点ID，包含自身ID")
    @GetMapping(value = "/child")
    @PreAuthorize("@el.check('menu:list','roles:list')")
    public Set<Long> childMenu(@RequestParam Long id){
        Set<Menu> menuSet = new HashSet<>();
        List<Menu> menuList = menuService.getMenus(id);
        menuSet.add(menuService.getById(id));
        menuSet = menuService.getChildMenus(menuList, menuSet);
        Set<Long> ids = menuSet.stream().map(Menu::getId).collect(Collectors.toSet());
        return ids;
    }

    @GetMapping
    @Operation(summary = "查询菜单")
    @PreAuthorize("@el.check('menu:list')")
    public PageResult<Menu> queryMenu(MenuQueryCriteria criteria) throws Exception {
        List<Menu> menuList = menuService.queryAll(criteria, true);
        return PageUtil.toPage(menuList);
    }

    @Operation(summary = "查询菜单:根据ID获取同级与上级数据")
    @PostMapping("/superior")
    @PreAuthorize("@el.check('menu:list')")
    public List<Menu> getMenuSuperior(@RequestBody List<Long> ids) {
        Set<Menu> menus = new LinkedHashSet<>();
        if(!CollectionUtils.isEmpty(ids)){
            for (Long id : ids) {
                Menu menu = menuService.findById(id);
                List<Menu> menuList = menuService.getSuperior(menu, new ArrayList<>());
                for (Menu data : menuList) {
                    if(data.getId().equals(menu.getPid())) {
                        data.setSubCount(data.getSubCount() - 1);
                    }
                }
                menus.addAll(menuList);
            }
            // 编辑菜单时不显示自己以及自己下级的数据，避免出现PID数据环形问题
            menus = menus.stream().filter(i -> !ids.contains(i.getId())).collect(Collectors.toSet());
            return menuService.buildTree(new ArrayList<>(menus));
        }
        return menuService.getMenus(null);
    }

    @Log("新增菜单")
    @Operation(summary = "新增菜单")
    @PostMapping
    @PreAuthorize("@el.check('menu:add')")
    public void createMenu(@Validated @RequestBody Menu resources){
        if (resources.getId() != null) {
            throw new BadRequestException("A new "+ ENTITY_NAME +" cannot already have an ID");
        }
        menuService.create(resources);
    }

    @Log("修改菜单")
    @Operation(summary = "修改菜单")
    @PutMapping
    @PreAuthorize("@el.check('menu:edit')")
    public void updateMenu(@Validated(Menu.Update.class) @RequestBody Menu resources){
        menuService.update(resources);
    }

    @Log("删除菜单")
    @Operation(summary = "删除菜单")
    @DeleteMapping
    @PreAuthorize("@el.check('menu:del')")
    public void deleteMenu(@RequestBody Set<Long> ids){
        Set<Menu> menuSet = new HashSet<>();
        for (Long id : ids) {
            List<Menu> menuList = menuService.getMenus(id);
            menuSet.add(menuService.getById(id));
            menuSet = menuService.getChildMenus(menuList, menuSet);
        }
        menuService.delete(menuSet);
    }
}
