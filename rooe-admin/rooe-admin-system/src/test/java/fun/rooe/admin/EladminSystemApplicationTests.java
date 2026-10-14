package fun.rooe.admin;

import fun.rooe.admin.aspect.LimitAspect;
import fun.rooe.admin.config.SpringContextConfig;
import fun.rooe.admin.config.mybatis.MybatisPlusConfig;
import fun.rooe.admin.exception.handler.GlobalExceptionHandler;
import fun.rooe.admin.config.security.config.SpringSecurityConfig;
import fun.rooe.admin.config.security.service.OnlineSessionService;
import fun.rooe.admin.modules.security.service.OnlineUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EladminSystemApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void contextLoads() {
        assertNotNull(applicationContext.getBean(SpringContextConfig.class));
        assertNotNull(applicationContext.getBean(MybatisPlusConfig.class));
        assertNotNull(applicationContext.getBean(SpringSecurityConfig.class));
        assertNotNull(applicationContext.getBean(GlobalExceptionHandler.class));
        assertNotNull(applicationContext.getBean(LimitAspect.class));
        assertSame(applicationContext.getBean(OnlineUserService.class),
                applicationContext.getBean(OnlineSessionService.class));
    }

    public static void main(String[] args) {
    }
}

