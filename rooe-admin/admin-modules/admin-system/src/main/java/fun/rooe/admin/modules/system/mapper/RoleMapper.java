package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.rooe.admin.modules.system.domain.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    List<Role> findByUserId(@Param("userId") Long userId);

    List<Role> findByMenuId(@Param("menuId") Long menuId);
}
