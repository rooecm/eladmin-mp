package fun.rooe.admin.infrastructure.config.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.rooe.admin.modules.system.domain.entity.DeptDO;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MyMetaObjectHandlerTest {

    private final MyMetaObjectHandler handler = new MyMetaObjectHandler();

    @Test
    void fillsLocalDateTimeAuditFields() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DeptDO.class);

        DeptDO dept = new DeptDO();
        handler.insertFill(SystemMetaObject.forObject(dept));

        assertNotNull(dept.getCreateTime());
        assertNotNull(dept.getUpdateTime());
    }

    @Test
    void fillsTimestampAuditFields() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserDO.class);

        UserDO user = new UserDO();
        handler.insertFill(SystemMetaObject.forObject(user));

        assertNotNull(user.getCreateTime());
        assertNotNull(user.getUpdateTime());
    }
}
