package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.converter.DictAssembler;
import fun.rooe.admin.modules.system.domain.dto.DictDTO;
import fun.rooe.admin.modules.system.domain.entity.DictDO;
import fun.rooe.admin.modules.system.domain.entity.DictDetailDO;
import fun.rooe.admin.modules.system.domain.request.DictCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictDeleteRequest;
import fun.rooe.admin.modules.system.domain.request.DictQueryRequest;
import fun.rooe.admin.modules.system.domain.request.DictUpdateRequest;
import fun.rooe.admin.modules.system.mapper.DictDetailMapper;
import fun.rooe.admin.modules.system.mapper.DictMapper;
import fun.rooe.admin.modules.system.service.DictService;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.ExcelUtil;
import fun.rooe.admin.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

/**
 * Coordinates dictionary persistence, dependent entries, exports, and dictionary caches.
 */
@Service
@RequiredArgsConstructor
public class DictServiceImpl extends ServiceImpl<DictMapper, DictDO> implements DictService {
    private final DictMapper dictMapper;
    private final DictDetailMapper dictDetailMapper;
    private final RedisUtils redisUtils;
    private final DictAssembler assembler;

    @Override
    public PageResult<DictDTO> queryAll(DictQueryRequest request) {
        Page<DictDO> page = new Page<>(request.getPage(), request.getSize());
        dictMapper.selectPage(page, wrapper(request));
        return new PageResult<>(assembler.toDtos(page.getRecords()), page.getTotal());
    }

    @Override
    public List<DictDTO> queryList(DictQueryRequest request) {
        return assembler.toDtos(dictMapper.selectList(wrapper(request)));
    }

    private LambdaQueryWrapper<DictDO> wrapper(DictQueryRequest request) {
        LambdaQueryWrapper<DictDO> wrapper = Wrappers.lambdaQuery();
        wrapper.and(
                        StringUtils.isNotBlank(request.getBlurry()),
                        query ->
                                query.like(DictDO::getName, request.getBlurry())
                                        .or()
                                        .like(DictDO::getDescription, request.getBlurry()))
                .orderByDesc(DictDO::getId);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2) {
            wrapper.between(
                    DictDO::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DictCreateRequest request) {
        save(assembler.toEntity(request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DictUpdateRequest request) {
        DictDO dict = getById(request.getId());
        delCaches(dict);
        assembler.updateEntity(request, dict);
        updateById(dict);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(DictDeleteRequest request) {
        List<DictDO> dicts = dictMapper.selectBatchIds(request.getIds());
        dicts.forEach(this::delCaches);
        dictMapper.deleteBatchIds(request.getIds());
        dictDetailMapper.delete(
                Wrappers.<DictDetailDO>lambdaQuery()
                        .in(DictDetailDO::getDictId, request.getIds()));
    }

    @Override
    public void download(List<DictDTO> dicts, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DictDTO dict : dicts) {
            List<DictDetailDO> details = dictDetailMapper.findByDictName(dict.getName());
            if (details.isEmpty()) details = Collections.singletonList(null);
            for (DictDetailDO detail : details) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("字典名称", dict.getName());
                row.put("字典描述", dict.getDescription());
                row.put("字典标签", detail == null ? null : detail.getLabel());
                row.put("字典值", detail == null ? null : detail.getValue());
                row.put("创建日期", detail == null ? dict.getCreateTime() : detail.getCreateTime());
                rows.add(row);
            }
        }
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=file.xlsx");
        ExcelUtil.write(rows, response.getOutputStream());
    }

    private void delCaches(DictDO dict) {
        if (dict != null) redisUtils.del(CacheKey.DICT_NAME + dict.getName());
    }
}
