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
package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.security.service.UserCacheManager;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.domain.dto.DeptDTO;
import fun.rooe.admin.modules.system.domain.entity.DeptDO;
import fun.rooe.admin.modules.system.domain.entity.RoleDeptDO;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.mapper.DeptMapper;
import fun.rooe.admin.modules.system.mapper.RoleDeptMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import fun.rooe.admin.modules.system.service.DataService;
import fun.rooe.admin.modules.system.service.DeptService;
import fun.rooe.admin.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* @author Zheng Jie
* @date 2019-03-25
*/
@Service
@RequiredArgsConstructor
public class DeptServiceImpl extends ServiceImpl<DeptMapper, DeptDO> implements DeptService {

    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final RedisUtils redisUtils;
    private final RoleDeptMapper roleDeptMapper;
    private final DeptAssembler deptAssembler;
    private final DataService dataService;
    private final UserCacheManager userCacheManager;

    @Override
    public List<DeptDTO> queryAll(DeptQueryRequest request) {
        UserDataScope dataScope = dataService.getCurrentDataScope();
        if (dataScope.isNone()
                || (dataScope.isSelf() && dataScope.selfDeptId() == null)) {
            return List.of();
        }
        LambdaQueryWrapper<DeptDO> queryWrapper = buildQueryWrapper(request, dataScope);
        List<DeptDO> list = deptMapper.selectList(queryWrapper);
        // Restricted scopes return only the highest visible nodes for tree rendering.
        if (!dataScope.isAll()) {
            list = deduplication(list);
        }
        return deptAssembler.toDtos(list);
    }

    private LambdaQueryWrapper<DeptDO> buildQueryWrapper(
            DeptQueryRequest request, UserDataScope dataScope) {
        boolean hasExplicitCondition = request.getName() != null
                || request.getPid() != null
                || request.getCreateTime() != null;
        boolean queryRootDept = !hasExplicitCondition
                && (dataScope.isAll()
                || Boolean.TRUE.equals(request.getPidIsNull()));

        Set<Long> visibleDeptIds = new LinkedHashSet<>(dataScope.deptIds());
        if (dataScope.selfDeptId() != null) {
            visibleDeptIds.add(dataScope.selfDeptId());
        }
        LambdaQueryWrapper<DeptDO> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.in(!dataScope.isAll(), DeptDO::getId, visibleDeptIds)
                .like(StringUtils.isNotBlank(request.getName()), DeptDO::getName, request.getName())
                .eq(request.getEnabled() != null, DeptDO::getEnabled, request.getEnabled())
                .eq(request.getPid() != null, DeptDO::getPid, request.getPid())
                .isNull(queryRootDept, DeptDO::getPid)
                .orderByAsc(DeptDO::getDeptSort);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2) {
            queryWrapper.between(DeptDO::getCreateTime,
                    request.getCreateTime().get(0), request.getCreateTime().get(1));
        }
        return queryWrapper;
    }

    @Override
    public DeptDO findById(Long id) {
        String key = CacheKey.DEPT_ID + id;
        DeptDO dept = redisUtils.get(key, DeptDO.class);
        if(dept == null){
            dept = deptMapper.selectById(id);
            redisUtils.set(key, dept, 1, TimeUnit.DAYS);
        }
        return dept;
    }

    @Override
    public List<DeptDO> findByPid(long pid) {
        return deptMapper.selectList(Wrappers.<DeptDO>lambdaQuery().eq(DeptDO::getPid, pid));
    }

    @Override
    public Set<DeptDO> findByRoleId(Long id) {
        return deptMapper.findByRoleId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DeptCreateRequest request) {
        dataService.checkDeptAccess(request.getPid());
        DeptDO dept = deptAssembler.toDept(request);
        save(dept);
        // 清理缓存
        updateSubCnt(dept.getPid());
        // 清理自定义角色权限的datascope缓存
        delCaches();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DeptUpdateRequest request) {
        dataService.checkDeptAccess(request.getId());
        // 旧的部门
        Long oldPid = findById(request.getId()).getPid();
        Long newPid = request.getPid();
        if (!Objects.equals(oldPid, newPid)) {
            dataService.checkDeptAccess(newPid);
        }
        if(request.getPid() != null && request.getId().equals(request.getPid())) {
            throw new BadRequestException("上级不能为自己");
        }
        DeptDO dept = getById(request.getId());
        deptAssembler.updateDept(request, dept);
        saveOrUpdate(dept);
        // 更新父节点中子节点数目
        updateSubCnt(oldPid);
        updateSubCnt(newPid);
        // 清理缓存
        delCaches();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(DeptDeleteRequest request) {
        Map<Long, DeptDO> deptById = new LinkedHashMap<>();
        for (Long id : request.getIds()) {
            DeptDO dept = findById(id);
            if (dept != null) {
                deptById.putIfAbsent(dept.getId(), dept);
                collectDeleteDepts(findByPid(id), deptById);
            }
        }
        dataService.checkDeptAccess(deptById.keySet());
        verification(deptById.values());
        for (DeptDO dept : deptById.values()) {
            // 清理缓存
            deptMapper.deleteById(dept.getId());
            updateSubCnt(dept.getPid());
        }
        delCaches();
    }

    private void collectDeleteDepts(List<DeptDO> deptList, Map<Long, DeptDO> deptById) {
        for (DeptDO dept : deptList) {
            if (deptById.putIfAbsent(dept.getId(), dept) == null) {
                List<DeptDO> children = findByPid(dept.getId());
                if (!CollectionUtils.isEmpty(children)) {
                    collectDeleteDepts(children, deptById);
                }
            }
        }
    }

    @Override
    public List<Long> getDeptChildren(List<DeptDO> deptList) {
        List<Long> list = new ArrayList<>();
        deptList.forEach(dept -> {
                    if (dept!=null && dept.getEnabled()) {
                        List<DeptDO> depts = findByPid(dept.getId());
                        if (!CollectionUtils.isEmpty(depts)) {
                            list.addAll(getDeptChildren(depts));
                        }
                        list.add(dept.getId());
                    }
                }
        );
        return list;
    }

    @Override
    public List<DeptDTO> getSuperior(DeptSuperiorRequest request) {
        List<Long> ids = request.getIds();
        UserDataScope dataScope = dataService.getCurrentDataScope();
        dataService.checkDeptReadAccess(ids);
        Set<Long> hierarchyIds = getHierarchyIds(ids);
        Map<Long, DeptDO> deptById = new LinkedHashMap<>();
        Set<Long> adjustedParentIds = new HashSet<>();
        for (Long id : ids) {
            DeptDO dept = findById(id);
            List<DeptDO> depts = getSuperiorDepts(dept, new ArrayList<>());
            if (request.isExclude()) {
                if (dept.getPid() != null) {
                    adjustedParentIds.add(dept.getPid());
                }
                // 编辑部门时不显示自己以及自己下级的数据，避免出现PID数据环形问题
                depts = depts.stream().filter(item -> !ids.contains(item.getId())).toList();
            }
            depts.stream()
                    .filter(item -> dataScope.permits(item.getId())
                            || hierarchyIds.contains(item.getId()))
                    .forEach(item -> deptById.putIfAbsent(item.getId(), item));
        }
        List<DeptDTO> deptDtos = deptAssembler.toDtos(new ArrayList<>(deptById.values()));
        deptDtos.stream()
                .filter(dept -> adjustedParentIds.contains(dept.getId()))
                .forEach(dept -> dept.setSubCount(Math.max(0,
                        Optional.ofNullable(dept.getSubCount()).orElse(0) - 1)));
        return deptAssembler.toTree(deptDtos);
    }

    private Set<Long> getHierarchyIds(Collection<Long> deptIds) {
        Set<Long> hierarchyIds = new HashSet<>();
        for (Long deptId : deptIds) {
            DeptDO current = findById(deptId);
            while (current != null && hierarchyIds.add(current.getId())) {
                current = current.getPid() == null ? null : findById(current.getPid());
            }
        }
        return hierarchyIds;
    }

    private List<DeptDO> getSuperiorDepts(DeptDO dept, List<DeptDO> depts) {
        if(dept.getPid() == null){
            depts.addAll(deptMapper.selectList(
                    Wrappers.<DeptDO>lambdaQuery().isNull(DeptDO::getPid)));
            return depts;
        }
        depts.addAll(findByPid(dept.getPid()));
        return getSuperiorDepts(findById(dept.getPid()), depts);
    }

    private void verification(Collection<DeptDO> depts) {
        Set<Long> deptIds = depts.stream().map(DeptDO::getId).collect(Collectors.toSet());
        if (userMapper.selectCount(
                Wrappers.<UserDO>lambdaQuery().in(UserDO::getDeptId, deptIds)) > 0) {
            throw new BadRequestException("所选部门存在用户关联，请解除后再试！");
        }
        if (roleDeptMapper.selectCount(
                Wrappers.<RoleDeptDO>lambdaQuery().in(RoleDeptDO::getDeptId, deptIds)) > 0) {
            throw new BadRequestException("所选部门存在角色关联，请解除后再试！");
        }
    }

    private void updateSubCnt(Long deptId){
        if(deptId != null){
            long count = deptMapper.selectCount(
                    Wrappers.<DeptDO>lambdaQuery().eq(DeptDO::getPid, deptId));
            deptMapper.update(Wrappers.<DeptDO>lambdaUpdate()
                    .eq(DeptDO::getId, deptId)
                    .set(DeptDO::getSubCount, Math.toIntExact(count)));
        }
    }

    private List<DeptDO> deduplication(List<DeptDO> list) {
        List<DeptDO> depts = new ArrayList<>();
        for (DeptDO dept : list) {
            boolean flag = true;
            for (DeptDO dept1 : list) {
                if (dept1.getId().equals(dept.getPid())) {
                    flag = false;
                    break;
                }
            }
            if (flag){
                depts.add(dept);
            }
        }
        return depts;
    }

    /**
     * Clears all scope-dependent caches after a department hierarchy change.
     */
    public void delCaches() {
        redisUtils.scanDel(CacheKey.DATA_USER + "*");
        redisUtils.scanDel(CacheKey.DEPT_ID + "*");
        userCacheManager.cleanAllUserCaches();
        // 删除数据权限
    }
}
