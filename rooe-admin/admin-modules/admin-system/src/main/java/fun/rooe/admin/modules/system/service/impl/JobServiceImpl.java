package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.exception.EntityExistException;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.converter.JobAssembler;
import fun.rooe.admin.modules.system.domain.dto.JobDTO;
import fun.rooe.admin.modules.system.domain.entity.Job;
import fun.rooe.admin.modules.system.domain.entity.UserJob;
import fun.rooe.admin.modules.system.domain.request.JobCreateRequest;
import fun.rooe.admin.modules.system.domain.request.JobDeleteRequest;
import fun.rooe.admin.modules.system.domain.request.JobQueryRequest;
import fun.rooe.admin.modules.system.domain.request.JobUpdateRequest;
import fun.rooe.admin.modules.system.mapper.JobMapper;
import fun.rooe.admin.modules.system.mapper.UserJobMapper;
import fun.rooe.admin.modules.system.service.JobService;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.ExcelUtil;
import fun.rooe.admin.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates job persistence, usage validation, exports, and job caches.
 */
@Service
@RequiredArgsConstructor
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements JobService {
    private final JobMapper jobMapper;
    private final RedisUtils redisUtils;
    private final UserJobMapper userJobMapper;
    private final JobAssembler assembler;

    @Override
    public PageResult<JobDTO> queryAll(JobQueryRequest request) {
        Page<Job> page = new Page<>(request.getPage(), request.getSize());
        jobMapper.selectPage(page, wrapper(request));
        return new PageResult<>(assembler.toDtos(page.getRecords()), page.getTotal());
    }

    @Override
    public List<JobDTO> queryList(JobQueryRequest request) {
        return assembler.toDtos(jobMapper.selectList(wrapper(request)));
    }

    private LambdaQueryWrapper<Job> wrapper(JobQueryRequest request) {
        LambdaQueryWrapper<Job> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.isNotBlank(request.getName()), Job::getName, request.getName())
                .eq(request.getEnabled() != null, Job::getEnabled, request.getEnabled())
                .orderByAsc(Job::getJobSort)
                .orderByDesc(Job::getId);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2) {
            wrapper.between(
                    Job::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        }
        return wrapper;
    }

    @Override
    public Job findById(Long id) {
        String key = CacheKey.JOB_ID + id;
        Job job = redisUtils.get(key, Job.class);
        if (job == null) {
            job = getById(id);
            redisUtils.set(key, job, 1, TimeUnit.DAYS);
        }
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(JobCreateRequest request) {
        if (jobMapper.selectOne(Wrappers.<Job>lambdaQuery().eq(Job::getName, request.getName()))
                != null) {
            throw new EntityExistException(Job.class, "name", request.getName());
        }
        save(assembler.toEntity(request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(JobUpdateRequest request) {
        Job job = getById(request.getId());
        Job duplicate =
                jobMapper.selectOne(
                        Wrappers.<Job>lambdaQuery().eq(Job::getName, request.getName()));
        if (duplicate != null && !duplicate.getId().equals(request.getId())) {
            throw new EntityExistException(Job.class, "name", request.getName());
        }
        assembler.updateEntity(request, job);
        updateById(job);
        delCaches(job.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(JobDeleteRequest request) {
        if (userJobMapper.selectCount(
                Wrappers.<UserJob>lambdaQuery().in(UserJob::getJobId, request.getIds())) > 0) {
            throw new BadRequestException("所选岗位存在用户关联，请解除关联后再试");
        }
        removeBatchByIds(request.getIds());
        request.getIds().forEach(this::delCaches);
    }

    @Override
    public void download(List<JobDTO> jobs, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JobDTO job : jobs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("岗位名称", job.getName());
            row.put("岗位状态", Boolean.TRUE.equals(job.getEnabled()) ? "启用" : "停用");
            row.put("创建日期", job.getCreateTime());
            rows.add(row);
        }
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=file.xlsx");
        ExcelUtil.write(rows, response.getOutputStream());
    }

    private void delCaches(Long id) {
        redisUtils.del(CacheKey.JOB_ID + id);
    }
}
