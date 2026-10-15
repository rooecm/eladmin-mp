package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.dto.*;
import fun.rooe.admin.modules.system.service.JobService;
import fun.rooe.admin.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

/**
 * HTTP endpoints for job administration and exports.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "系统：岗位管理")
@RequestMapping("/api/job")
public class JobController {
    private final JobService jobService;

    @Operation(summary = "导出岗位数据")
    @GetMapping("/download")
    @PreAuthorize("@el.check('job:list')")
    public void exportJob(HttpServletResponse response, @Valid JobQueryRequest request)
            throws IOException {
      jobService.download(jobService.queryList(request), response);
    }

    @Operation(summary = "查询岗位")
    @GetMapping
    @PreAuthorize("@el.check('job:list','user:list')")
    public PageResult<JobDto> queryJob(@Valid JobQueryRequest request) {
      return jobService.queryAll(request);
    }

    @Log("新增岗位")
    @Operation(summary = "新增岗位")
    @PostMapping
    @PreAuthorize("@el.check('job:add')")
    public void createJob(@Valid @RequestBody JobCreateRequest request) {
      jobService.create(request);
    }

    @Log("修改岗位")
    @Operation(summary = "修改岗位")
    @PutMapping
    @PreAuthorize("@el.check('job:edit')")
    public void updateJob(@Valid @RequestBody JobUpdateRequest request) {
      jobService.update(request);
    }

    @Log("删除岗位")
    @Operation(summary = "删除岗位")
    @DeleteMapping
    @PreAuthorize("@el.check('job:del')")
    public void deleteJob(@Valid @RequestBody JobDeleteRequest request) {
      jobService.delete(request);
    }
}
