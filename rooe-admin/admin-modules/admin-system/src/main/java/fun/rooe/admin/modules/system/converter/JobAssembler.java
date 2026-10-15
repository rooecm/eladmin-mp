package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.JobDTO;
import fun.rooe.admin.modules.system.domain.entity.JobDO;
import fun.rooe.admin.modules.system.domain.request.JobCreateRequest;
import fun.rooe.admin.modules.system.domain.request.JobUpdateRequest;
import org.mapstruct.*;

import java.util.List;

/**
 * Maps job persistence models to API models and applies validated write requests.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface JobAssembler {
    JobDTO toDto(JobDO entity);

    List<JobDTO> toDtos(List<JobDO> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "jobSort", source = "jobSort")
    @Mapping(target = "enabled", source = "enabled")
    JobDO toEntity(JobCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "jobSort", source = "jobSort")
    @Mapping(target = "enabled", source = "enabled")
    void updateEntity(JobUpdateRequest request, @MappingTarget JobDO entity);
}
