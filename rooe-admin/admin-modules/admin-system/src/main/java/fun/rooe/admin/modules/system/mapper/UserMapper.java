package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    List<UserDO> findByRoleId(@Param("roleId") Long roleId);

    List<UserDO> findByRoleDeptId(@Param("deptId") Long deptId);

    List<UserDO> findByMenuId(@Param("menuId") Long menuId);
}
