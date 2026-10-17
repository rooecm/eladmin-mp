package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.modules.system.converter.SysLogAssembler;
import fun.rooe.admin.modules.system.domain.dto.SysLogDTO;
import fun.rooe.admin.modules.system.domain.dto.SysLogUserDTO;
import fun.rooe.admin.modules.system.domain.entity.SysLogDO;
import fun.rooe.admin.modules.system.domain.request.SysLogQueryRequest;
import fun.rooe.admin.modules.system.mapper.SysLogMapper;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.ExcelUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class SysLogServiceImplTest {
    private SysLogMapper mapper;
    private SysLogServiceImpl service;
    private LambdaQueryWrapper<SysLogDO> capturedQuery;
    // 使用 JVM 默认时区，保证时间戳的构造与格式化一致
    private final ObjectMapper objectMapper = new ObjectMapper().setTimeZone(TimeZone.getDefault());

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysLogDO.class);
        mapper = mock(SysLogMapper.class);
        service = new SysLogServiceImpl(mapper, Mappers.getMapper(SysLogAssembler.class), objectMapper);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SysLogDO> page = invocation.getArgument(0);
            capturedQuery = invocation.getArgument(1);
            page.setRecords(List.of(log()));
            page.setTotal(31);
            return page;
        });
    }

    @Test
    void adminQueryPreservesGroupedSearchDateRangeSortingAndPagination() {
        SysLogQueryRequest request = new SysLogQueryRequest();
        request.setBlurry("keyword");
        request.setUsername("admin");
        request.setPage(2);
        request.setSize(15);
        Timestamp start = Timestamp.valueOf("2026-10-01 00:00:00");
        Timestamp end = Timestamp.valueOf("2026-10-16 23:59:59");
        request.setCreateTime(List.of(start, end));

        PageResult<SysLogDTO> result = service.queryAll(request);

        String sql = capturedQuery.getSqlSegment();
        assertTrue(sql.contains("AND (username LIKE"), sql);
        assertTrue(sql.contains("OR description LIKE"), sql);
        assertTrue(sql.contains("OR address LIKE"), sql);
        assertTrue(sql.contains("OR request_ip LIKE"), sql);
        assertTrue(sql.contains("OR method LIKE"), sql);
        assertTrue(sql.contains("OR params LIKE"), sql);
        assertTrue(sql.contains("create_time BETWEEN"), sql);
        assertTrue(sql.endsWith("ORDER BY id DESC"), sql);
        assertTrue(capturedQuery.getParamNameValuePairs().containsValue("INFO"));
        assertTrue(capturedQuery.getParamNameValuePairs().containsValue("%keyword%"));
        assertTrue(capturedQuery.getParamNameValuePairs().containsValue("%admin%"));
        assertTrue(capturedQuery.getParamNameValuePairs().containsValue(start));
        assertTrue(capturedQuery.getParamNameValuePairs().containsValue(end));
        assertFalse(capturedQuery.getSqlSelect().contains("exception_detail"));
        ArgumentCaptor<Page<SysLogDO>> page = ArgumentCaptor.forClass(Page.class);
        verify(mapper).selectPage(page.capture(), any());
        assertEquals(2, page.getValue().getCurrent());
        assertEquals(15, page.getValue().getSize());
        assertEquals(31, result.getTotalElements());
        assertEquals("admin", result.getContent().get(0).getUsername());
        JsonNode json = objectMapper.valueToTree(result.getContent().get(0));
        assertEquals("2026-10-16 12:00:00", json.path("createTime").asText());
        assertFalse(json.has("exceptionDetail"));
        assertFalse(json.has("logType"));
    }

    @Test
    void errorQueryFixesLogTypeAndPreservesItsColumnProjection() {
        service.queryErrors(new SysLogQueryRequest());

        capturedQuery.getSqlSegment();
        assertEquals(List.of("ERROR"), List.copyOf(capturedQuery.getParamNameValuePairs().values()));
        Set<String> columns = Set.of(capturedQuery.getSqlSelect().split(","));
        assertFalse(columns.contains("time"));
        assertFalse(columns.contains("exception_detail"));
        assertTrue(columns.containsAll(Set.of("id", "method", "params", "username")));
    }

    @Test
    void personalLogsUseExactCurrentUsernameAndExposeOnlyPersonalFields() {
        SysLogQueryRequest request = new SysLogQueryRequest();
        request.setUsername("someone-else");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUsername).thenReturn("admin");

            PageResult<SysLogUserDTO> result = service.queryAllByUser(request);

            String sql = capturedQuery.getSqlSegment();
            assertTrue(sql.contains("username ="), sql);
            assertFalse(sql.contains("username LIKE"), sql);
            assertTrue(capturedQuery.getParamNameValuePairs().containsValue("admin"));
            assertFalse(capturedQuery.getParamNameValuePairs().containsValue("someone-else"));
            Set<String> columns = Set.of(capturedQuery.getSqlSelect().split(","));
            assertEquals(Set.of("id", "description", "request_ip", "time", "address",
                    "browser", "create_time"), columns);
            JsonNode json = objectMapper.valueToTree(result.getContent().get(0));
            Set<String> fields = new HashSet<>();
            json.fieldNames().forEachRemaining(fields::add);
            assertEquals(Set.of("id", "description", "requestIp", "time", "address",
                    "browser", "createTime"), fields);
            assertEquals("someone-else", request.getUsername());
        }
    }

    @Test
    void emptyFiltersDoNotAddLikeOrTimeConditionsButWhitespaceRemainsSearchable() {
        SysLogQueryRequest request = new SysLogQueryRequest();
        request.setUsername("");
        request.setBlurry("");
        service.queryAll(request);
        assertFalse(capturedQuery.getSqlSegment().contains("LIKE"));
        assertFalse(capturedQuery.getSqlSegment().contains("BETWEEN"));

        request.setBlurry(" ");
        service.queryAll(request);
        assertTrue(capturedQuery.getSqlSegment().contains("LIKE"));
        assertTrue(capturedQuery.getParamNameValuePairs().containsValue("% %"));
    }

    @Test
    void detailResponsePreservesExceptionPropertyAndHandlesMissingDetails() {
        when(mapper.selectOne(any())).thenReturn(log(), null);

        assertEquals("stack trace", service.findByErrDetail(1L).getException());
        assertNull(service.findByErrDetail(2L).getException());
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorExportRetainsExceptionDetailsAndDoesNotUsePagination() throws Exception {
        SysLogDO error = log();
        error.setTime(null);
        when(mapper.selectList(any())).thenReturn(List.of(error));
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream output = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(output);

        try (MockedStatic<ExcelUtil> excel = mockStatic(ExcelUtil.class)) {
            service.downloadErrors(new SysLogQueryRequest(), response);

            ArgumentCaptor<List<Map<String, Object>>> rows = ArgumentCaptor.forClass(List.class);
            excel.verify(() -> ExcelUtil.write(rows.capture(), same(output)));
            assertEquals("stack trace", rows.getValue().get(0).get("异常详情"));
            assertEquals("admin", rows.getValue().get(0).get("用户名"));
            assertNull(rows.getValue().get(0).get("请求耗时/毫秒"));
        }
        ArgumentCaptor<LambdaQueryWrapper<SysLogDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("ERROR"));
        assertTrue(query.getValue().getSqlSelect().contains("exception_detail"));
        verify(mapper, never()).selectPage(any(Page.class), any());
        verify(response).setHeader("Content-Disposition", "attachment;filename=file.xlsx");
    }

    private SysLogDO log() {
        SysLogDO log = new SysLogDO("INFO", 12L);
        log.setId(1L);
        log.setUsername("admin");
        log.setDescription("operation");
        log.setMethod("example.method()");
        log.setParams("parameters");
        log.setRequestIp("127.0.0.1");
        log.setAddress("local");
        log.setBrowser("browser");
        log.setExceptionDetail("stack trace");
        log.setCreateTime(Timestamp.valueOf("2026-10-16 12:00:00"));
        return log;
    }
}
