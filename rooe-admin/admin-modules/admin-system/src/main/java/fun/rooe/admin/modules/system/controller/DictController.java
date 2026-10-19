package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.dto.DictDTO;
import fun.rooe.admin.modules.system.domain.request.DictCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictDeleteRequest;
import fun.rooe.admin.modules.system.domain.request.DictQueryRequest;
import fun.rooe.admin.modules.system.domain.request.DictUpdateRequest;
import fun.rooe.admin.modules.system.service.DictService;
import fun.rooe.admin.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/** HTTP endpoints for dictionary administration and exports. */
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：字典管理")
@RequestMapping("/api/dict")
public class DictController {
    private final DictService dictService;

    @Operation(summary = "导出字典数据")
    @GetMapping("/download")
    @PreAuthorize("@perm.has('dict:list')")
    public void exportDict(HttpServletResponse response, @Valid DictQueryRequest request)
            throws IOException {
        dictService.download(dictService.queryList(request), response);
    }

    @Operation(summary = "查询全部字典")
    @GetMapping("/all")
    @PreAuthorize("@perm.has('dict:list')")
    public List<DictDTO> queryAllDict() {
        return dictService.queryList(new DictQueryRequest());
    }

    @Operation(summary = "查询字典")
    @GetMapping
    @PreAuthorize("@perm.has('dict:list')")
    public PageResult<DictDTO> queryDict(@Valid DictQueryRequest request) {
        return dictService.queryAll(request);
    }

    @Log("新增字典")
    @Operation(summary = "新增字典")
    @PostMapping
    @PreAuthorize("@perm.has('dict:add')")
    public void createDict(@Valid @RequestBody DictCreateRequest request) {
        dictService.create(request);
    }

    @Log("修改字典")
    @Operation(summary = "修改字典")
    @PutMapping
    @PreAuthorize("@perm.has('dict:edit')")
    public void updateDict(@Valid @RequestBody DictUpdateRequest request) {
        dictService.update(request);
    }

    @Log("删除字典")
    @Operation(summary = "删除字典")
    @DeleteMapping
    @PreAuthorize("@perm.has('dict:del')")
    public void deleteDict(@Valid @RequestBody DictDeleteRequest request) {
        dictService.delete(request);
    }
}
