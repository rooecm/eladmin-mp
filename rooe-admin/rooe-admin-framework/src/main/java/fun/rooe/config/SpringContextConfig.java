package fun.rooe.config;

import fun.rooe.utils.SpringBeanHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册通用 Spring 上下文访问工具。
 */
@Configuration
public class SpringContextConfig {

    @Bean
    public SpringBeanHolder springContextHolder() {
        return new SpringBeanHolder();
    }
}
