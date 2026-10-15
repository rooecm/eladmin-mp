package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.Dict;
import fun.rooe.admin.modules.system.domain.dto.*;
import fun.rooe.admin.response.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface DictService extends IService<Dict> {
    PageResult<DictDto> queryAll(DictQueryRequest request);

    List<DictDto> queryList(DictQueryRequest request);

    void create(DictCreateRequest request);

    void update(DictUpdateRequest request);

    void delete(DictDeleteRequest request);

    void download(List<DictDto> dicts, HttpServletResponse response) throws IOException;
}
