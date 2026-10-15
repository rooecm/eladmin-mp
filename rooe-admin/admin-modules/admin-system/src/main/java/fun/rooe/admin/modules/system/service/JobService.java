package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.dto.JobDto;
import fun.rooe.admin.modules.system.domain.entity.Job;
import fun.rooe.admin.modules.system.domain.request.JobCreateRequest;
import fun.rooe.admin.modules.system.domain.request.JobDeleteRequest;
import fun.rooe.admin.modules.system.domain.request.JobQueryRequest;
import fun.rooe.admin.modules.system.domain.request.JobUpdateRequest;
import fun.rooe.admin.response.PageResult;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public interface JobService extends IService<Job> {
    Job findById(Long id);

    PageResult<JobDto> queryAll(JobQueryRequest request);

    List<JobDto> queryList(JobQueryRequest request);

    void create(JobCreateRequest request);

    void update(JobUpdateRequest request);

    void delete(JobDeleteRequest request);

    void download(List<JobDto> jobs, HttpServletResponse response) throws IOException;
}
