package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.converter.DictAssembler;
import fun.rooe.admin.modules.system.converter.DictDetailAssembler;
import fun.rooe.admin.modules.system.domain.dto.DictDetailDTO;
import fun.rooe.admin.modules.system.domain.entity.Dict;
import fun.rooe.admin.modules.system.domain.entity.DictDetail;
import fun.rooe.admin.modules.system.domain.request.DictDetailCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictDetailQueryRequest;
import fun.rooe.admin.modules.system.domain.request.DictDetailUpdateRequest;
import fun.rooe.admin.modules.system.mapper.DictDetailMapper;
import fun.rooe.admin.modules.system.mapper.DictMapper;
import fun.rooe.admin.modules.system.service.DictDetailService;
import fun.rooe.admin.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates dictionary-entry persistence and invalidates affected dictionary caches.
 */
@Service
@RequiredArgsConstructor
public class DictDetailServiceImpl extends ServiceImpl<DictDetailMapper, DictDetail>
        implements DictDetailService {
    private final DictMapper dictMapper;
    private final DictDetailMapper dictDetailMapper;
    private final RedisUtils redisUtils;
  private final DictDetailAssembler assembler;
  private final DictAssembler dictAssembler;

    @Override
    public PageResult<DictDetailDTO> queryAll(DictDetailQueryRequest request) {
      IPage<DictDetail> page =
              dictDetailMapper.findAll(
                      request.getDictName(),
                      request.getLabel(),
                      new Page<>(request.getPage(), request.getSize()));
      return new PageResult<>(assemble(page.getRecords()), page.getTotal());
    }

  @Override
  public List<DictDetailDTO> getDictByName(String name) {
    String key = CacheKey.DICT_NAME + name;
    List<DictDetailDTO> cached = redisUtils.getList(key, DictDetailDTO.class);
    if (cached == null || cached.isEmpty()) {
      cached = assemble(dictDetailMapper.findByDictName(name));
      redisUtils.set(key, cached, 1, TimeUnit.DAYS);
    }
    return cached;
  }

  private List<DictDetailDTO> assemble(List<DictDetail> details) {
    if (details.isEmpty()) return new ArrayList<>();
    Set<Long> ids = details.stream().map(DictDetail::getDictId).collect(Collectors.toSet());
    Map<Long, Dict> dicts =
            dictMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Dict::getId, Function.identity()));
    List<DictDetailDTO> result = assembler.toDtos(details);
    result.forEach(
            detail -> detail.setDict(dictAssembler.toDto(dicts.get(detail.getDictId()))));
    return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DictDetailCreateRequest request) {
      DictDetail detail = assembler.toEntity(request);
      save(detail);
      delCaches(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DictDetailUpdateRequest request) {
      DictDetail detail = getById(request.getId());
      delCaches(detail);
      assembler.updateEntity(request, detail);
      updateById(detail);
      delCaches(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
      DictDetail detail = getById(id);
        removeById(id);
      delCaches(detail);
    }

  private void delCaches(DictDetail detail) {
    if (detail == null) return;
    Dict dict = dictMapper.selectById(detail.getDictId());
    if (dict != null) redisUtils.del(CacheKey.DICT_NAME + dict.getName());
    }
}
