package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.RoleDTO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.domain.request.RoleCreateRequest;
import fun.rooe.admin.modules.system.domain.request.RoleUpdateRequest;
import org.mapstruct.*;

import java.util.List;

/**
 * Maps role persistence models to API models and applies validated write requests.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoleAssembler {
    @Mapping(target = "menus", ignore = true)
    @Mapping(target = "depts", ignore = true)
    RoleDTO toDto(RoleDO entity);

    List<RoleDTO> toDtos(List<RoleDO> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "level", source = "level")
    @Mapping(target = "dataScope", source = "dataScope")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "enabled", source = "enabled")
    RoleDO toEntity(RoleCreateRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(RoleUpdateRequest request, @MappingTarget RoleDO entity);
}
