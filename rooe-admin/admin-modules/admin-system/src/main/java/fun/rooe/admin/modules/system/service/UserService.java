package fun.rooe.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.rooe.admin.modules.system.domain.dto.UserDTO;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDTO;
import fun.rooe.admin.modules.system.domain.entity.User;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.response.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface UserService extends IService<User> {
    User findById(long id);

    User findByName(String userName);

    UserLoginDTO getLoginData(String userName);

    PageResult<UserDTO> queryAll(UserQueryRequest request);

    List<UserDTO> queryList(UserQueryRequest request);

    void create(UserCreateRequest request);

    void update(UserUpdateRequest request);

    void updateCenter(UserCenterUpdateRequest request);

    void delete(UserDeleteRequest request);

    void resetPwd(UserResetPasswordRequest request, String rawPassword);

    void updatePass(String username, String rawPassword);

    Map<String, String> updateAvatar(MultipartFile file);

    void updateEmail(String username, String email);

    void download(List<UserDTO> users, HttpServletResponse response) throws IOException;
}
