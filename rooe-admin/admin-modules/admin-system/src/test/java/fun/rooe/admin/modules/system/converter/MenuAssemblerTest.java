package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.MenuDto;
import fun.rooe.admin.modules.system.domain.dto.MenuRouterDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MenuAssemblerTest {
    private final MenuAssembler assembler = Mappers.getMapper(MenuAssembler.class);

    @Test
    void convertsRootLeafToLayoutRouteWithoutChangingFrontendContract() {
        MenuDto menu = new MenuDto();
        menu.setId(1L);
        menu.setTitle("控制台");
        menu.setComponentName("Dashboard");
        menu.setComponent("dashboard/index");
        menu.setPath("dashboard");
        menu.setIcon("dashboard");
        menu.setCache(true);
        menu.setHidden(false);
        menu.setIFrame(false);

        MenuRouterDto root = assembler.toRouterDtos(List.of(menu)).get(0);
        MenuRouterDto child = root.getChildren().get(0);

        assertEquals("/dashboard", root.getPath());
        assertEquals("Layout", root.getComponent());
        assertNull(root.getName());
        assertNull(root.getMeta());
        assertEquals("Dashboard", child.getName());
        assertEquals("index", child.getPath());
        assertEquals("dashboard/index", child.getComponent());
        assertEquals("控制台", child.getMeta().getTitle());
        assertEquals("dashboard", child.getMeta().getIcon());
        assertFalse(child.getMeta().getNoCache());
    }
}
