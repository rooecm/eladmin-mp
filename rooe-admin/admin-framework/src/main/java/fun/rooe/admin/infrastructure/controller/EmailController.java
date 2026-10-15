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
package fun.rooe.admin.infrastructure.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.infrastructure.domain.EmailConfigDO;
import fun.rooe.admin.infrastructure.domain.dto.EmailDTO;
import fun.rooe.admin.infrastructure.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 发送邮件
 * @author 郑杰
 * @date 2018/09/28 6:55:53
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("api/email")
@Tag(name = "工具：邮件管理")
public class EmailController {

    private final EmailService emailService;

    @GetMapping
    public EmailConfigDO queryEmailConfig() {
        return emailService.find();
    }

    @Log("配置邮件")
    @PutMapping
    @Operation(summary = "配置邮件")
    public void updateEmailConfig(@Validated @RequestBody EmailConfigDO emailConfig) throws Exception {
        emailService.config(emailConfig, emailService.find());
    }

    @Log("发送邮件")
    @PostMapping
    @Operation(summary = "发送邮件")
    public void sendEmail(@Validated @RequestBody EmailDTO emailDto){
        emailService.send(emailDto,emailService.find());
    }
}
