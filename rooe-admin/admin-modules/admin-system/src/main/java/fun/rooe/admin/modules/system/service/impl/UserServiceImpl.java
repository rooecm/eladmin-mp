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
import fun.rooe.admin.modules.security.service.OnlineUserService;
import fun.rooe.admin.modules.security.service.UserCacheManager;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.converter.JobAssembler;
import fun.rooe.admin.modules.system.converter.RoleAssembler;
import fun.rooe.admin.modules.system.converter.UserAssembler;
import fun.rooe.admin.modules.system.domain.Role;
import fun.rooe.admin.modules.system.domain.User;
import fun.rooe.admin.modules.system.domain.dto.*;
import fun.rooe.admin.modules.system.mapper.*;
import fun.rooe.admin.modules.system.service.DataService;
import fun.rooe.admin.modules.system.service.DeptService;
import fun.rooe.admin.modules.system.service.RoleService;
import fun.rooe.admin.modules.system.service.UserService;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.ExcelUtil;
import fun.rooe.admin.utils.FileUtil;
import fun.rooe.admin.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    private final UserAssembler assembler;
    private final DeptAssembler deptAssembler;
    private final RoleAssembler roleAssembler;
    private final JobAssembler jobAssembler;

    @Override
    public PageResult<UserDto> queryAll(UserQueryRequest request) {
        Scope scope = resolveScope(request);
        if (scope.empty()) return new PageResult<>(new ArrayList<>(), 0);
        Page<User> page = new Page<>(request.getPage(), request.getSize());
        userMapper.selectPage(page, wrapper(request, scope.ids()));
        return new PageResult<>(assemble(page.getRecords()), page.getTotal());
    }

    @Override
    public List<UserDto> queryList(UserQueryRequest request) {
        Scope scope = resolveScope(request);
        return scope.empty()
                ? new ArrayList<>()
                : assemble(userMapper.selectList(wrapper(request, scope.ids())));
    }

    /**
     * Intersects an optional department filter with the current user's data scope. An empty allowed
     * set means unrestricted access, matching {@link DataService}'s contract.
     */
    private Scope resolveScope(UserQueryRequest request) {
        Set<Long> requested = new LinkedHashSet<>();
        if (request.getDeptId() != null) {
            requested.add(request.getDeptId());
            requested.addAll(
                    deptService.getDeptChildren(deptService.findByPid(request.getDeptId())));
        }
        User current = findByName(SecurityUtils.getCurrentUsername());
        if (current == null) return new Scope(Collections.emptySet(), true);
        Set<Long> allowed =
                new LinkedHashSet<>(dataService.getDeptIds(current.getId(), current.getDeptId()));
        if (!requested.isEmpty() && !allowed.isEmpty()) {
            requested.retainAll(allowed);
            return new Scope(requested, requested.isEmpty());
        }
        return new Scope(requested.isEmpty() ? allowed : requested, false);
    }

    private LambdaQueryWrapper<User> wrapper(UserQueryRequest request, Set<Long> deptIds) {
        LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(request.getId() != null, User::getId, request.getId())
                .eq(request.getEnabled() != null, User::getEnabled, request.getEnabled())
                .in(!deptIds.isEmpty(), User::getDeptId, deptIds)
                .and(
                        StringUtils.isNotBlank(request.getBlurry()),
                        query ->
                                query.like(User::getUsername, request.getBlurry())
                                        .or()
                                        .like(User::getNickName, request.getBlurry())
                                        .or()
                                        .like(User::getEmail, request.getBlurry()))
                .orderByDesc(User::getId);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2)
            wrapper.between(
                    User::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        return wrapper;
    }

    private List<UserDto> assemble(List<User> users) {
        List<UserDto> result = assembler.toDtos(users);
        for (UserDto dto : result) {
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
        checkLevel(roleIds(request.getRoles()));
        checkUnique(request.getUsername(), request.getEmail(), request.getPhone(), null);
        User user = assembler.toEntity(request);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setIsAdmin(false);
        save(user);
        insertRelations(user.getId(), request.getJobs(), request.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateRequest request) {
        checkLevel(roleIds(request.getRoles()));
        User user = getById(request.getId());
        checkUnique(request.getUsername(), request.getEmail(), request.getPhone(), user.getId());
        Set<Long> oldRoles =
                roleMapper.findByUserId(user.getId()).stream()
                        .map(Role::getId)
                        .collect(Collectors.toSet());
        Set<Long> newRoles = roleIds(request.getRoles());
        if (!oldRoles.equals(newRoles)) clearAuthorizationCaches(user.getId());
        if (!Objects.equals(user.getDeptId(), request.getDept().getId()))
            redisUtils.del(CacheKey.DATA_USER + user.getId());
        if (!Boolean.TRUE.equals(request.getEnabled()))
            onlineUserService.kickOutForUsername(request.getUsername());
        assembler.updateEntity(request, user);
        updateById(user);
        delCaches(user.getId(), user.getUsername());
        // Relation rows represent the complete request state, so replace them atomically.
        userJobMapper.deleteByUserId(user.getId());
        userRoleMapper.deleteByUserId(user.getId());
        insertRelations(user.getId(), request.getJobs(), request.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCenter(UserCenterUpdateRequest request) {
        if (!request.getId().equals(SecurityUtils.getCurrentUserId()))
            throw new BadRequestException("不能修改他人资料");
        User user = getById(request.getId());
        User duplicate = userMapper.findByPhone(request.getPhone());
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
        userJobMapper.deleteByUserIds(request.getIds());
        userRoleMapper.deleteByUserIds(request.getIds());
    }

    private void checkLevel(Set<Long> roleIds) {
        if (roleService.getCurrentUserLevel() > roleService.findByRoleIds(roleIds))
            throw new BadRequestException("角色权限不足");
    }

    private void checkUnique(String username, String email, String phone, Long currentId) {
        checkDuplicate(userMapper.findByUsername(username), currentId, "username", username);
        checkDuplicate(userMapper.findByEmail(email), currentId, "email", email);
        checkDuplicate(userMapper.findByPhone(phone), currentId, "phone", phone);
    }

    private void checkDuplicate(User duplicate, Long currentId, String field, String value) {
        if (duplicate != null && !duplicate.getId().equals(currentId))
            throw new EntityExistException(User.class, field, value);
    }

    private void insertRelations(Long userId, Set<IdReference> jobs, Set<IdReference> roles) {
        Set<Long> jobIds = ids(jobs);
        if (!jobIds.isEmpty()) userJobMapper.insertData(userId, jobIds);
        Set<Long> roleIds = ids(roles);
        if (!roleIds.isEmpty()) userRoleMapper.insertData(userId, roleIds);
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
        return userMapper.findByUsername(userName);
    }

    @Override
    public UserLoginDto getLoginData(String userName) {
        User user = userMapper.findByUsername(userName);
        if (user == null) return null;
        UserLoginDto dto = assembler.toLoginDto(user);
        if (user.getDeptId() != null)
            dto.setDept(deptAssembler.toDto(deptMapper.selectById(user.getDeptId())));
        dto.setRoles(Collections.emptySet());
        dto.setJobs(Collections.emptySet());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePass(String username, String pass) {
        userMapper.updatePass(username, pass, new Date());
        flushCache(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(UserResetPasswordRequest request, String pwd) {
        List<User> users = userMapper.selectBatchIds(request.getIds());
        users.forEach(
                user -> {
                    flushCache(user.getUsername());
                    onlineUserService.kickOutForUsername(user.getUsername());
                });
        userMapper.resetPwd(request.getIds(), pwd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> updateAvatar(MultipartFile multipartFile) {
        FileUtil.checkSize(properties.getAvatarMaxSize(), multipartFile.getSize());
        String image = "gif jpg png jpeg";
        String fileType = FileUtil.getExtensionName(multipartFile.getOriginalFilename());
        if (fileType != null && !image.contains(fileType))
            throw new BadRequestException("文件格式错误，仅支持 " + image);
        User user = userMapper.findByUsername(SecurityUtils.getCurrentUsername());
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
        userMapper.updateEmail(username, email);
        flushCache(username);
    }

    @Override
    public void download(List<UserDto> users, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserDto user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("用户名", user.getUsername());
            row.put("角色", user.getRoles().stream().map(RoleDto::getName).toList());
            row.put("部门", user.getDept() == null ? null : user.getDept().getName());
            row.put("岗位", user.getJobs().stream().map(JobDto::getName).toList());
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

    /**
     * Resolved department IDs plus an explicit marker for an empty intersection.
     */
    private record Scope(Set<Long> ids, boolean empty) {}
}
