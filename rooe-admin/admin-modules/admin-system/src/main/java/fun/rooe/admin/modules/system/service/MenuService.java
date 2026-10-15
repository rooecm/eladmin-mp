package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.Menu;
import fun.rooe.admin.modules.system.domain.dto.*;

import java.util.List;
import java.util.Set;

public interface MenuService extends IService<Menu> {
    List<MenuVo> buildCurrentUserMenus();

    List<MenuDto> queryAll(MenuQueryRequest request);

    List<MenuDto> getMenus(Long pid);

    Set<Long> getChildIds(Long id);

    List<MenuDto> getSuperior(MenuSuperiorRequest request);

    void create(MenuCreateRequest request);

    void update(MenuUpdateRequest request);

    void delete(MenuDeleteRequest request);
}
