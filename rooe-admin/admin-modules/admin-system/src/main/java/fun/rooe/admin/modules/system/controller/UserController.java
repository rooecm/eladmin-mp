package fun.rooe.admin.modules.system.controller;

import fun.rooe.admin.enums.CodeEnum;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.framework.config.properties.RsaProperties;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.modules.system.domain.dto.*;
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
    private final UserService userService;
    private final VerifyService verificationCodeService;

    @Operation(summary = "导出用户数据")
    @GetMapping("/download")
    @PreAuthorize("@el.check('user:list')")
    public void exportUser(HttpServletResponse response, @Valid UserQueryRequest request)
            throws IOException {
      userService.download(userService.queryList(request), response);
    }

    @Operation(summary = "查询用户")
    @GetMapping
    @PreAuthorize("@el.check('user:list')")
    public PageResult<UserDto> queryUser(@Valid UserQueryRequest request) {
      return userService.queryAll(request);
    }

    @Log("新增用户")
    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("@el.check('user:add')")
    public void createUser(@Valid @RequestBody UserCreateRequest request) {
      userService.create(request);
    }

    @Log("修改用户")
    @Operation(summary = "修改用户")
    @PutMapping
    @PreAuthorize("@el.check('user:edit')")
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
    @PreAuthorize("@el.check('user:del')")
    public void deleteUser(@Valid @RequestBody UserDeleteRequest request) {
      userService.delete(request);
    }

    @Operation(summary = "修改密码")
    @PostMapping("/updatePass")
    public void updateUserPass(@Valid @RequestBody UserPasswordUpdateRequest request)
            throws Exception {
      String oldPass =
              RsaUtils.decryptByPrivateKey(RsaProperties.privateKey, request.getOldPass());
      String newPass =
              RsaUtils.decryptByPrivateKey(RsaProperties.privateKey, request.getNewPass());
      String username = SecurityUtils.getCurrentUsername();
      if (!userCredentialService.matches(username, oldPass))
        throw new BadRequestException("旧密码错误");
      if (userCredentialService.matches(username, newPass))
            throw new BadRequestException("新密码不能与旧密码相同");
      userService.updatePass(username, newPass);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/resetPwd")
    public void resetPwd(@Valid @RequestBody UserResetPasswordRequest request) {
      userService.resetPwd(request, "123456");
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
            throw new BadRequestException("密码错误");
      verificationCodeService.validated(
              CodeEnum.EMAIL_RESET_EMAIL_CODE.getKey() + request.getEmail(), code);
      userService.updateEmail(username, request.getEmail());
    }
}
