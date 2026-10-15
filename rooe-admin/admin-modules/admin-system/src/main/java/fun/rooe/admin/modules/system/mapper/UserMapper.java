package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.rooe.admin.modules.system.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Set;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    User findByUsername(@Param("username") String username);

    User findByEmail(@Param("email") String email);

    User findByPhone(@Param("phone") String phone);

    void updatePass(
            @Param("username") String username,
            @Param("password") String password,
            @Param("lastPasswordResetTime") Date lastPasswordResetTime);

    void updateEmail(@Param("username") String username, @Param("email") String email);

    List<User> findByRoleId(@Param("roleId") Long roleId);

    List<User> findByRoleDeptId(@Param("deptId") Long deptId);

    List<User> findByMenuId(@Param("menuId") Long menuId);

    int countByJobs(@Param("jobIds") Set<Long> jobIds);

    int countByDepts(@Param("deptIds") Set<Long> deptIds);

    int countByRoles(@Param("roleIds") Set<Long> roleIds);

    void resetPwd(@Param("userIds") Set<Long> userIds, @Param("pwd") String pwd);
}
