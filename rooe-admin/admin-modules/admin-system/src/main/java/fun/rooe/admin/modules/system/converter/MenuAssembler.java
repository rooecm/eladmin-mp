package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.MenuDto;
import fun.rooe.admin.modules.system.domain.dto.MenuRouterDto;
import fun.rooe.admin.modules.system.domain.dto.MenuRouterMetaDto;
import fun.rooe.admin.modules.system.domain.entity.Menu;
import fun.rooe.admin.modules.system.domain.request.MenuCreateRequest;
import fun.rooe.admin.modules.system.domain.request.MenuUpdateRequest;
import fun.rooe.admin.utils.StringUtils;
import org.mapstruct.*;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * Maps menu persistence models to API models and assembles menu trees for responses.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MenuAssembler {
    @Mapping(target = "children", ignore = true)
    MenuDto toDto(Menu entity);

    List<MenuDto> toDtos(List<Menu> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "pid", source = "pid")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "componentName", source = "componentName")
    @Mapping(target = "component", source = "component")
    @Mapping(target = "menuSort", source = "menuSort")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "IFrame", source = "IFrame")
    @Mapping(target = "cache", source = "cache")
    @Mapping(target = "hidden", source = "hidden")
    @Mapping(target = "permission", source = "permission")
    Menu toEntity(MenuCreateRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(MenuUpdateRequest request, @MappingTarget Menu entity);

    /** Builds a forest from a flat menu list while preserving the source query order. */
    default List<MenuDto> toTree(List<MenuDto> menus) {
        if (menus == null || menus.isEmpty()) return new ArrayList<>();
        Map<Long, MenuDto> byId = new LinkedHashMap<>();
        menus.forEach(menu -> byId.putIfAbsent(menu.getId(), menu));
        List<MenuDto> roots = new ArrayList<>();
        Set<Long> childIds = new HashSet<>();
        for (MenuDto menu : byId.values()) {
            if (menu.getPid() == null) roots.add(menu);
            MenuDto parent = byId.get(menu.getPid());
            if (parent != null) {
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(menu);
                childIds.add(menu.getId());
            }
        }
        if (!roots.isEmpty()) return roots;
        return byId.values().stream().filter(menu -> !childIds.contains(menu.getId())).toList();
    }

    /**
     * Converts the administrative menu tree into the route contract consumed by the frontend.
     */
    default List<MenuRouterDto> toRouterDtos(List<MenuDto> menus) {
        List<MenuRouterDto> result = new LinkedList<>();
        for (MenuDto menu : menus) {
            MenuRouterDto item = new MenuRouterDto();
            item.setName(
                    StringUtils.isNotBlank(menu.getComponentName())
                            ? menu.getComponentName()
                            : menu.getTitle());
            item.setPath(menu.getPid() == null ? "/" + menu.getPath() : menu.getPath());
            item.setHidden(menu.getHidden());
            if (!Boolean.TRUE.equals(menu.getIFrame())) {
                if (menu.getPid() == null) {
                    item.setComponent(
                            StringUtils.isEmpty(menu.getComponent())
                                    ? "Layout"
                                    : menu.getComponent());
                } else if (StringUtils.isNoneBlank(menu.getComponent())) {
                    item.setComponent(menu.getComponent());
                }
            }
            item.setMeta(
                    new MenuRouterMetaDto(
                            menu.getTitle(),
                            menu.getIcon(),
                            !Boolean.TRUE.equals(menu.getCache())));
            if (!CollectionUtils.isEmpty(menu.getChildren())) {
                item.setAlwaysShow(true);
                item.setRedirect("noredirect");
                item.setChildren(toRouterDtos(menu.getChildren()));
            } else if (menu.getPid() == null) {
                MenuRouterDto child = new MenuRouterDto();
                child.setMeta(item.getMeta());
                if (!Boolean.TRUE.equals(menu.getIFrame())) {
                    child.setPath("index");
                    child.setName(item.getName());
                    child.setComponent(item.getComponent());
                } else {
                    child.setPath(menu.getPath());
                }
                item.setName(null);
                item.setMeta(null);
                item.setComponent("Layout");
                item.setChildren(List.of(child));
            }
            result.add(item);
        }
        return result;
    }
}
