package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.rooe.admin.modules.system.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    List<User> findByRoleId(@Param("roleId") Long roleId);

    List<User> findByRoleDeptId(@Param("deptId") Long deptId);

    List<User> findByMenuId(@Param("menuId") Long menuId);
}
