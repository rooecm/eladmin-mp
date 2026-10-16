package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.converter.JobAssembler;
import fun.rooe.admin.modules.system.domain.dto.JobDTO;
import fun.rooe.admin.modules.system.domain.entity.JobDO;
import fun.rooe.admin.modules.system.domain.entity.UserJobDO;
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
public class JobServiceImpl extends ServiceImpl<JobMapper, JobDO> implements JobService {
    private final JobMapper jobMapper;
    private final RedisUtils redisUtils;
    private final UserJobMapper userJobMapper;
    private final JobAssembler assembler;

    @Override
    public PageResult<JobDTO> queryAll(JobQueryRequest request) {
        Page<JobDO> page = new Page<>(request.getPage(), request.getSize());
        jobMapper.selectPage(page, wrapper(request));
        return new PageResult<>(assembler.toDtos(page.getRecords()), page.getTotal());
    }

    @Override
    public List<JobDTO> queryList(JobQueryRequest request) {
        return assembler.toDtos(jobMapper.selectList(wrapper(request)));
    }

    private LambdaQueryWrapper<JobDO> wrapper(JobQueryRequest request) {
        LambdaQueryWrapper<JobDO> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.isNotBlank(request.getName()), JobDO::getName, request.getName())
                .eq(request.getEnabled() != null, JobDO::getEnabled, request.getEnabled())
                .orderByAsc(JobDO::getJobSort)
                .orderByDesc(JobDO::getId);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2) {
            wrapper.between(
                    JobDO::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        }
        return wrapper;
    }

    @Override
    public JobDO findById(Long id) {
        String key = CacheKey.JOB_ID + id;
        JobDO job = redisUtils.get(key, JobDO.class);
        if (job == null) {
            job = getById(id);
            redisUtils.set(key, job, 1, TimeUnit.DAYS);
        }
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(JobCreateRequest request) {
        if (jobMapper.selectOne(Wrappers.<JobDO>lambdaQuery().eq(JobDO::getName, request.getName()))
                != null) {
            throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS, "岗位名称已存在");
        }
        save(assembler.toEntity(request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(JobUpdateRequest request) {
        JobDO job = getById(request.getId());
        JobDO duplicate =
                jobMapper.selectOne(
                        Wrappers.<JobDO>lambdaQuery().eq(JobDO::getName, request.getName()));
        if (duplicate != null && !duplicate.getId().equals(request.getId())) {
            throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS, "岗位名称已存在");
        }
        assembler.updateEntity(request, job);
        updateById(job);
        delCaches(job.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(JobDeleteRequest request) {
        if (userJobMapper.selectCount(
                Wrappers.<UserJobDO>lambdaQuery().in(UserJobDO::getJobId, request.getIds())) > 0) {
            throw new BusinessException(CommonErrorCode.DATA_IN_USE, "所选岗位存在用户关联，请解除关联后再试");
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
