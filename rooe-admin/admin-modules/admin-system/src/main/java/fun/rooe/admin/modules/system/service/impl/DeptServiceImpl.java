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

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.infrastructure.utils.PageUtil;
import fun.rooe.admin.utils.*;
import lombok.RequiredArgsConstructor;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.domain.Dept;
import fun.rooe.admin.modules.system.domain.User;
import fun.rooe.admin.modules.system.domain.dto.DeptDto;
import fun.rooe.admin.modules.system.mapper.RoleMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import fun.rooe.admin.modules.system.domain.dto.DeptQueryCriteria;
import fun.rooe.admin.modules.system.mapper.DeptMapper;
import fun.rooe.admin.modules.system.service.DeptService;
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.Field;
import org.apache.commons.lang3.reflect.FieldUtils;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

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
    private final RoleMapper roleMapper;
    private final DeptAssembler deptAssembler;

    @Override
    public PageResult<DeptDto> queryAll(DeptQueryCriteria criteria) throws Exception {
        String dataScopeType = SecurityUtils.getDataScopeType();
        if(dataScopeType.equals(DataScopeEnum.ALL.getValue())){
            criteria.setPidIsNull(true);
        }
        List<Field> fields = List.of(FieldUtils.getAllFields(criteria.getClass()));
        List<String> fieldNames = new ArrayList<String>(){{ add("pidIsNull");add("enabled");}};
        for (Field field : fields) {
            //设置对象的访问权限，保证对private的属性的访问
            field.setAccessible(true);
            Object val = field.get(criteria);
            if(fieldNames.contains(field.getName())){
                continue;
            }
            if (Objects.nonNull(val)) {
                criteria.setPidIsNull(null);
                break;
            }
        }
        // 数据权限
        criteria.setIds(SecurityUtils.getCurrentUserDataScope());
        List<Dept> list = deptMapper.findAll(criteria);
        // 如果为空，就代表为自定义权限或者本级权限，就需要去重，不理解可以注释掉，看查询结果
        if(StringUtils.isBlank(dataScopeType)){
            list = deduplication(list);
        }
        return PageUtil.toPage(deptAssembler.toDtos(list));
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
        return deptMapper.findByPid(pid);
    }

    @Override
    public Set<Dept> findByRoleId(Long id) {
        return deptMapper.findByRoleId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Dept resources) {
        save(resources);
        // 清理缓存
        updateSubCnt(resources.getPid());
        // 清理自定义角色权限的datascope缓存
        delCaches(resources.getPid());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Dept resources) {
        // 旧的部门
        Long oldPid = findById(resources.getId()).getPid();
        Long newPid = resources.getPid();
        if(resources.getPid() != null && resources.getId().equals(resources.getPid())) {
            throw new BadRequestException("上级不能为自己");
        }
        Dept dept = getById(resources.getId());
        resources.setId(dept.getId());
        saveOrUpdate(resources);
        // 更新父节点中子节点数目
        updateSubCnt(oldPid);
        updateSubCnt(newPid);
        // 清理缓存
        delCaches(resources.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Dept> depts) {
        for (Dept dept : depts) {
            // 清理缓存
            delCaches(dept.getId());
            deptMapper.deleteById(dept.getId());
            updateSubCnt(dept.getPid());
        }
    }

    @Override
    public Set<Dept> getDeleteDepts(List<Dept> menuList, Set<Dept> deptSet) {
        for (Dept dept : menuList) {
            deptSet.add(dept);
            List<Dept> depts = deptMapper.findByPid(dept.getId());
            if(!CollectionUtils.isEmpty(depts)){
                getDeleteDepts(depts, deptSet);
            }
        }
        return deptSet;
    }

    @Override
    public List<Long> getDeptChildren(List<Dept> deptList) {
        List<Long> list = new ArrayList<>();
        deptList.forEach(dept -> {
                    if (dept!=null && dept.getEnabled()) {
                        List<Dept> depts = deptMapper.findByPid(dept.getId());
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
    public PageResult<DeptDto> getSuperior(List<Long> ids, Boolean exclude) {
        Set<Dept> deptSet = new LinkedHashSet<>();
        Set<Long> adjustedParentIds = new HashSet<>();
        for (Long id : ids) {
            Dept dept = findById(id);
            List<Dept> depts = getSuperiorDepts(dept, new ArrayList<>());
            if (Boolean.TRUE.equals(exclude)) {
                if (dept.getPid() != null) {
                    adjustedParentIds.add(dept.getPid());
                }
                // 编辑部门时不显示自己以及自己下级的数据，避免出现PID数据环形问题
                depts = depts.stream().filter(item -> !ids.contains(item.getId())).toList();
            }
            deptSet.addAll(depts);
        }
        List<DeptDto> deptDtos = deptAssembler.toDtos(new ArrayList<>(deptSet));
        deptDtos.stream()
                .filter(dept -> adjustedParentIds.contains(dept.getId()))
                .forEach(dept -> dept.setSubCount(Math.max(0,
                        Optional.ofNullable(dept.getSubCount()).orElse(0) - 1)));
        return deptAssembler.toTree(deptDtos);
    }

    private List<Dept> getSuperiorDepts(Dept dept, List<Dept> depts) {
        if(dept.getPid() == null){
            depts.addAll(deptMapper.findByPidIsNull());
            return depts;
        }
        depts.addAll(deptMapper.findByPid(dept.getPid()));
        return getSuperiorDepts(findById(dept.getPid()), depts);
    }

    @Override
    public void verification(Set<Dept> depts) {
        Set<Long> deptIds = depts.stream().map(Dept::getId).collect(Collectors.toSet());
        if(userMapper.countByDepts(deptIds) > 0){
            throw new BadRequestException("所选部门存在用户关联，请解除后再试！");
        }
        if(roleMapper.countByDepts(deptIds) > 0){
            throw new BadRequestException("所选部门存在角色关联，请解除后再试！");
        }
    }

    private void updateSubCnt(Long deptId){
        if(deptId != null){
            int count = deptMapper.countByPid(deptId);
            deptMapper.updateSubCntById(count, deptId);
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
