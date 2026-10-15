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
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.domain.dto.DeptDto;
import fun.rooe.admin.modules.system.domain.entity.Dept;
import fun.rooe.admin.modules.system.domain.entity.RoleDept;
import fun.rooe.admin.modules.system.domain.entity.User;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.mapper.DeptMapper;
import fun.rooe.admin.modules.system.mapper.RoleDeptMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
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
public class DeptServiceImpl extends ServiceImpl<DeptMapper, Dept> implements DeptService {

    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final RedisUtils redisUtils;
    private final RoleDeptMapper roleDeptMapper;
    private final DeptAssembler deptAssembler;

    @Override
    public List<DeptDto> queryAll(DeptQueryRequest request) {
        String dataScopeType = SecurityUtils.getDataScopeType();
        List<Long> dataScopeIds = SecurityUtils.getCurrentUserDataScope();
        LambdaQueryWrapper<Dept> queryWrapper = buildQueryWrapper(request, dataScopeIds, dataScopeType);
        List<Dept> list = deptMapper.selectList(queryWrapper);
        // 如果为空，就代表为自定义权限或者本级权限，就需要去重，不理解可以注释掉，看查询结果
        if(StringUtils.isBlank(dataScopeType)){
            list = deduplication(list);
        }
        return deptAssembler.toDtos(list);
    }

    private LambdaQueryWrapper<Dept> buildQueryWrapper(DeptQueryRequest request,
                                                        List<Long> dataScopeIds,
                                                        String dataScopeType) {
        boolean hasExplicitCondition = request.getName() != null
                || request.getPid() != null
                || request.getCreateTime() != null;
        boolean queryRootDept = !hasExplicitCondition
                && (DataScopeEnum.ALL.getValue().equals(dataScopeType)
                || Boolean.TRUE.equals(request.getPidIsNull()));

        LambdaQueryWrapper<Dept> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.in(!CollectionUtils.isEmpty(dataScopeIds), Dept::getId, dataScopeIds)
                .like(StringUtils.isNotBlank(request.getName()), Dept::getName, request.getName())
                .eq(request.getEnabled() != null, Dept::getEnabled, request.getEnabled())
                .eq(request.getPid() != null, Dept::getPid, request.getPid())
                .isNull(queryRootDept, Dept::getPid)
                .orderByAsc(Dept::getDeptSort);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2) {
            queryWrapper.between(Dept::getCreateTime,
                    request.getCreateTime().get(0), request.getCreateTime().get(1));
        }
        return queryWrapper;
    }

    @Override
    public Dept findById(Long id) {
        String key = CacheKey.DEPT_ID + id;
        Dept dept = redisUtils.get(key, Dept.class);
        if(dept == null){
            dept = deptMapper.selectById(id);
            redisUtils.set(key, dept, 1, TimeUnit.DAYS);
        }
        return dept;
    }

    @Override
    public List<Dept> findByPid(long pid) {
        return deptMapper.selectList(Wrappers.<Dept>lambdaQuery().eq(Dept::getPid, pid));
    }

    @Override
    public Set<Dept> findByRoleId(Long id) {
        return deptMapper.findByRoleId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DeptCreateRequest request) {
        Dept dept = deptAssembler.toDept(request);
        save(dept);
        // 清理缓存
        updateSubCnt(dept.getPid());
        // 清理自定义角色权限的datascope缓存
        delCaches(dept.getPid());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DeptUpdateRequest request) {
        // 旧的部门
        Long oldPid = findById(request.getId()).getPid();
        Long newPid = request.getPid();
        if(request.getPid() != null && request.getId().equals(request.getPid())) {
            throw new BadRequestException("上级不能为自己");
        }
        Dept dept = getById(request.getId());
        deptAssembler.updateDept(request, dept);
        saveOrUpdate(dept);
        // 更新父节点中子节点数目
        updateSubCnt(oldPid);
        updateSubCnt(newPid);
        // 清理缓存
        delCaches(request.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(DeptDeleteRequest request) {
        Map<Long, Dept> deptById = new LinkedHashMap<>();
        for (Long id : request.getIds()) {
            Dept dept = findById(id);
            if (dept != null) {
                deptById.putIfAbsent(dept.getId(), dept);
                collectDeleteDepts(findByPid(id), deptById);
            }
        }
        verification(deptById.values());
        for (Dept dept : deptById.values()) {
            // 清理缓存
            delCaches(dept.getId());
            deptMapper.deleteById(dept.getId());
            updateSubCnt(dept.getPid());
        }
    }

    private void collectDeleteDepts(List<Dept> deptList, Map<Long, Dept> deptById) {
        for (Dept dept : deptList) {
            if (deptById.putIfAbsent(dept.getId(), dept) == null) {
                List<Dept> children = findByPid(dept.getId());
                if (!CollectionUtils.isEmpty(children)) {
                    collectDeleteDepts(children, deptById);
                }
            }
        }
    }

    @Override
    public List<Long> getDeptChildren(List<Dept> deptList) {
        List<Long> list = new ArrayList<>();
        deptList.forEach(dept -> {
                    if (dept!=null && dept.getEnabled()) {
                        List<Dept> depts = findByPid(dept.getId());
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
    public List<DeptDto> getSuperior(DeptSuperiorRequest request) {
        List<Long> ids = request.getIds();
        Map<Long, Dept> deptById = new LinkedHashMap<>();
        Set<Long> adjustedParentIds = new HashSet<>();
        for (Long id : ids) {
            Dept dept = findById(id);
            List<Dept> depts = getSuperiorDepts(dept, new ArrayList<>());
            if (request.isExclude()) {
                if (dept.getPid() != null) {
                    adjustedParentIds.add(dept.getPid());
                }
                // 编辑部门时不显示自己以及自己下级的数据，避免出现PID数据环形问题
                depts = depts.stream().filter(item -> !ids.contains(item.getId())).toList();
            }
            depts.forEach(item -> deptById.putIfAbsent(item.getId(), item));
        }
        List<DeptDto> deptDtos = deptAssembler.toDtos(new ArrayList<>(deptById.values()));
        deptDtos.stream()
                .filter(dept -> adjustedParentIds.contains(dept.getId()))
                .forEach(dept -> dept.setSubCount(Math.max(0,
                        Optional.ofNullable(dept.getSubCount()).orElse(0) - 1)));
        return deptAssembler.toTree(deptDtos);
    }

    private List<Dept> getSuperiorDepts(Dept dept, List<Dept> depts) {
        if(dept.getPid() == null){
            depts.addAll(deptMapper.selectList(
                    Wrappers.<Dept>lambdaQuery().isNull(Dept::getPid)));
            return depts;
        }
        depts.addAll(findByPid(dept.getPid()));
        return getSuperiorDepts(findById(dept.getPid()), depts);
    }

    private void verification(Collection<Dept> depts) {
        Set<Long> deptIds = depts.stream().map(Dept::getId).collect(Collectors.toSet());
        if (userMapper.selectCount(
                Wrappers.<User>lambdaQuery().in(User::getDeptId, deptIds)) > 0) {
            throw new BadRequestException("所选部门存在用户关联，请解除后再试！");
        }
        if (roleDeptMapper.selectCount(
                Wrappers.<RoleDept>lambdaQuery().in(RoleDept::getDeptId, deptIds)) > 0) {
            throw new BadRequestException("所选部门存在角色关联，请解除后再试！");
        }
    }

    private void updateSubCnt(Long deptId){
        if(deptId != null){
            long count = deptMapper.selectCount(
                    Wrappers.<Dept>lambdaQuery().eq(Dept::getPid, deptId));
            deptMapper.update(Wrappers.<Dept>lambdaUpdate()
                    .eq(Dept::getId, deptId)
                    .set(Dept::getSubCount, Math.toIntExact(count)));
        }
    }

    private List<Dept> deduplication(List<Dept> list) {
        List<Dept> depts = new ArrayList<>();
        for (Dept dept : list) {
            boolean flag = true;
            for (Dept dept1 : list) {
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
     * 清理缓存
     * @param id /
     */
    public void delCaches(Long id){
        List<User> users = userMapper.findByRoleDeptId(id);
        // 删除数据权限
        redisUtils.delByKeys(CacheKey.DATA_USER, users.stream().map(User::getId).collect(Collectors.toSet()));
        redisUtils.del(CacheKey.DEPT_ID + id);
    }
}
