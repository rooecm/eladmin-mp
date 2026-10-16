package fun.rooe.admin.modules.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.modules.system.converter.SysLogAssembler;
import fun.rooe.admin.modules.system.domain.dto.SysLogDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogErrorDetailDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogUserDTO;
import fun.rooe.admin.modules.system.domain.entity.SysLogDO;
import fun.rooe.admin.modules.system.domain.request.SysLogQueryRequest;
import fun.rooe.admin.modules.system.mapper.SysLogMapper;
import fun.rooe.admin.modules.system.service.SysLogService;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.ExcelUtil;
import fun.rooe.admin.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * @author Zheng Jie
 * @date 2018-11-24
 */
@Service
@RequiredArgsConstructor
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLogDO> implements SysLogService {

    private final SysLogMapper sysLogMapper;
    private final SysLogAssembler assembler;
    // 定义敏感字段常量数组
    private static final String[] SENSITIVE_KEYS = {"password"};

    @Override
    public PageResult<SysLogDTO> queryAll(SysLogQueryRequest request) {
        return queryPage(request, "INFO");
    }

    @Override
    public PageResult<SysLogDTO> queryErrors(SysLogQueryRequest request) {
        return queryPage(request, "ERROR");
    }

    private PageResult<SysLogDTO> queryPage(SysLogQueryRequest request, String logType) {
        Page<SysLogDO> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<SysLogDO> wrapper = adminWrapper(request, logType);
        // Exception details are fetched separately; ERROR lists historically omit elapsed time.
        wrapper.select(SysLogDO.class, field -> !"exceptionDetail".equals(field.getProperty())
                && !"logType".equals(field.getProperty())
                && (!"ERROR".equals(logType) || !"time".equals(field.getProperty())));
        sysLogMapper.selectPage(page, wrapper);
        return new PageResult<>(assembler.toDtos(page.getRecords()), page.getTotal());
    }

    @Override
    public PageResult<SysLogUserDTO> queryAllByUser(SysLogQueryRequest request) {
        Page<SysLogDO> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<SysLogDO> wrapper = wrapper(request, "INFO")
                .eq(SysLogDO::getUsername, SecurityUtils.getCurrentUsername())
                .select(SysLogDO::getId, SysLogDO::getDescription, SysLogDO::getRequestIp,
                        SysLogDO::getTime, SysLogDO::getAddress, SysLogDO::getBrowser,
                        SysLogDO::getCreateTime);
        sysLogMapper.selectPage(page, wrapper);
        return new PageResult<>(assembler.toUserDtos(page.getRecords()), page.getTotal());
    }

    private LambdaQueryWrapper<SysLogDO> adminWrapper(SysLogQueryRequest request, String logType) {
        return wrapper(request, logType)
                .like(request.getUsername() != null && !request.getUsername().isEmpty(),
                        SysLogDO::getUsername, request.getUsername());
    }

    private LambdaQueryWrapper<SysLogDO> wrapper(SysLogQueryRequest request, String logType) {
        LambdaQueryWrapper<SysLogDO> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysLogDO::getLogType, logType)
                .orderByDesc(SysLogDO::getId);
        String blurry = request.getBlurry();
        if (blurry != null && !blurry.isEmpty()) {
            wrapper.and(query -> query.like(SysLogDO::getUsername, blurry)
                    .or().like(SysLogDO::getDescription, blurry)
                    .or().like(SysLogDO::getAddress, blurry)
                    .or().like(SysLogDO::getRequestIp, blurry)
                    .or().like(SysLogDO::getMethod, blurry)
                    .or().like(SysLogDO::getParams, blurry));
        }
        if (request.getCreateTime() != null && request.getCreateTime().size() == 2) {
            wrapper.between(SysLogDO::getCreateTime,
                    request.getCreateTime().get(0), request.getCreateTime().get(1));
        }
        return wrapper;
    }

    @Async
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(String username, String browser, String ip, ProceedingJoinPoint joinPoint, SysLogDO sysLog) {
        if (sysLog == null) {
            throw new IllegalArgumentException("Log 不能为 null!");
        }

        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log aopLog = method.getAnnotation(Log.class);

        // 方法路径
        String methodName = joinPoint.getTarget().getClass().getName() + "." + signature.getName() + "()";

        // 获取参数
        JSONObject params = getParameter(method, joinPoint.getArgs());

        // 填充基本信息
        sysLog.setRequestIp(ip);
        sysLog.setAddress(StringUtils.getCityInfo(sysLog.getRequestIp()));
        sysLog.setMethod(methodName);
        sysLog.setUsername(username);
        sysLog.setParams(JSON.toJSONString(params));
        sysLog.setBrowser(browser);
        sysLog.setDescription(aopLog.value());

        // 如果没有获取到用户名，尝试从参数中获取
        if(StringUtils.isBlank(sysLog.getUsername())){
            sysLog.setUsername(params.getString("username"));
        }

        // 保存
        save(sysLog);
    }

    /**
     * 根据方法和传入的参数获取请求参数
     */
    private JSONObject getParameter(Method method, Object[] args) {
        JSONObject params = new JSONObject();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            // 过滤掉 MultiPartFile
            if (args[i] instanceof MultipartFile) {
                continue;
            }
            // 过滤掉 HttpServletResponse
            if (args[i] instanceof HttpServletResponse) {
                continue;
            }
            // 过滤掉 HttpServletRequest
            if (args[i] instanceof HttpServletRequest) {
                continue;
            }
            // 将RequestBody注解修饰的参数作为请求参数
            RequestBody requestBody = parameters[i].getAnnotation(RequestBody.class);
            if (requestBody != null) {
                Object json = JSON.toJSON(args[i]);
                if (json instanceof JSONArray) {
                    params.put("reqBodyList", json);
                } else {
                    params.putAll((JSONObject) json);
                }
            } else {
                String key = parameters[i].getName();
                params.put(key, args[i]);
            }
        }
        // 遍历敏感字段数组并替换值
        Set<String> keys = params.keySet();
        for (String key : SENSITIVE_KEYS) {
            if (keys.contains(key)) {
                params.put(key, "******");
            }
        }
        // 返回参数
        return params;
    }

    @Override
    public SysLogErrorDetailDTO findByErrDetail(Long id) {
        SysLogDO sysLog = sysLogMapper.selectOne(Wrappers.<SysLogDO>lambdaQuery()
                .select(SysLogDO::getExceptionDetail)
                .eq(SysLogDO::getId, id));
        return new SysLogErrorDetailDTO(sysLog == null ? null : sysLog.getExceptionDetail());
    }

    @Override
    public void download(SysLogQueryRequest request, HttpServletResponse response) throws IOException {
        download(sysLogMapper.selectList(adminWrapper(request, "INFO")), response);
    }

    @Override
    public void downloadErrors(SysLogQueryRequest request, HttpServletResponse response) throws IOException {
        LambdaQueryWrapper<SysLogDO> wrapper = adminWrapper(request, "ERROR");
        wrapper.select(SysLogDO.class, field -> !"time".equals(field.getProperty()));
        download(sysLogMapper.selectList(wrapper), response);
    }

    private void download(List<SysLogDO> sysLogs, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysLogDO sysLog : sysLogs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("用户名", sysLog.getUsername());
            map.put("IP", sysLog.getRequestIp());
            map.put("IP来源", sysLog.getAddress());
            map.put("描述", sysLog.getDescription());
            map.put("浏览器", sysLog.getBrowser());
            map.put("请求耗时/毫秒", sysLog.getTime());
            map.put("异常详情", sysLog.getExceptionDetail());
            map.put("创建日期", sysLog.getCreateTime());
            list.add(map);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=file.xlsx");
        ExcelUtil.write(list, response.getOutputStream());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delAllByError() {
        // 删除 ERROR 级别的日志
        sysLogMapper.delete(Wrappers.<SysLogDO>lambdaQuery().eq(SysLogDO::getLogType, "ERROR"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delAllByInfo() {
        // 删除 INFO 级别的日志
        sysLogMapper.delete(Wrappers.<SysLogDO>lambdaQuery().eq(SysLogDO::getLogType, "INFO"));
    }
}
