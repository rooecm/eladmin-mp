package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.rooe.admin.modules.system.domain.entity.DictDetailDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DictDetailMapper extends BaseMapper<DictDetailDO> {
    IPage<DictDetailDO> findAll(
            @Param("dictName") String dictName,
            @Param("label") String label,
            Page<DictDetailDO> page);

    List<DictDetailDO> findByDictName(@Param("name") String name);
}
