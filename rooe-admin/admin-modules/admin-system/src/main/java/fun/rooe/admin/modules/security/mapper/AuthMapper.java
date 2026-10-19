package fun.rooe.admin.modules.security.mapper;

import fun.rooe.admin.modules.security.domain.entity.RolePermissionRow;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * 认证查询使用当前唯一主数据源，并绕过 MyBatis 查询缓存以获取数据库状态。
 */
@Mapper
public interface AuthMapper {
    @Select("SELECT * FROM sys_user WHERE LOWER(TRIM(username)) = #{username}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    UserDO findForLogin(String username);

    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    UserDO findUser(long id);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{id} ORDER BY role_id")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<Long> roleIds(long id);

    @Select({"<script>", "SELECT r.id role_id, r.role_code, r.enabled, m.permission FROM sys_role r",
            "LEFT JOIN sys_role_menu rm ON rm.role_id = r.id",
            "LEFT JOIN sys_menu m ON m.id = rm.menu_id AND m.enabled = 1",
            "WHERE r.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>", "</script>"})
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<RolePermissionRow> rolePermissions(@Param("ids") Collection<Long> ids);

    @Update("UPDATE sys_user SET auth_version = auth_version + 1 WHERE id = #{id}")
    int bumpVersion(long id);

    /**
     * 管理写操作共用的行锁，须在业务事务中持有到提交或回滚。
     */
    @Select("SELECT id FROM sys_role WHERE role_code = #{code} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long lockSuperRole(String code);

    @Select("SELECT COUNT(DISTINCT u.id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id WHERE u.enabled = 1 AND r.enabled = 1 AND r.role_code = #{code}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    long enabledSuperUsers(String code);

    @Update("UPDATE sys_user SET last_login_at = #{time}, last_login_ip = #{ip} WHERE id = #{id}")
    int recordLogin(@Param("id") long id, @Param("time") java.time.LocalDateTime time, @Param("ip") String ip);
}
