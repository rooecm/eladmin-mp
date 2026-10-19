package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.enums.CodeEnum;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.framework.config.properties.RsaProperties;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.modules.system.domain.dto.UserDTO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.error.UserErrorCode;
import fun.rooe.admin.modules.system.service.UserCredentialService;
import fun.rooe.admin.modules.system.service.UserService;
import fun.rooe.admin.modules.system.service.VerifyService;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.RsaUtils;
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
  private final UserCredentialService userCredentialService;
  private final fun.rooe.admin.framework.config.security.config.AuthProperties authProperties;
    private final UserService userService;
    private final VerifyService verificationCodeService;

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

    @Log("修改邮箱")
    @Operation(summary = "修改邮箱")
    @PostMapping("/updateEmail/{code}")
    public void updateUserEmail(
            @PathVariable String code, @Valid @RequestBody UserEmailUpdateRequest request)
            throws Exception {
      String password =
              RsaUtils.decryptByPrivateKey(RsaProperties.privateKey, request.getPassword());
      String username = SecurityUtils.getCurrentUsername();
      if (!userCredentialService.matches(username, password))
          throw new BusinessException(UserErrorCode.PASSWORD_INCORRECT);
      verificationCodeService.validated(
              CodeEnum.EMAIL_RESET_EMAIL_CODE.getKey() + request.getEmail(), code);
      userService.updateEmail(username, request.getEmail());
    }
}
