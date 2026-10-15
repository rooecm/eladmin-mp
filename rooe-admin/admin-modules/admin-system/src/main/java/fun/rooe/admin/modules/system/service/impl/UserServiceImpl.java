package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.exception.EntityExistException;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.config.properties.FileProperties;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.security.service.OnlineUserService;
import fun.rooe.admin.modules.security.service.UserCacheManager;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.converter.JobAssembler;
import fun.rooe.admin.modules.system.converter.RoleAssembler;
import fun.rooe.admin.modules.system.converter.UserAssembler;
import fun.rooe.admin.modules.system.domain.dto.JobDTO;
import fun.rooe.admin.modules.system.domain.dto.RoleDTO;
import fun.rooe.admin.modules.system.domain.dto.UserDTO;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDTO;
import fun.rooe.admin.modules.system.domain.entity.Role;
import fun.rooe.admin.modules.system.domain.entity.User;
import fun.rooe.admin.modules.system.domain.entity.UserJob;
import fun.rooe.admin.modules.system.domain.entity.UserRole;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Coordinates user persistence, authorization scope, associations, and cache invalidation.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
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
    private final RedisUtils redisUtils;
    private final UserCacheManager userCacheManager;
    private final OnlineUserService onlineUserService;
    private final UserCredentialService userCredentialService;
    private final UserAssembler assembler;
    private final DeptAssembler deptAssembler;
    private final RoleAssembler roleAssembler;
    private final JobAssembler jobAssembler;

    @Override
    public PageResult<UserDTO> queryAll(UserQueryRequest request) {
        UserDataScope dataScope = dataService.getCurrentDataScope();
        if (dataScope.isNone()) return new PageResult<>(new ArrayList<>(), 0);
        Page<User> page = new Page<>(request.getPage(), request.getSize());
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

    private LambdaQueryWrapper<User> wrapper(
            UserQueryRequest request, Set<Long> requestedDeptIds, UserDataScope dataScope) {
        LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(request.getId() != null, User::getId, request.getId())
                .eq(request.getEnabled() != null, User::getEnabled, request.getEnabled())
                .in(!requestedDeptIds.isEmpty(), User::getDeptId, requestedDeptIds)
                .and(
                        StringUtils.isNotBlank(request.getBlurry()),
                        query ->
                                query.like(User::getUsername, request.getBlurry())
                                        .or()
                                        .like(User::getNickName, request.getBlurry())
                                        .or()
                                        .like(User::getEmail, request.getBlurry()))
                .orderByDesc(User::getId);
        if (!dataScope.isAll()) {
            wrapper.and(permission -> {
                if (!dataScope.deptIds().isEmpty()) {
                    permission.in(User::getDeptId, dataScope.deptIds());
                }
                if (dataScope.selfUserId() != null) {
                    if (!dataScope.deptIds().isEmpty()) {
                        permission.or();
                    }
                    permission.eq(User::getId, dataScope.selfUserId());
                }
            });
        }
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2)
            wrapper.between(
                    User::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        return wrapper;
    }

    private List<UserDTO> assemble(List<User> users) {
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
    public User findById(long id) {
        String key = CacheKey.USER_ID + id;
        User user = redisUtils.get(key, User.class);
        if (user == null) {
            user = getById(id);
            redisUtils.set(key, user, 1, TimeUnit.DAYS);
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserCreateRequest request) {
        Long deptId = request.getDept().getId();
        Set<Long> roleIds = roleIds(request.getRoles());
        dataService.checkDeptAccess(deptId);
        checkLevel(roleIds);
        roleService.checkDataScopes(roleIds, deptId);
        checkUnique(request.getUsername(), request.getEmail(), request.getPhone(), null);
        User user = assembler.toEntity(request);
        user.setPassword(userCredentialService.encode("123456"));
        user.setIsAdmin(false);
        save(user);
        insertRelations(user.getId(), request.getJobs(), request.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateRequest request) {
        User user = getById(request.getId());
        checkUserAccess(user);
        checkLevel(roleMapper.findByUserId(user.getId()).stream()
                .map(Role::getId)
                .collect(Collectors.toSet()));
        Long newDeptId = request.getDept().getId();
        if (!Objects.equals(user.getDeptId(), newDeptId)) {
            dataService.checkDeptAccess(newDeptId);
        }
        Set<Long> newRoles = roleIds(request.getRoles());
        checkLevel(newRoles);
        roleService.checkDataScopes(newRoles, newDeptId);
        checkUnique(request.getUsername(), request.getEmail(), request.getPhone(), user.getId());
        Set<Long> oldRoles =
                roleMapper.findByUserId(user.getId()).stream()
                        .map(Role::getId)
                        .collect(Collectors.toSet());
        if (!oldRoles.equals(newRoles)) clearAuthorizationCaches(user.getId());
        if (!Objects.equals(user.getDeptId(), request.getDept().getId()))
            redisUtils.del(CacheKey.DATA_USER + user.getId());
        if (!Boolean.TRUE.equals(request.getEnabled()))
            onlineUserService.kickOutForUsername(request.getUsername());
        assembler.updateEntity(request, user);
        updateById(user);
        delCaches(user.getId(), user.getUsername());
        // Relation rows represent the complete request state, so replace them atomically.
        userJobMapper.delete(
                Wrappers.<UserJob>lambdaQuery().eq(UserJob::getUserId, user.getId()));
        userRoleMapper.delete(
                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, user.getId()));
        insertRelations(user.getId(), request.getJobs(), request.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCenter(UserCenterUpdateRequest request) {
        if (!request.getId().equals(SecurityUtils.getCurrentUserId()))
            throw new BadRequestException("不能修改他人资料");
        User user = getById(request.getId());
        User duplicate =
                userMapper.selectOne(
                        Wrappers.<User>lambdaQuery().eq(User::getPhone, request.getPhone()));
        if (duplicate != null && !user.getId().equals(duplicate.getId()))
            throw new EntityExistException(User.class, "phone", request.getPhone());
        assembler.updateCenter(request, user);
        updateById(user);
        delCaches(user.getId(), user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UserDeleteRequest request) {
        int currentLevel = roleService.getCurrentUserLevel();
        for (Long id : request.getIds()) {
            checkUserAccess(getById(id));
            int targetLevel =
                    roleService.findByUsersId(id).stream()
                            .map(Role::getLevel)
                            .min(Integer::compareTo)
                            .orElse(Integer.MAX_VALUE);
            if (currentLevel > targetLevel)
                throw new BadRequestException("角色权限不足，不能删除：" + findById(id).getUsername());
        }
        for (Long id : request.getIds()) {
            User user = getById(id);
            if (user != null) delCaches(user.getId(), user.getUsername());
        }
        userMapper.deleteBatchIds(request.getIds());
        userJobMapper.delete(
                Wrappers.<UserJob>lambdaQuery().in(UserJob::getUserId, request.getIds()));
        userRoleMapper.delete(
                Wrappers.<UserRole>lambdaQuery().in(UserRole::getUserId, request.getIds()));
    }

    private void checkLevel(Set<Long> roleIds) {
        if (roleService.getCurrentUserLevel() > roleService.findByRoleIds(roleIds))
            throw new BadRequestException("角色权限不足");
    }

    private void checkUnique(String username, String email, String phone, Long currentId) {
        checkDuplicate(findByUsername(username), currentId, "username", username);
        checkDuplicate(
                userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)),
                currentId,
                "email",
                email);
        checkDuplicate(
                userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone)),
                currentId,
                "phone",
                phone);
    }

    private void checkDuplicate(User duplicate, Long currentId, String field, String value) {
        if (duplicate != null && !duplicate.getId().equals(currentId))
            throw new EntityExistException(User.class, field, value);
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

    private UserJob toUserJob(Long userId, Long jobId) {
        UserJob relation = new UserJob();
        relation.setUserId(userId);
        relation.setJobId(jobId);
        return relation;
    }

    private UserRole toUserRole(Long userId, Long roleId) {
        UserRole relation = new UserRole();
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
    public User findByName(String userName) {
        return findByUsername(userName);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    @Override
    public UserLoginDTO getLoginData(String userName) {
        User user = findByUsername(userName);
        if (user == null) return null;
        UserLoginDTO dto = assembler.toLoginDto(user);
        if (user.getDeptId() != null)
            dto.setDept(deptAssembler.toDto(deptMapper.selectById(user.getDeptId())));
        dto.setRoles(Collections.emptySet());
        dto.setJobs(Collections.emptySet());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePass(String username, String rawPassword) {
        userMapper.update(Wrappers.<User>lambdaUpdate()
                .eq(User::getUsername, username)
                .set(User::getPassword, userCredentialService.encode(rawPassword))
                .set(User::getPwdResetTime, new Date()));
        flushCache(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(UserResetPasswordRequest request, String rawPassword) {
        List<User> users = userMapper.selectBatchIds(request.getIds());
        if (users.size() != request.getIds().size()) {
            throw new BadRequestException("User does not exist");
        }
        for (User user : users) {
            checkUserAccess(user);
            checkLevel(roleMapper.findByUserId(user.getId()).stream()
                    .map(Role::getId)
                    .collect(Collectors.toSet()));
        }
        users.forEach(
                user -> {
                    flushCache(user.getUsername());
                    onlineUserService.kickOutForUsername(user.getUsername());
                });
        userMapper.update(Wrappers.<User>lambdaUpdate()
                .in(User::getId, request.getIds())
                .set(User::getPassword, userCredentialService.encode(rawPassword)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> updateAvatar(MultipartFile multipartFile) {
        FileUtil.checkSize(properties.getAvatarMaxSize(), multipartFile.getSize());
        String image = "gif jpg png jpeg";
        String fileType = FileUtil.getExtensionName(multipartFile.getOriginalFilename());
        if (fileType != null && !image.contains(fileType))
            throw new BadRequestException("文件格式错误，仅支持 " + image);
        User user = findByUsername(SecurityUtils.getCurrentUsername());
        String oldPath = user.getAvatarPath();
        File file;
        try {
            file =
                    FileUtil.upload(
                            multipartFile.getInputStream(),
                            multipartFile.getOriginalFilename(),
                            properties.getPath().getAvatar());
        } catch (IOException e) {
            throw new BadRequestException("头像上传失败");
        }
        user.setAvatarPath(Objects.requireNonNull(file).getPath());
        user.setAvatarName(file.getName());
        updateById(user);
        if (StringUtils.isNotBlank(oldPath)) FileUtil.del(oldPath);
        flushCache(user.getUsername());
        return Map.of("avatar", file.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(String username, String email) {
        userMapper.update(Wrappers.<User>lambdaUpdate()
                .eq(User::getUsername, username)
                .set(User::getEmail, email));
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

    private void clearAuthorizationCaches(Long id) {
        redisUtils.del(CacheKey.DATA_USER + id);
        redisUtils.del(CacheKey.MENU_USER + id);
        redisUtils.del(CacheKey.ROLE_AUTH + id);
        redisUtils.del(CacheKey.ROLE_USER + id);
    }

    private void delCaches(Long id, String username) {
        redisUtils.del(CacheKey.USER_ID + id);
        flushCache(username);
    }

    private void flushCache(String username) {
        userCacheManager.cleanUserCache(username);
    }

    private void checkUserAccess(User user) {
        if (user == null) {
            throw new BadRequestException("User does not exist");
        }
        dataService.checkUserAccess(user.getId(), user.getDeptId());
    }
}
