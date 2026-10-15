/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package fun.rooe.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.domain.SysLogDO;
import fun.rooe.admin.domain.dto.SysLogQueryCriteria;
import fun.rooe.admin.response.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;

import java.io.IOException;
import java.util.List;

/**
 * @author Zheng Jie
 * @date 2018-11-24
 */
public interface SysLogService extends IService<SysLogDO> {

    /**
     * 分页查询
     *
     * @param criteria 查询条件
     * @param page     分页参数
     * @return /
     */
    PageResult<SysLogDO> queryAll(SysLogQueryCriteria criteria, Page<SysLogDO> page);

    /**
     * 查询全部数据
     * @param criteria 查询条件
     * @return /
     */
    List<SysLogDO> queryAll(SysLogQueryCriteria criteria);

    /**
     * 查询用户日志
     * @param criteria 查询条件
     * @param page 分页参数
     * @return -
     */
    PageResult<SysLogDO> queryAllByUser(SysLogQueryCriteria criteria, Page<SysLogDO> page);

    /**
     * 保存日志数据
     * @param username 用户
     * @param browser 浏览器
     * @param ip 请求IP
     * @param joinPoint /
     * @param sysLog 日志实体
     */
    void save(String username, String browser, String ip, ProceedingJoinPoint joinPoint, SysLogDO sysLog);

    /**
     * 查询异常详情
     * @param id 日志ID
     * @return Object
     */
    Object findByErrDetail(Long id);

    /**
     * 导出日志
     * @param sysLogs 待导出的数据
     * @param response /
     * @throws IOException /
     */
    void download(List<SysLogDO> sysLogs, HttpServletResponse response) throws IOException;

    /**
     * 删除所有错误日志
     */
    void delAllByError();

    /**
     * 删除所有INFO日志
     */
    void delAllByInfo();
}
