package fun.rooe;

import fun.rooe.aspect.LimitAspect;
import fun.rooe.config.SpringContextConfig;
import fun.rooe.config.mybatis.MybatisPlusConfig;
import fun.rooe.exception.handler.GlobalExceptionHandler;
import fun.rooe.modules.security.config.SpringSecurityConfig;
import fun.rooe.modules.security.service.OnlineSessionService;
import fun.rooe.modules.security.service.OnlineUserService;
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

