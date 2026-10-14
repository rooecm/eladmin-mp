package fun.rooe.admin.framework.config;

import fun.rooe.admin.utils.StringUtils;
import fun.rooe.admin.framework.utils.SpringBeanHolder;
import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 启动时向 common 注入 IP 归属地查询实现, 避免 common 依赖 Spring 容器
     */
    @Autowired
    public void initAddressFinder(Ip2regionSearcher searcher) {
        StringUtils.setAddressFinder(searcher);
    }
}
