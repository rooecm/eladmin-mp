package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.dto.SysLogDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogErrorDetailDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogUserDTO;
import fun.rooe.admin.modules.system.domain.entity.SysLogDO;
import fun.rooe.admin.modules.system.domain.request.SysLogQueryRequest;
import fun.rooe.admin.response.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;

import java.io.IOException;

/**
 * Log queries, exports, recording, and cleanup.
 */
public interface SysLogService extends IService<SysLogDO> {
    /**
     * Paginated INFO logs for administrators.
     */
    PageResult<SysLogDTO> queryAll(SysLogQueryRequest request);

    /**
     * Paginated ERROR logs for administrators.
     */
    PageResult<SysLogDTO> queryErrors(SysLogQueryRequest request);

    /**
     * Paginated INFO logs belonging to the current user.
     */
    PageResult<SysLogUserDTO> queryAllByUser(SysLogQueryRequest request);

    /**
     * Records an operation captured by the logging aspect.
     */
    void save(String username, String browser, String ip, ProceedingJoinPoint joinPoint, SysLogDO sysLog);

    /**
     * Returns exception details without exposing the persistence model.
     */
    SysLogErrorDetailDTO findByErrDetail(Long id);

    /**
     * Exports matching INFO logs without pagination.
     */
    void download(SysLogQueryRequest request, HttpServletResponse response) throws IOException;

    /**
     * Exports matching ERROR logs without pagination.
     */
    void downloadErrors(SysLogQueryRequest request, HttpServletResponse response) throws IOException;

    void delAllByError();

    void delAllByInfo();
}
