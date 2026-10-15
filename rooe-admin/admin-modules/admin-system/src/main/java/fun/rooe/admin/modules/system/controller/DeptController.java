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
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.modules.system.domain.Dept;
import fun.rooe.admin.modules.system.domain.dto.DeptDto;
import fun.rooe.admin.modules.system.service.DeptService;
import fun.rooe.admin.modules.system.domain.dto.DeptQueryCriteria;
import fun.rooe.admin.response.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.util.CollectionUtils;

/**
* @author Zheng Jie
* @date 2019-03-25
*/
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：部门管理")
@RequestMapping("/api/dept")
public class DeptController {

    private final DeptService deptService;
    private static final String ENTITY_NAME = "dept";

    @Operation(summary = "查询部门")
    @GetMapping
    @PreAuthorize("@el.check('user:list','dept:list')")
    public PageResult<DeptDto> queryDept(DeptQueryCriteria criteria) throws Exception {
        return deptService.queryAll(criteria);
    }

    @Operation(summary = "查询部门:根据ID获取同级与上级数据")
    @PostMapping("/superior")
    @PreAuthorize("@el.check('user:list','dept:list')")
    public PageResult<DeptDto> getDeptSuperior(@RequestBody List<Long> ids,
                                               @RequestParam(defaultValue = "false") Boolean exclude) {
        return deptService.getSuperior(ids, exclude);
    }

    @Log("新增部门")
    @Operation(summary = "新增部门")
    @PostMapping
    @PreAuthorize("@el.check('dept:add')")
    public void createDept(@Validated @RequestBody Dept resources){
        if (resources.getId() != null) {
            throw new BadRequestException("A new "+ ENTITY_NAME +" cannot already have an ID");
        }
        deptService.create(resources);
    }

    @Log("修改部门")
    @Operation(summary = "修改部门")
    @PutMapping
    @PreAuthorize("@el.check('dept:edit')")
    public void updateDept(@Validated(Dept.Update.class) @RequestBody Dept resources){
        deptService.update(resources);
    }

    @Log("删除部门")
    @Operation(summary = "删除部门")
    @DeleteMapping
    @PreAuthorize("@el.check('dept:del')")
    public void deleteDept(@RequestBody Set<Long> ids){
        Set<Dept> depts = new HashSet<>();
        for (Long id : ids) {
            List<Dept> deptList = deptService.findByPid(id);
            depts.add(deptService.findById(id));
            if(!CollectionUtils.isEmpty(deptList)){
                depts = deptService.getDeleteDepts(deptList, depts);
            }
        }
        // 验证是否被角色或用户关联
        deptService.verification(depts);
        deptService.delete(depts);
    }
}
