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
package fun.rooe.admin.modules.system.service.impl;

import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.infrastructure.domain.dto.EmailDTO;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.service.VerifyService;
import fun.rooe.admin.modules.system.utils.TemplateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;

/**
 * @author Zheng Jie
 * @date 2018-12-26
 */
@Service
@RequiredArgsConstructor
public class VerifyServiceImpl implements VerifyService {

    @Value("${code.expiration}")
    private Long expiration;
    private final RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmailDTO sendEmail(String email, String key) {
        EmailDTO emailDto;
        String content;
        String redisKey = key + email;
        // 如果不存在有效的验证码，就创建一个新的
        String oldCode =  redisUtils.get(redisKey, String.class);
        if(oldCode == null){
            String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            // 存入缓存
            if(!redisUtils.set(redisKey, code, expiration)){
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试");
            }
            content = TemplateUtils.render("email.ftl", Map.of("code", code));
            // 存在就再次发送原来的验证码
        } else {
            content = TemplateUtils.render("email.ftl", Map.of("code", oldCode));
        }
        emailDto = new EmailDTO(Collections.singletonList(email),"ROOE ADMIN后台管理系统",content);
        return emailDto;
    }

    @Override
    public void validated(String key, String code) {
        String value = redisUtils.get(key, String.class);
        if(value == null || !value.equals(code)){
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID, "验证码错误");
        } else {
            redisUtils.del(key);
        }
    }
}
