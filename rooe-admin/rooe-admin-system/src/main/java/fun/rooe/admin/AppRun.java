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
package fun.rooe.admin;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import fun.rooe.admin.annotation.rest.AnonymousGetMapping;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Zheng Jie
 * @date 2018/11/15 9:20:19
 */
@Slf4j
@Hidden
@RestController
@SpringBootApplication
public class AppRun {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(AppRun.class);
        // 监控应用的PID，启动时可指定PID路径：--spring.pid.file=/home/rooe-admin/app.pid
        // 或者在 application.yml 添加文件路径，方便 kill，kill `cat /home/rooe-admin/app.pid`
        springApplication.addListeners(new ApplicationPidFileWriter());
        ConfigurableApplicationContext context = springApplication.run(args);
        String port = context.getEnvironment().getProperty("server.port");
        log.info("---------------------------------------------");
        log.info("Local: http://localhost:{}", port);
        log.info("Swagger: http://localhost:{}/doc.html", port);
        log.info("---------------------------------------------");
    }

    /**
     * 访问首页提示
     * @return /
     */
    @AnonymousGetMapping("/")
    public String index() {
        return "Backend service started successfully";
    }
}
