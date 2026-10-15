package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.rooe.admin.modules.system.domain.entity.DictDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DictDetailMapper extends BaseMapper<DictDetail> {
    IPage<DictDetail> findAll(
            @Param("dictName") String dictName,
            @Param("label") String label,
            Page<DictDetail> page);

    List<DictDetail> findByDictName(@Param("name") String name);
}
