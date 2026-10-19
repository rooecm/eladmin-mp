package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.framework.config.security.config.AuthProperties;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.config.properties.FileProperties;
import fun.rooe.admin.infrastructure.error.StorageErrorCode;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.security.service.*;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.converter.JobAssembler;
import fun.rooe.admin.modules.system.converter.RoleAssembler;
import fun.rooe.admin.modules.system.converter.UserAssembler;
import fun.rooe.admin.modules.system.domain.dto.JobDTO;
import fun.rooe.admin.modules.system.domain.dto.RoleDTO;
import fun.rooe.admin.modules.system.domain.dto.UserDTO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.domain.entity.UserJobDO;
import fun.rooe.admin.modules.system.domain.entity.UserRoleDO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.mapper.*;
import fun.rooe.admin.modules.system.service.*;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.ExcelUtil;
import fun.rooe.admin.utils.FileUtil;
import fun.rooe.admin.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordinates user persistence, authorization scope, associations, and cache invalidation.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {
    private final UserMapper userMapper;
    private final UserJobMapper userJobMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final JobMapper jobMapper;
    private final DeptMapper deptMapper;
    private final DeptService deptService;
    private final RoleService roleService;
    private final DataService dataService;
    private final FileProperties properties;
    private final UserCacheManager userCacheManager;
    private final AdminPrivilegeGuard privilegeGuard;
    private final AuthVersionService authVersions;
    private final AuthCacheInvalidator authInvalidator;
    private final AuthProperties authProperties;
    private final UserCredentialService userCredentialService;
    private final UserAssembler assembler;
    private final DeptAssembler deptAssembler;
    private final RoleAssembler roleAssembler;
    private final JobAssembler jobAssembler;

    @Override
    public PageResult<UserDTO> queryAll(UserQueryRequest request) {
        UserDataScope dataScope = dataService.getCurrentDataScope();
        if (dataScope.isNone()) {
            return new PageResult<>(new ArrayList<>(), 0);
        }
        Page<UserDO> page = new Page<>(request.getPage(), request.getSize());
        userMapper.selectPage(page, wrapper(request, requestedDeptIds(request), dataScope));
        return new PageResult<>(assemble(page.getRecords()), page.getTotal());
    }

    @Override
    public List<UserDTO> queryList(UserQueryRequest request) {
        UserDataScope dataScope = dataService.getCurrentDataScope();
        return dataScope.isNone()
                ? new ArrayList<>()
                : assemble(userMapper.selectList(
                wrapper(request, requestedDeptIds(request), dataScope)));
    }

    private Set<Long> requestedDeptIds(UserQueryRequest request) {
        Set<Long> requested = new LinkedHashSet<>();
        if (request.getDeptId() != null) {
            requested.add(request.getDeptId());
            requested.addAll(
                    deptService.getDeptChildren(deptService.findByPid(request.getDeptId())));
        }
        return requested;
    }

    private LambdaQueryWrapper<UserDO> wrapper(
            UserQueryRequest request, Set<Long> requestedDeptIds, UserDataScope dataScope) {
        LambdaQueryWrapper<UserDO> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(request.getId() != null, UserDO::getId, request.getId())
                .eq(request.getEnabled() != null, UserDO::getEnabled, request.getEnabled())
                .in(!requestedDeptIds.isEmpty(), UserDO::getDeptId, requestedDeptIds)
                .and(
                        StringUtils.isNotBlank(request.getBlurry()),
                        query ->
                                query.like(UserDO::getUsername, request.getBlurry())
                                        .or()
                                        .like(UserDO::getNickName, request.getBlurry())
                                        .or()
                                        .like(UserDO::getEmail, request.getBlurry()))
                .orderByDesc(UserDO::getId);
        if (!dataScope.isAll()) {
            wrapper.and(permission -> {
                if (!dataScope.getDeptIds().isEmpty()) {
                    permission.in(UserDO::getDeptId, dataScope.getDeptIds());
                }
                if (dataScope.getSelfUserId() != null) {
                    if (!dataScope.getDeptIds().isEmpty()) {
                        permission.or();
                    }
                    permission.eq(UserDO::getId, dataScope.getSelfUserId());
                }
            });
        }
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2)
            wrapper.between(
                    UserDO::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        return wrapper;
    }

    private List<UserDTO> assemble(List<UserDO> users) {
        List<UserDTO> result = assembler.toDtos(users);
        for (UserDTO dto : result) {
            if (dto.getDeptId() != null)
                dto.setDept(deptAssembler.toDto(deptMapper.selectById(dto.getDeptId())));
            dto.setRoles(
                    new LinkedHashSet<>(
                            roleAssembler.toDtos(roleMapper.findByUserId(dto.getId()))));
            dto.setJobs(
                    new LinkedHashSet<>(
                            jobAssembler.toDtos(
                                    new ArrayList<>(jobMapper.findByUserId(dto.getId())))));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDO findById(long id) {
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserCreateRequest request) {
        // 在同一事务内先串行化管理写操作并校验角色授予范围，再保存用户及关联关系。
        privilegeGuard.lockAdministration();
        privilegeGuard.assignRoles(null, roleIds(request.getRoles()));
        request.setUsername(LoginUserDetailsService.canonical(request.getUsername()));
        Long deptId = request.getDept().getId();
        Set<Long> roleIds = roleIds(request.getRoles());
        dataService.checkDeptAccess(deptId);
        checkLevel(roleIds);
        roleService.checkDataScopes(roleIds, deptId);
        checkUnique(request.getUsername(), request.getEmail(), request.getPhone(), null);
        UserDO user = assembler.toEntity(request);
        user.setPassword(userCredentialService.encode(authProperties.getInitialPassword()));
        // 新建账号只使用配置的初始密码，首次登录后必须自行改密。
        user.setMustChangePassword(true);
        user.setIsAdmin(false);
        save(user);
        insertRelations(user.getId(), request.getJobs(), request.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateRequest request) {
        privilegeGuard.lockAdministration();
        privilegeGuard.changeUser(request.getId(), !Boolean.TRUE.equals(request.getEnabled()));
        privilegeGuard.assignRoles(request.getId(), roleIds(request.getRoles()));
        request.setUsername(LoginUserDetailsService.canonical(request.getUsername()));
        UserDO user = getById(request.getId());
        checkUserAccess(user);
        checkLevel(roleMapper.findByUserId(user.getId()).stream()
                .map(RoleDO::getId)
                .collect(Collectors.toSet()));
        Long newDeptId = request.getDept().getId();
        if (!Objects.equals(user.getDeptId(), newDeptId)) {
            dataService.checkDeptAccess(newDeptId);
        }
        Set<Long> newRoles = roleIds(request.getRoles());
        checkLevel(newRoles);
        roleService.checkDataScopes(newRoles, newDeptId);
        checkUnique(request.getUsername(), request.getEmail(), request.getPhone(), user.getId());
        // 禁用递增认证版本；普通角色调整通过提交后的缓存失效更新权限。
        if (Boolean.TRUE.equals(user.getEnabled()) && !Boolean.TRUE.equals(request.getEnabled()))
            authVersions.bumpAuthVersion(user.getId(), "disabled");
        assembler.updateEntity(request, user);
        updateById(user);
        delCaches(user.getId(), user.getUsername());
        // 请求包含完整关联状态，用户、岗位和角色关系在同一事务内整体替换。
        userJobMapper.delete(
                Wrappers.<UserJobDO>lambdaQuery().eq(UserJobDO::getUserId, user.getId()));
        userRoleMapper.delete(
                Wrappers.<UserRoleDO>lambdaQuery().eq(UserRoleDO::getUserId, user.getId()));
        insertRelations(user.getId(), request.getJobs(), request.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCenter(UserCenterUpdateRequest request) {
        if (!request.getId().equals(SecurityUtils.getCurrentUserId()))
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "不能修改他人资料");
        UserDO user = getById(request.getId());
        UserDO duplicate =
                userMapper.selectOne(
                        Wrappers.<UserDO>lambdaQuery().eq(UserDO::getPhone, request.getPhone()));
        if (duplicate != null && !user.getId().equals(duplicate.getId()))
            throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS, "手机号已存在");
        userMapper.update(Wrappers.<UserDO>lambdaUpdate().eq(UserDO::getId, user.getId())
                .set(UserDO::getNickName, request.getNickName())
                .set(UserDO::getPhone, request.getPhone())
                .set(UserDO::getGender, request.getGender()));
        delCaches(user.getId(), user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UserDeleteRequest request) {
        // 整批删除持有同一管理锁；任一用户触发保护规则，整批操作回滚。
        privilegeGuard.lockAdministration();
        int currentLevel = roleService.getCurrentUserLevel();
        for (Long id : request.getIds()) {
            checkUserAccess(getById(id));
            int targetLevel =
                    roleService.findByUsersId(id).stream()
                            .map(RoleDO::getLevel)
                            .min(Integer::compareTo)
                            .orElse(Integer.MAX_VALUE);
            if (currentLevel > targetLevel)
                throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "角色权限不足，无法操作该用户");
        }
        for (Long id : request.getIds()) {
            UserDO user = getById(id);
            if (user != null) {
                privilegeGuard.changeUser(id, true);
                authVersions.bumpAuthVersion(id, "deleted");
                authInvalidator.users(List.of(id));
                userMapper.deleteById(id);
            }
        }
        userJobMapper.delete(
                Wrappers.<UserJobDO>lambdaQuery().in(UserJobDO::getUserId, request.getIds()));
        userRoleMapper.delete(
                Wrappers.<UserRoleDO>lambdaQuery().in(UserRoleDO::getUserId, request.getIds()));
    }

    private void checkLevel(Set<Long> roleIds) {
        if (roleService.getCurrentUserLevel() > roleService.findByRoleIds(roleIds))
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "角色权限不足，无法操作该用户");
    }

    private void checkUnique(String username, String email, String phone, Long currentId) {
        checkDuplicate(findByUsername(username), currentId, "username", username);
        checkDuplicate(
                userMapper.selectOne(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getEmail, email)),
                currentId,
                "email",
                email);
        checkDuplicate(
                userMapper.selectOne(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getPhone, phone)),
                currentId,
                "phone",
                phone);
    }

    private void checkDuplicate(UserDO duplicate, Long currentId, String field, String value) {
        if (duplicate != null && !duplicate.getId().equals(currentId))
            throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS, switch (field) {
                case "username" -> "用户名已存在";
                case "email" -> "邮箱已存在";
                case "phone" -> "手机号已存在";
                default -> CommonErrorCode.DATA_ALREADY_EXISTS.getMessage();
            });
    }

    private void insertRelations(Long userId, Set<IdReference> jobs, Set<IdReference> roles) {
        Set<Long> jobIds = ids(jobs);
        if (!jobIds.isEmpty()) {
            userJobMapper.insert(
                    jobIds.stream().map(id -> toUserJob(userId, id)).toList());
        }
        Set<Long> roleIds = ids(roles);
        if (!roleIds.isEmpty()) {
            userRoleMapper.insert(
                    roleIds.stream().map(id -> toUserRole(userId, id)).toList());
        }
    }

    private UserJobDO toUserJob(Long userId, Long jobId) {
        UserJobDO relation = new UserJobDO();
        relation.setUserId(userId);
        relation.setJobId(jobId);
        return relation;
    }

    private UserRoleDO toUserRole(Long userId, Long roleId) {
        UserRoleDO relation = new UserRoleDO();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private Set<Long> roleIds(Set<IdReference> refs) {
        return ids(refs);
    }

    private Set<Long> ids(Set<IdReference> refs) {
        return refs == null
                ? Collections.emptySet()
                : refs.stream().map(IdReference::getId).collect(Collectors.toSet());
    }

    @Override
    public UserDO findByName(String userName) {
        return findByUsername(userName);
    }

    private UserDO findByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, username));
    }

    @Override
    public UserDTO getLoginData(String userName) {
        UserDO user = findByUsername(userName);
        if (user == null) {
            return null;
        }
        UserDTO dto = assembler.toDto(user);
        if (user.getDeptId() != null)
            dto.setDept(deptAssembler.toDto(deptMapper.selectById(user.getDeptId())));
        dto.setRoles(Collections.emptySet());
        dto.setJobs(Collections.emptySet());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(UserResetPasswordRequest request, String rawPassword) {
        // 重置密码、设置强制改密及递增版本在同一事务内完成，提交后再清理缓存。
        privilegeGuard.lockAdministration();
        List<UserDO> users = userMapper.selectBatchIds(request.getIds());
        if (users.size() != request.getIds().size()) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        for (UserDO user : users) {
            checkUserAccess(user);
            checkLevel(roleMapper.findByUserId(user.getId()).stream()
                    .map(RoleDO::getId)
                    .collect(Collectors.toSet()));
        }
        users.forEach(
                user -> {
                    privilegeGuard.changeUser(user.getId(), false);
                    authVersions.bumpAuthVersion(user.getId(), "password_reset");
                    authInvalidator.users(List.of(user.getId()));
                });
        userMapper.update(Wrappers.<UserDO>lambdaUpdate()
                .in(UserDO::getId, request.getIds())
                .set(UserDO::getPassword, userCredentialService.encode(rawPassword))
                .set(UserDO::getMustChangePassword, true));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> updateAvatar(MultipartFile multipartFile) {
        FileUtil.checkSize(properties.getAvatarMaxSize(), multipartFile.getSize());
        String image = "gif jpg png jpeg";
        String fileType = FileUtil.getExtensionName(multipartFile.getOriginalFilename());
        if (fileType != null && !image.contains(fileType))
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "文件格式错误，仅支持 " + image);
        UserDO user = findByUsername(SecurityUtils.getCurrentUsername());
        String oldPath = user.getAvatarPath();
        File file;
        try {
            file =
                    FileUtil.upload(
                            multipartFile.getInputStream(),
                            multipartFile.getOriginalFilename(),
                            properties.getPath().getAvatar());
        } catch (IOException e) {
            throw new BusinessException(StorageErrorCode.OPERATION_FAILED, "文件上传失败，请稍后重试", e);
        }
        if (file == null) {
            throw new BusinessException(StorageErrorCode.OPERATION_FAILED, "文件上传失败，请稍后重试");
        }
        userMapper.update(Wrappers.<UserDO>lambdaUpdate().eq(UserDO::getId, user.getId())
                .set(UserDO::getAvatarPath, file.getPath())
                .set(UserDO::getAvatarName, file.getName()));
        if (StringUtils.isNotBlank(oldPath)) {
            FileUtil.del(oldPath);
        }
        flushCache(user.getUsername());
        return Map.of("avatar", file.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(String username, String email) {
        userMapper.update(Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getUsername, username)
                .set(UserDO::getEmail, email));
        flushCache(username);
    }

    @Override
    public void download(List<UserDTO> users, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserDTO user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("用户名", user.getUsername());
            row.put("角色", user.getRoles().stream().map(RoleDTO::getName).toList());
            row.put("部门", user.getDept() == null ? null : user.getDept().getName());
            row.put("岗位", user.getJobs().stream().map(JobDTO::getName).toList());
            row.put("邮箱", user.getEmail());
            row.put("状态", Boolean.TRUE.equals(user.getEnabled()) ? "启用" : "禁用");
            row.put("手机号", user.getPhone());
            row.put("修改密码时间", user.getPwdResetTime());
            row.put("创建日期", user.getCreateTime());
            rows.add(row);
        }
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=file.xlsx");
        ExcelUtil.write(rows, response.getOutputStream());
    }

    private void delCaches(Long id, String username) {
        authInvalidator.users(List.of(id));
    }

    private void flushCache(String username) {
        userCacheManager.cleanUserCache(username);
    }

    private void checkUserAccess(UserDO user) {
        if (user == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        dataService.checkUserAccess(user.getId(), user.getDeptId());
    }
}
