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
package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.dto.DeptDto;
import fun.rooe.admin.modules.system.domain.entity.Dept;
import fun.rooe.admin.modules.system.domain.request.*;

import java.util.List;
import java.util.Set;

/**
* @author Zheng Jie
* @date 2019-03-25
*/
public interface DeptService extends IService<Dept> {

    /**
     * 查询所有数据
     * @param request 查询条件
     * @return /
     */
    List<DeptDto> queryAll(DeptQueryRequest request);

    /**
     * 根据ID查询
     * @param id /
     * @return /
     */
    Dept findById(Long id);

    /**
     * 创建
     * @param request 新增参数
     */
    void create(DeptCreateRequest request);

    /**
     * 编辑
     * @param request 修改参数
     */
    void update(DeptUpdateRequest request);

    /**
     * 删除
     * @param request 删除参数
     *
     */
    void delete(DeptDeleteRequest request);

    /**
     * 根据PID查询
     * @param pid /
     * @return /
     */
    List<Dept> findByPid(long pid);

    /**
     * 根据角色ID查询
     * @param id /
     * @return /
     */
    Set<Dept> findByRoleId(Long id);

    /**
     * 根据ID获取同级与上级数据
     * @param request 查询参数
     * @return /
     */
    List<DeptDto> getSuperior(DeptSuperiorRequest request);

    /**
     * 获取
     * @param deptList 、
     * @return 、
     */
    List<Long> getDeptChildren(List<Dept> deptList);

}
