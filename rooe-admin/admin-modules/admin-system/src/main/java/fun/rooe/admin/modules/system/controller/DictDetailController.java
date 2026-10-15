package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.dto.DictDetailDto;
import fun.rooe.admin.modules.system.domain.request.DictDetailCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictDetailQueryRequest;
import fun.rooe.admin.modules.system.domain.request.DictDetailUpdateRequest;
import fun.rooe.admin.modules.system.service.DictDetailService;
import fun.rooe.admin.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP endpoints for dictionary-entry administration.
 */
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：字典详情管理")
@RequestMapping("/api/dictDetail")
public class DictDetailController {
    private final DictDetailService dictDetailService;

    @Operation(summary = "查询字典详情")
    @GetMapping
    public PageResult<DictDetailDto> queryDictDetail(@Valid DictDetailQueryRequest request) {
      return dictDetailService.queryAll(request);
    }

    @Operation(summary = "查询多个字典详情")
    @GetMapping("/map")
    public Map<String, List<DictDetailDto>> getDictDetailMaps(@RequestParam String dictName) {
      Map<String, List<DictDetailDto>> result = new LinkedHashMap<>();
      for (String name : dictName.split("[,，]"))
        result.put(name, dictDetailService.getDictByName(name));
      return result;
    }

    @Log("新增字典详情")
    @Operation(summary = "新增字典详情")
    @PostMapping
    @PreAuthorize("@el.check('dict:add')")
    public void createDictDetail(@Valid @RequestBody DictDetailCreateRequest request) {
      dictDetailService.create(request);
    }

    @Log("修改字典详情")
    @Operation(summary = "修改字典详情")
    @PutMapping
    @PreAuthorize("@el.check('dict:edit')")
    public void updateDictDetail(@Valid @RequestBody DictDetailUpdateRequest request) {
      dictDetailService.update(request);
    }

    @Log("删除字典详情")
    @Operation(summary = "删除字典详情")
    @DeleteMapping("/{id}")
    @PreAuthorize("@el.check('dict:del')")
    public void deleteDictDetail(@PathVariable @Positive Long id) {
        dictDetailService.delete(id);
    }
}
