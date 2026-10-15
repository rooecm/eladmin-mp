package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.security.dto.AuthorityDto;
import fun.rooe.admin.modules.system.domain.Role;
import fun.rooe.admin.modules.system.domain.dto.*;
import fun.rooe.admin.response.PageResult;

import java.util.List;
import java.util.Set;

public interface RoleService extends IService<Role> {
    List<RoleDto> queryAll();

    PageResult<RoleDto> queryAll(RoleQueryRequest request);

    RoleDto findDtoById(Long id);

    void create(RoleCreateRequest request);

    void update(RoleUpdateRequest request);

    void updateMenu(RoleMenuUpdateRequest request);

    void delete(RoleDeleteRequest request);

    List<Role> findByUsersId(Long userId);

    Integer findByRoleIds(Set<Long> roleIds);

    int getCurrentUserLevel();

    List<AuthorityDto> buildPermissions(UserLoginDto user);

    List<Role> findByMenuId(Long menuId);
}
