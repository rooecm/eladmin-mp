package fun.rooe.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import fun.rooe.admin.framework.advice.GlobalExceptionAdvice;
import fun.rooe.admin.framework.aspect.LimitAspect;
import fun.rooe.admin.framework.config.SpringContextConfig;
import fun.rooe.admin.framework.config.security.config.SpringSecurityConfig;
import fun.rooe.admin.infrastructure.config.mybatis.MybatisPlusConfig;
import fun.rooe.admin.modules.system.domain.entity.MenuDO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.mapper.MenuMapper;
import fun.rooe.admin.modules.system.mapper.RoleMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import fun.rooe.admin.modules.system.service.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RooeAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf("integrationEnvironmentAvailable")
public class RooeAdminBootstrapApplicationTests {

    // 集成测试依赖 application-dev.yml 中配置的 Redis 与 MySQL，Redis 地址可通过环境变量覆盖；
    // 环境不可达时整类跳过，避免本地开发因缺少基础设施而构建失败
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "192.168.2.10");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    private static final String DB_HOST = "192.168.2.10";
    private static final int DB_PORT = 3306;

    static boolean integrationEnvironmentAvailable() {
        return isReachable(REDIS_HOST, REDIS_PORT) && isReachable(DB_HOST, DB_PORT);
    }

    private static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private RoleService roleService;

    @Test
    public void contextLoads() {
        assertNotNull(applicationContext.getBean(SpringContextConfig.class));
        assertNotNull(applicationContext.getBean(MybatisPlusConfig.class));
        assertNotNull(applicationContext.getBean(SpringSecurityConfig.class));
        assertNotNull(applicationContext.getBean(GlobalExceptionAdvice.class));
        assertNotNull(applicationContext.getBean(LimitAspect.class));
        assertNotNull(applicationContext.getBean(fun.rooe.admin.framework.security.service.SessionService.class));
    }

    @Test
    public void servesHomePageFromBootstrap() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Backend service started successfully", response.getBody());
    }

    @Test
    public void mapsIdentifiersRequiredToLoadUserMenus() {
        UserDO user = userMapper.selectOne(
                Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, "admin"));
        assertNotNull(user);
        assertNotNull(user.getId());

        List<RoleDO> roles = roleMapper.findByUserId(user.getId());
        assertFalse(roles.isEmpty());
        assertTrue(roles.stream().allMatch(role -> role.getId() != null));

        List<RoleDO> cachedRoles = roleService.findByUsersId(user.getId());
        assertFalse(cachedRoles.isEmpty());
        assertTrue(cachedRoles.stream().allMatch(role -> role.getId() != null));

        Set<Long> roleIds = roles.stream().map(RoleDO::getId).collect(Collectors.toSet());
        List<MenuDO> menus = List.copyOf(menuMapper.findByRoleIdsAndTypeNot(roleIds, 2));
        assertFalse(menus.isEmpty());
        assertTrue(menus.stream().allMatch(menu -> menu.getId() != null));
    }
}

