package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.Dict;
import fun.rooe.admin.modules.system.domain.dto.DictCreateRequest;
import fun.rooe.admin.modules.system.domain.dto.DictDto;
import fun.rooe.admin.modules.system.domain.dto.DictUpdateRequest;
import org.mapstruct.*;

import java.util.List;

/**
 * Maps dictionary persistence models to API models and applies validated write requests.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DictAssembler {
    DictDto toDto(Dict entity);

    List<DictDto> toDtos(List<Dict> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    Dict toEntity(DictCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    void updateEntity(DictUpdateRequest request, @MappingTarget Dict entity);
}
