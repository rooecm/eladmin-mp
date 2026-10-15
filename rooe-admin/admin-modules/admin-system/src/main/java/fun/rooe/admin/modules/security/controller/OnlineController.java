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
package fun.rooe.admin.modules.security.controller;

import fun.rooe.admin.modules.security.dto.OnlineUserDto;
import fun.rooe.admin.modules.security.request.OnlineUserDeleteRequest;
import fun.rooe.admin.modules.security.request.OnlineUserQueryRequest;
import fun.rooe.admin.modules.security.service.OnlineUserService;
import fun.rooe.admin.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * @author Zheng Jie
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/online")
@Tag(name = "系统：在线用户管理")
public class OnlineController {

    private final OnlineUserService onlineUserService;

    @Operation(summary = "查询在线用户")
    @GetMapping
    @PreAuthorize("@el.check()")
    public PageResult<OnlineUserDto> queryOnlineUser(@Valid OnlineUserQueryRequest request) {
        return onlineUserService.getAll(request);
    }

    @Operation(summary = "导出数据")
    @GetMapping(value = "/download")
    @PreAuthorize("@el.check()")
    public void exportOnlineUser(
            HttpServletResponse response, @Valid OnlineUserQueryRequest request)
            throws IOException {
        onlineUserService.download(onlineUserService.getAll(request.getUsername()), response);
    }

    @Operation(summary = "踢出用户")
    @DeleteMapping
    @PreAuthorize("@el.check()")
    public void deleteOnlineUser(@Valid @RequestBody OnlineUserDeleteRequest request) {
        onlineUserService.logout(request);
    }
}
