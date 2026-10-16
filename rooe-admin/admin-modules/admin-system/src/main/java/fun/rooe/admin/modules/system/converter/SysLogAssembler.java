package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.SysLogDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogUserDTO;
import fun.rooe.admin.modules.system.domain.entity.SysLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Maps stored logs to the fields exposed by each query endpoint.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SysLogAssembler {
    SysLogDTO toDto(SysLogDO entity);

    List<SysLogDTO> toDtos(List<SysLogDO> entities);

    SysLogUserDTO toUserDto(SysLogDO entity);

    List<SysLogUserDTO> toUserDtos(List<SysLogDO> entities);
}
