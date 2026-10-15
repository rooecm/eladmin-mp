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

import fun.rooe.admin.model.UserDataScope;

import java.util.Collection;
import java.util.Set;

/**
 * 数据权限服务类
 *
 * @author Zheng Jie
 * @date 2020-05-07
 */
public interface DataService {

    /**
     * 获取数据权限
     *
     * @param userId user ID
     * @param deptId current department ID
     * @return /
     */
    UserDataScope getDataScope(Long userId, Long deptId);

    UserDataScope getCurrentDataScope();

    void checkDeptAccess(Long deptId);

    void checkDeptAccess(Collection<Long> deptIds);

    void checkDeptReadAccess(Collection<Long> deptIds);

    void checkUserAccess(Long userId, Long deptId);

    Set<Long> expandDeptIds(Collection<Long> deptIds);
}
