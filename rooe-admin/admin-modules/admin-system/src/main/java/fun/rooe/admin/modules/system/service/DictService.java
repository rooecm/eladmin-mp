package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.dto.DictDTO;
import fun.rooe.admin.modules.system.domain.entity.Dict;
import fun.rooe.admin.modules.system.domain.request.DictCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DictDeleteRequest;
import fun.rooe.admin.modules.system.domain.request.DictQueryRequest;
import fun.rooe.admin.modules.system.domain.request.DictUpdateRequest;
import fun.rooe.admin.response.PageResult;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public interface DictService extends IService<Dict> {
    PageResult<DictDTO> queryAll(DictQueryRequest request);

    List<DictDTO> queryList(DictQueryRequest request);

    void create(DictCreateRequest request);

    void update(DictUpdateRequest request);

    void delete(DictDeleteRequest request);

    void download(List<DictDTO> dicts, HttpServletResponse response) throws IOException;
}
