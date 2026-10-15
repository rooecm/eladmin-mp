package fun.rooe.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.rooe.admin.modules.system.domain.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

@Mapper
public interface JobMapper extends BaseMapper<Job> {
    @Select(
            "select j.* from sys_job j join sys_user_job uj on uj.job_id = j.id where uj.user_id"
                    + " = #{userId}")
    Set<Job> findByUserId(@Param("userId") Long userId);
}
