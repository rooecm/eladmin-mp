package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.rooe.admin.modules.system.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    List<Role> findByUserId(@Param("userId") Long userId);

    int countByDepts(@Param("deptIds") Set<Long> deptIds);

    List<Role> findByMenuId(@Param("menuId") Long menuId);
}
