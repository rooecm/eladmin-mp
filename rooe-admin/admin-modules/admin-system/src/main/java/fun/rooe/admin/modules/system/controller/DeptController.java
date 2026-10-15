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

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.dto.DeptDTO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.service.DeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "查询部门")
    @GetMapping
    @PreAuthorize("@el.check('user:list','dept:list')")
    public List<DeptDTO> queryDept(@Valid DeptQueryRequest request) {
        return deptService.queryAll(request);
    }

    @Operation(summary = "查询部门:根据ID获取同级与上级数据")
    @PostMapping("/superior")
    @PreAuthorize("@el.check('user:list','dept:list')")
    public List<DeptDTO> getDeptSuperior(@Valid @RequestBody DeptSuperiorRequest request) {
        return deptService.getSuperior(request);
    }

    @Log("新增部门")
    @Operation(summary = "新增部门")
    @PostMapping
    @PreAuthorize("@el.check('dept:add')")
    public void createDept(@Valid @RequestBody DeptCreateRequest request) {
        deptService.create(request);
    }

    @Log("修改部门")
    @Operation(summary = "修改部门")
    @PutMapping
    @PreAuthorize("@el.check('dept:edit')")
    public void updateDept(@Valid @RequestBody DeptUpdateRequest request) {
        deptService.update(request);
    }

    @Log("删除部门")
    @Operation(summary = "删除部门")
    @DeleteMapping
    @PreAuthorize("@el.check('dept:del')")
    public void deleteDept(@Valid @RequestBody DeptDeleteRequest request) {
        deptService.delete(request);
    }
}
