package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.security.domain.dto.AuthorityDTO;
import fun.rooe.admin.modules.system.domain.dto.RoleDTO;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDTO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.response.PageResult;

import java.util.List;
import java.util.Set;

public interface RoleService extends IService<RoleDO> {
    List<RoleDTO> queryAll();

    PageResult<RoleDTO> queryAll(RoleQueryRequest request);

    RoleDTO findDtoById(Long id);

    void create(RoleCreateRequest request);

    void update(RoleUpdateRequest request);

    void updateMenu(RoleMenuUpdateRequest request);

    void delete(RoleDeleteRequest request);

    List<RoleDO> findByUsersId(Long userId);

    Integer findByRoleIds(Set<Long> roleIds);

    void checkDataScopes(Set<Long> roleIds, Long targetDeptId);

    int getCurrentUserLevel();

    List<AuthorityDTO> buildPermissions(UserLoginDTO user);

    List<RoleDO> findByMenuId(Long menuId);
}
