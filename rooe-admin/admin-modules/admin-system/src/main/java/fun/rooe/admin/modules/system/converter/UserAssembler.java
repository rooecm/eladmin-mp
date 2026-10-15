package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.dto.UserDto;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDto;
import fun.rooe.admin.modules.system.domain.entity.User;
import fun.rooe.admin.modules.system.domain.request.UserCenterUpdateRequest;
import fun.rooe.admin.modules.system.domain.request.UserCreateRequest;
import fun.rooe.admin.modules.system.domain.request.UserUpdateRequest;
import org.mapstruct.*;

import java.util.List;

/**
 * Maps users between persistence, request, response, and authentication models.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserAssembler {
    @Mapping(target = "dept", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "jobs", ignore = true)
    UserDto toDto(User entity);

    List<UserDto> toDtos(List<User> entities);

    @Mapping(target = "dept", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "jobs", ignore = true)
    UserLoginDto toLoginDto(User entity);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deptId", source = "dept.id")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "nickName", source = "nickName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "enabled", source = "enabled")
    User toEntity(UserCreateRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(UserUpdateRequest request, @MappingTarget User entity);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "nickName", source = "nickName")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "gender", source = "gender")
    void updateCenter(UserCenterUpdateRequest request, @MappingTarget User entity);
}
