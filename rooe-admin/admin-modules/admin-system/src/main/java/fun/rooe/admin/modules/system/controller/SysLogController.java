package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.modules.system.domain.dto.SysLogDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogErrorDetailDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogUserDTO;
import fun.rooe.admin.modules.system.domain.request.SysLogQueryRequest;
import fun.rooe.admin.modules.system.service.SysLogService;
import fun.rooe.admin.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * @author Zheng Jie
 * @date 2018-11-24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
@Tag(name = "系统：日志管理")
public class SysLogController {

    private final SysLogService sysLogService;

    @Log("导出数据")
    @Operation(summary = "导出数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@perm.has('logs:list')")
    public void exportLog(HttpServletResponse response, @Valid SysLogQueryRequest request) throws IOException {
        sysLogService.download(request, response);
    }

    @Log("导出错误数据")
    @Operation(summary = "导出错误数据")
    @GetMapping(value = "/error/download")
    @PreAuthorize("@perm.has('logs:list')")
    public void exportErrorLog(HttpServletResponse response, @Valid SysLogQueryRequest request) throws IOException {
        sysLogService.downloadErrors(request, response);
    }

    @GetMapping
    @Operation(summary = "日志查询")
    @PreAuthorize("@perm.has('logs:list')")
    public PageResult<SysLogDTO> queryLog(@Valid SysLogQueryRequest request) {
        return sysLogService.queryAll(request);
    }

    @GetMapping(value = "/user")
    @Operation(summary = "用户日志查询")
    public PageResult<SysLogUserDTO> queryUserLog(@Valid SysLogQueryRequest request) {
        return sysLogService.queryAllByUser(request);
    }

    @GetMapping(value = "/error")
    @Operation(summary = "错误日志查询")
    @PreAuthorize("@perm.has('logs:list')")
    public PageResult<SysLogDTO> queryErrorLog(@Valid SysLogQueryRequest request) {
        return sysLogService.queryErrors(request);
    }

    @GetMapping(value = "/error/{id}")
    @Operation(summary = "日志异常详情查询")
    @PreAuthorize("@perm.has('logs:list')")
    public SysLogErrorDetailDTO queryErrorLogDetail(@PathVariable @Positive Long id) {
        return sysLogService.findByErrDetail(id);
    }

    @DeleteMapping(value = "/del/error")
    @Log("删除所有ERROR日志")
    @Operation(summary = "删除所有ERROR日志")
    @PreAuthorize("@perm.has('logs:del')")
    public void delAllErrorLog() {
        sysLogService.delAllByError();
    }

    @DeleteMapping(value = "/del/info")
    @Log("删除所有INFO日志")
    @Operation(summary = "删除所有INFO日志")
    @PreAuthorize("@perm.has('logs:del')")
    public void delAllInfoLog() {
        sysLogService.delAllByInfo();
    }
}
