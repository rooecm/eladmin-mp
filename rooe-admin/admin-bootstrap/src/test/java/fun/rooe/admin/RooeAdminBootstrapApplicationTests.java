package fun.rooe.admin;

import fun.rooe.admin.aspect.LimitAspect;
import fun.rooe.admin.config.SpringContextConfig;
import fun.rooe.admin.config.mybatis.MybatisPlusConfig;
import fun.rooe.admin.exception.handler.GlobalExceptionHandler;
import fun.rooe.admin.config.security.config.SpringSecurityConfig;
import fun.rooe.admin.config.security.service.OnlineSessionService;
import fun.rooe.admin.modules.security.service.OnlineUserService;
import fun.rooe.admin.modules.quartz.config.JobRunner;
import fun.rooe.admin.modules.system.domain.Menu;
import fun.rooe.admin.modules.system.domain.Role;
import fun.rooe.admin.modules.system.domain.User;
import fun.rooe.admin.modules.system.mapper.MenuMapper;
import fun.rooe.admin.modules.system.mapper.RoleMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import fun.rooe.admin.modules.system.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = RooeAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.quartz.auto-startup=false")
public class RooeAdminBootstrapApplicationTests {

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

    // 启动测试验证模块组装，避免加载和调度数据库中的业务任务。
    @MockitoBean
    private JobRunner jobRunner;

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

    @Test
    public void servesHomePageFromBootstrap() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Backend service started successfully", response.getBody());
    }

    @Test
    public void mapsIdentifiersRequiredToLoadUserMenus() {
        User user = userMapper.findByUsername("admin");
        assertNotNull(user);
        assertNotNull(user.getId());

        List<Role> roles = roleMapper.findByUserId(user.getId());
        assertFalse(roles.isEmpty());
        assertTrue(roles.stream().allMatch(role -> role.getId() != null));

        List<Role> cachedRoles = roleService.findByUsersId(user.getId());
        assertFalse(cachedRoles.isEmpty());
        assertTrue(cachedRoles.stream().allMatch(role -> role.getId() != null));

        Set<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toSet());
        List<Menu> menus = List.copyOf(menuMapper.findByRoleIdsAndTypeNot(roleIds, 2));
        assertFalse(menus.isEmpty());
        assertTrue(menus.stream().allMatch(menu -> menu.getId() != null));
    }
}

