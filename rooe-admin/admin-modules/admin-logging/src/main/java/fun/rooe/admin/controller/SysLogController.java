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
package fun.rooe.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.rooe.admin.domain.SysLogDO;
import fun.rooe.admin.domain.dto.SysLogQueryCriteria;
import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
    @PreAuthorize("@el.check()")
    public void exportLog(HttpServletResponse response, SysLogQueryCriteria criteria) throws IOException {
        criteria.setLogType("INFO");
        sysLogService.download(sysLogService.queryAll(criteria), response);
    }

    @Log("导出错误数据")
    @Operation(summary = "导出错误数据")
    @GetMapping(value = "/error/download")
    @PreAuthorize("@el.check()")
    public void exportErrorLog(HttpServletResponse response, SysLogQueryCriteria criteria) throws IOException {
        criteria.setLogType("ERROR");
        sysLogService.download(sysLogService.queryAll(criteria), response);
    }

    @GetMapping
    @Operation(summary = "日志查询")
    @PreAuthorize("@el.check()")
    public PageResult<SysLogDO> queryLog(SysLogQueryCriteria criteria) {
        criteria.setLogType("INFO");
        Page<SysLogDO> page = new Page<>(criteria.getPage(), criteria.getSize());
        return sysLogService.queryAll(criteria,page);
    }

    @GetMapping(value = "/user")
    @Operation(summary = "用户日志查询")
    public PageResult<SysLogDO> queryUserLog(SysLogQueryCriteria criteria) {
        criteria.setLogType("INFO");
        criteria.setUsername(SecurityUtils.getCurrentUsername());
        Page<SysLogDO> page = new Page<>(criteria.getPage(), criteria.getSize());
        return sysLogService.queryAllByUser(criteria,page);
    }

    @GetMapping(value = "/error")
    @Operation(summary = "错误日志查询")
    @PreAuthorize("@el.check()")
    public PageResult<SysLogDO> queryErrorLog(SysLogQueryCriteria criteria) {
        criteria.setLogType("ERROR");
        Page<SysLogDO> page = new Page<>(criteria.getPage(), criteria.getSize());
        return sysLogService.queryAll(criteria,page);
    }

    @GetMapping(value = "/error/{id}")
    @Operation(summary = "日志异常详情查询")
    @PreAuthorize("@el.check()")
    public Object queryErrorLogDetail(@PathVariable Long id){
        return sysLogService.findByErrDetail(id);
    }
    @DeleteMapping(value = "/del/error")
    @Log("删除所有ERROR日志")
    @Operation(summary = "删除所有ERROR日志")
    @PreAuthorize("@el.check()")
    public void delAllErrorLog(){
        sysLogService.delAllByError();
    }

    @DeleteMapping(value = "/del/info")
    @Log("删除所有INFO日志")
    @Operation(summary = "删除所有INFO日志")
    @PreAuthorize("@el.check()")
    public void delAllInfoLog(){
        sysLogService.delAllByInfo();
    }
}
