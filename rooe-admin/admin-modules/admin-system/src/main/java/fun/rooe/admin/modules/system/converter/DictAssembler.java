package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.DictDTO;
import fun.rooe.admin.modules.system.domain.entity.DictDO;
import fun.rooe.admin.modules.system.domain.request.DictCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictUpdateRequest;
import org.mapstruct.*;

import java.util.List;

/**
 * Maps dictionary persistence models to API models and applies validated write requests.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DictAssembler {
    DictDTO toDto(DictDO entity);

    List<DictDTO> toDtos(List<DictDO> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    DictDO toEntity(DictCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    void updateEntity(DictUpdateRequest request, @MappingTarget DictDO entity);
}
