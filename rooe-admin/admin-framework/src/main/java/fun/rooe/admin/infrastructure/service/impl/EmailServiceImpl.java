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
package fun.rooe.admin.infrastructure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import fun.rooe.admin.infrastructure.domain.EmailConfig;
import fun.rooe.admin.infrastructure.domain.dto.EmailDto;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.infrastructure.mapper.EmailConfigMapper;
import fun.rooe.admin.infrastructure.service.EmailService;
import fun.rooe.admin.utils.EncryptUtils;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Zheng Jie
 * @date 2018-12-26
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "email")
public class EmailServiceImpl extends ServiceImpl<EmailConfigMapper, EmailConfig> implements EmailService {

    @Override
    @CachePut(key = "'config'")
    @Transactional(rollbackFor = Exception.class)
    public EmailConfig config(EmailConfig emailConfig, EmailConfig old) throws Exception {
        emailConfig.setId(1L);
        if(!emailConfig.getPass().equals(old.getPass())){
            // 对称加密
            emailConfig.setPass(EncryptUtils.desEncrypt(emailConfig.getPass()));
        }
        saveOrUpdate(emailConfig);
        return emailConfig;
    }

    @Override
    @Cacheable(key = "'config'")
    public EmailConfig find() {
        EmailConfig emailConfig = getById(1L);
        return emailConfig == null ? new EmailConfig() : emailConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(EmailDto emailDto, EmailConfig emailConfig){
        if(emailConfig.getId() == null){
            throw new BadRequestException("请先配置，再操作");
        }
        // 封装
        String user = emailConfig.getFromUser().split("@")[0];
        Properties properties = new Properties();
        properties.put("mail.smtp.host", emailConfig.getHost());
        properties.put("mail.smtp.port", emailConfig.getPort());
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.user", user);
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.ssl.protocols", "TLSv1 TLSv1.1 TLSv1.2");
        try {
            String password = EncryptUtils.desDecrypt(emailConfig.getPass());
            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password);
                }
            });
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(emailConfig.getFromUser(), emailConfig.getUser(), StandardCharsets.UTF_8.name()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(String.join(",", emailDto.getTos()), false));
            message.setSubject(emailDto.getSubject(), StandardCharsets.UTF_8.name());
            message.setContent(emailDto.getContent(), "text/html;charset=UTF-8");
            Transport.send(message);
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
