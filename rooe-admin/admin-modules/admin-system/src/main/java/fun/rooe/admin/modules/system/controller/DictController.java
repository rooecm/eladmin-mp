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
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.modules.system.domain.Dict;
import fun.rooe.admin.modules.system.service.DictService;
import fun.rooe.admin.modules.system.domain.dto.DictQueryCriteria;
import fun.rooe.admin.response.ApiResult;
import fun.rooe.admin.response.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
* @author Zheng Jie
* @date 2019-04-10
*/
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：字典管理")
@RequestMapping("/api/dict")
public class DictController {

    private final DictService dictService;
    private static final String ENTITY_NAME = "dict";

    @Operation(summary = "导出字典数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check('dict:list')")
    public void exportDict(HttpServletResponse response, DictQueryCriteria criteria) throws IOException {
        dictService.download(dictService.queryAll(criteria), response);
    }

    @Operation(summary = "查询字典")
    @GetMapping(value = "/all")
    @PreAuthorize("@el.check('dict:list')")
    public ApiResult<List<Dict>> queryAllDict(){
        return ApiResult.success(dictService.queryAll(new DictQueryCriteria()));
    }

    @Operation(summary = "查询字典")
    @GetMapping
    @PreAuthorize("@el.check('dict:list')")
    public ApiResult<PageResult<Dict>> queryDict(DictQueryCriteria criteria){
        Page<Object> page = new Page<>(criteria.getPage(), criteria.getSize());
        return ApiResult.success(dictService.queryAll(criteria, page));
    }

    @Log("新增字典")
    @Operation(summary = "新增字典")
    @PostMapping
    @PreAuthorize("@el.check('dict:add')")
    public ApiResult<Void> createDict(@Validated @RequestBody Dict resources){
        if (resources.getId() != null) {
            throw new BadRequestException("A new "+ ENTITY_NAME +" cannot already have an ID");
        }
        dictService.create(resources);
        return ApiResult.success();
    }

    @Log("修改字典")
    @Operation(summary = "修改字典")
    @PutMapping
    @PreAuthorize("@el.check('dict:edit')")
    public ApiResult<Void> updateDict(@Validated(Dict.Update.class) @RequestBody Dict resources){
        dictService.update(resources);
        return ApiResult.success();
    }

    @Log("删除字典")
    @Operation(summary = "删除字典")
    @DeleteMapping
    @PreAuthorize("@el.check('dict:del')")
    public ApiResult<Void> deleteDict(@RequestBody Set<Long> ids){
        dictService.delete(ids);
        return ApiResult.success();
    }
}
