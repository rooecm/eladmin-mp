package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.dto.DictDetailDTO;
import fun.rooe.admin.modules.system.domain.entity.DictDetailDO;
import fun.rooe.admin.modules.system.domain.request.DictDetailCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictDetailQueryRequest;
import fun.rooe.admin.modules.system.domain.request.DictDetailUpdateRequest;
import fun.rooe.admin.response.PageResult;

import java.util.List;

public interface DictDetailService extends IService<DictDetailDO> {
    PageResult<DictDetailDTO> queryAll(DictDetailQueryRequest request);

    List<DictDetailDTO> getDictByName(String name);

    void create(DictDetailCreateRequest request);

    void update(DictDetailUpdateRequest request);

    void delete(Long id);
}
