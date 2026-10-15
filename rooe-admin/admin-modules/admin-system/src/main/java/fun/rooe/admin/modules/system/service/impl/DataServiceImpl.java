/*
 * Copyright 2019-2025 Zheng Jie
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.system.domain.entity.DeptDO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.mapper.DeptMapper;
import fun.rooe.admin.modules.system.mapper.RoleMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import fun.rooe.admin.modules.system.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Resolves and enforces the department data scope assigned to a user.
 */
@Service
@RequiredArgsConstructor
public class DataServiceImpl implements DataService {

    private final RedisUtils redisUtils;
    private final RoleMapper roleMapper;
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;

    @Override
    public UserDataScope getDataScope(Long userId, Long deptId) {
        if (userId == null) {
            return UserDataScope.none();
        }
        String key = CacheKey.DATA_USER + userId;
        UserDataScope cached = redisUtils.get(key, UserDataScope.class);
        if (cached != null) {
            return cached;
        }

        List<RoleDO> roles = roleMapper.findByUserId(userId);
        if (roles.isEmpty()) {
            return cache(key, UserDataScope.none());
        }

        Set<Long> deptIds = new LinkedHashSet<>();
        boolean includeSelf = false;
        for (RoleDO role : roles) {
            DataScopeEnum configuredScope = DataScopeEnum.find(role.getDataScope());
            if (configuredScope == null) {
                throw new BadRequestException(
                        "Invalid data scope configured for role: " + role.getName());
            }
            switch (configuredScope) {
                case ALL -> {
                    return cache(key, UserDataScope.all());
                }
                case DEPT_AND_CHILD -> {
                    if (deptId != null) {
                        deptIds.addAll(expandDeptIds(List.of(deptId)));
                    }
                }
                case DEPT -> {
                    if (deptId != null) {
                        deptIds.add(deptId);
                    }
                }
                case SELF -> includeSelf = true;
                case CUSTOM -> addCustomDepartments(role.getId(), deptIds);
            }
        }
        return cache(
                key,
                deptIds.isEmpty()
                        ? (includeSelf
                        ? UserDataScope.self(userId, deptId)
                        : UserDataScope.none())
                        : UserDataScope.limited(
                        deptIds,
                        includeSelf ? userId : null,
                        includeSelf ? deptId : null));
    }

    @Override
    public UserDataScope getCurrentDataScope() {
        Long userId = SecurityUtils.getCurrentUserId();
        UserDO user = userMapper.selectById(userId);
        return user == null ? UserDataScope.none() : getDataScope(userId, user.getDeptId());
    }

    @Override
    public void checkDeptAccess(Long deptId) {
        if (!getCurrentDataScope().permits(deptId)) {
            throw new BadRequestException("No permission to access department: " + deptId);
        }
    }

    @Override
    public void checkDeptAccess(Collection<Long> deptIds) {
        UserDataScope scope = getCurrentDataScope();
        if (scope.isAll()) {
            return;
        }
        if (deptIds == null || deptIds.stream().anyMatch(id -> !scope.permits(id))) {
            throw new BadRequestException("Department data permission denied");
        }
    }

    @Override
    public void checkUserAccess(Long userId, Long deptId) {
        if (!getCurrentDataScope().permitsUser(userId, deptId)) {
            throw new BadRequestException("No permission to access user: " + userId);
        }
    }

    @Override
    public void checkDeptReadAccess(Collection<Long> deptIds) {
        UserDataScope scope = getCurrentDataScope();
        if (scope.isAll()) {
            return;
        }
        if (deptIds == null || deptIds.stream().anyMatch(id -> !scope.permitsDeptRead(id))) {
            throw new BadRequestException("Department data permission denied");
        }
    }

    @Override
    public Set<Long> expandDeptIds(Collection<Long> deptIds) {
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        if (deptIds != null) {
            for (Long deptId : deptIds) {
                if (deptId != null && result.add(deptId)) {
                    pending.add(deptId);
                }
            }
        }
        addDescendants(pending, result);
        return result;
    }

    private UserDataScope cache(String key, UserDataScope scope) {
        redisUtils.set(key, scope, 1, TimeUnit.DAYS);
        return scope;
    }

    private void addCustomDepartments(Long roleId, Set<Long> result) {
        for (DeptDO dept : deptMapper.findByRoleId(roleId)) {
            result.add(dept.getId());
        }
    }

    private void addDescendants(Deque<Long> pending, Set<Long> result) {
        while (!pending.isEmpty()) {
            List<Long> parentIds = new ArrayList<>();
            while (!pending.isEmpty()) {
                parentIds.add(pending.removeFirst());
            }
            List<DeptDO> children = deptMapper.selectList(
                    Wrappers.<DeptDO>lambdaQuery().in(DeptDO::getPid, parentIds));
            for (DeptDO child : children) {
                if (result.add(child.getId())) {
                    pending.add(child.getId());
                }
            }
        }
    }
}
