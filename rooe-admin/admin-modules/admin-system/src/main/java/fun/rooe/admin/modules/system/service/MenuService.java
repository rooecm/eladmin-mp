package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.dto.MenuDTO;
import fun.rooe.admin.modules.system.domain.dto.MenuRouterDTO;
import fun.rooe.admin.modules.system.domain.entity.Menu;
import fun.rooe.admin.modules.system.domain.request.*;

import java.util.List;
import java.util.Set;

public interface MenuService extends IService<Menu> {
    List<MenuRouterDTO> buildCurrentUserMenus();

    List<MenuDTO> queryAll(MenuQueryRequest request);

    List<MenuDTO> getMenus(Long pid);

    Set<Long> getChildIds(Long id);

    List<MenuDTO> getSuperior(MenuSuperiorRequest request);

    void create(MenuCreateRequest request);

    void update(MenuUpdateRequest request);

    void delete(MenuDeleteRequest request);
}
