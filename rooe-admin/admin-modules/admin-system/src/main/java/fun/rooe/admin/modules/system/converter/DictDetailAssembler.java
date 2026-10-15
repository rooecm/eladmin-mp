package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.DictDetailDTO;
import fun.rooe.admin.modules.system.domain.entity.DictDetailDO;
import fun.rooe.admin.modules.system.domain.request.DictDetailCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictDetailUpdateRequest;
import org.mapstruct.*;

import java.util.List;

/**
 * Maps dictionary-detail persistence models to API models and applies validated write requests.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DictDetailAssembler {
    @Mapping(target = "dict", ignore = true)
    DictDetailDTO toDto(DictDetailDO entity);

    List<DictDetailDTO> toDtos(List<DictDetailDO> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "dictId", source = "dict.id")
    @Mapping(target = "label", source = "label")
    @Mapping(target = "value", source = "value")
    @Mapping(target = "dictSort", source = "dictSort")
    DictDetailDO toEntity(DictDetailCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "dictId", source = "dict.id")
    @Mapping(target = "label", source = "label")
    @Mapping(target = "value", source = "value")
    @Mapping(target = "dictSort", source = "dictSort")
    void updateEntity(DictDetailUpdateRequest request, @MappingTarget DictDetailDO entity);
}
