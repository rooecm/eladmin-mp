package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.modules.system.domain.dto.UserDTO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.service.UserService;
import fun.rooe.admin.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP endpoints for user administration and self-service account operations.
 */
@Tag(name = "系统：用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final AuthProperties authProperties;
    private final UserService userService;

    @Operation(summary = "导出用户数据")
    @GetMapping("/download")
    @PreAuthorize("@perm.has('user:list')")
    public void exportUser(HttpServletResponse response, @Valid UserQueryRequest request)
            throws IOException {
      userService.download(userService.queryList(request), response);
    }

    @Operation(summary = "查询用户")
    @GetMapping
    @PreAuthorize("@perm.has('user:list')")
    public PageResult<UserDTO> queryUser(@Valid UserQueryRequest request) {
      return userService.queryAll(request);
    }

    @Log("新增用户")
    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("@perm.has('user:add')")
    public void createUser(@Valid @RequestBody UserCreateRequest request) {
      userService.create(request);
    }

    @Log("修改用户")
    @Operation(summary = "修改用户")
    @PutMapping
    @PreAuthorize("@perm.has('user:edit')")
    public void updateUser(@Valid @RequestBody UserUpdateRequest request) {
      userService.update(request);
    }

    @Log("修改用户：个人中心")
    @Operation(summary = "修改用户：个人中心")
    @PutMapping("center")
    public void centerUser(@Valid @RequestBody UserCenterUpdateRequest request) {
      userService.updateCenter(request);
    }

    @Log("删除用户")
    @Operation(summary = "删除用户")
    @DeleteMapping
    @PreAuthorize("@perm.has('user:del')")
    public void deleteUser(@Valid @RequestBody UserDeleteRequest request) {
      userService.delete(request);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/resetPwd")
    @PreAuthorize("@perm.has('user:edit')")
    public void resetPwd(@Valid @RequestBody UserResetPasswordRequest request) {
      userService.resetPwd(request, authProperties.getInitialPassword());
    }

    @Operation(summary = "修改头像")
    @PostMapping("/updateAvatar")
    public Map<String, String> updateUserAvatar(@RequestParam MultipartFile avatar) {
        return userService.updateAvatar(avatar);
    }
}
