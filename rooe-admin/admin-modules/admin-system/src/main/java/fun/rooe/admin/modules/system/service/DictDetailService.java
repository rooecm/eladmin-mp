package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.DictDetail;
import fun.rooe.admin.modules.system.domain.dto.*;
import fun.rooe.admin.response.PageResult;
import java.util.List;

public interface DictDetailService extends IService<DictDetail> {
    PageResult<DictDetailDto> queryAll(DictDetailQueryRequest request);

    List<DictDetailDto> getDictByName(String name);

    void create(DictDetailCreateRequest request);

    void update(DictDetailUpdateRequest request);

    void delete(Long id);
}
