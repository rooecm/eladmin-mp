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
package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.framework.config.security.config.SecurityProperties;
import fun.rooe.admin.framework.config.security.security.TokenProvider;
import fun.rooe.admin.framework.config.security.service.OnlineSessionService;
import fun.rooe.admin.infrastructure.utils.PageUtil;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.security.converter.OnlineUserAssembler;
import fun.rooe.admin.modules.security.domain.dto.JwtUserDTO;
import fun.rooe.admin.modules.security.domain.dto.OnlineUserDTO;
import fun.rooe.admin.modules.security.domain.request.OnlineUserDeleteRequest;
import fun.rooe.admin.modules.security.domain.request.OnlineUserQueryRequest;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.EncryptUtils;
import fun.rooe.admin.utils.ExcelUtil;
import fun.rooe.admin.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Zheng Jie
 * @date 2019年10月26日21:56:27
 */
@Service
@Slf4j
@AllArgsConstructor
public class OnlineUserService implements OnlineSessionService {

    private final SecurityProperties properties;
    private final TokenProvider tokenProvider;
    private final RedisUtils redisUtils;
    private final OnlineUserAssembler assembler;

    /**
     * 保存在线用户信息
     *
     * @param jwtUserDto /
     * @param token /
     * @param request /
     */
    public void save(JwtUserDTO jwtUserDto, String token, HttpServletRequest request) {
        String dept =
                jwtUserDto.getUser().getDept() == null
                        ? null
                        : jwtUserDto.getUser().getDept().getName();
        String ip = StringUtils.getIp(request);
        String id = tokenProvider.getId(token);
        String browser = StringUtils.getBrowser(request);
        String address = StringUtils.getCityInfo(ip);
        OnlineUserDTO onlineUserDto = null;
        try {
            String key = EncryptUtils.desEncrypt(token);
            onlineUserDto =
                    assembler.toOnlineUser(
                            jwtUserDto, id, dept, browser, ip, address, key, LocalDateTime.now());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        String loginKey = tokenProvider.loginKey(token);
        redisUtils.set(
                loginKey,
                onlineUserDto,
                properties.getTokenValidityInSeconds(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 分页查询在线用户
     *
     * @param request 查询条件
     * @return /
     */
    public PageResult<OnlineUserDTO> getAll(OnlineUserQueryRequest request) {
        List<OnlineUserDTO> onlineUserDtos = getAll(request.getUsername());
        return PageUtil.toPage(
                PageUtil.paging(request.getPage(), request.getSize(), onlineUserDtos),
                onlineUserDtos.size());
    }

    /**
     * 查询全部数据，不分页
     *
     * @param username /
     * @return /
     */
    public List<OnlineUserDTO> getAll(String username) {
        String loginKey =
                properties.getOnlineKey() + (StringUtils.isBlank(username) ? "" : "*" + username);
        List<String> keys = redisUtils.scan(loginKey + "*");
        Collections.reverse(keys);
        List<OnlineUserDTO> onlineUserDtos = new ArrayList<>();
        for (String key : keys) {
            OnlineUserDTO onlineUser = redisUtils.get(key, OnlineUserDTO.class);
            if (onlineUser != null) {
                onlineUserDtos.add(onlineUser);
            }
        }
        onlineUserDtos.sort((o1, o2) -> o2.getLoginTime().compareTo(o1.getLoginTime()));
        return onlineUserDtos;
    }

    /**
     * 退出登录
     *
     * @param token /
     */
    public void logout(String token) {
        String loginKey = tokenProvider.loginKey(token);
        redisUtils.del(loginKey);
    }

    /**
     * 根据待强退的加密会话标识批量下线用户
     *
     * @param request 强退请求
     */
    public void logout(OnlineUserDeleteRequest request) {
        for (String key : request.getKeys()) {
            try {
                logout(EncryptUtils.desDecrypt(key));
            } catch (Exception e) {
                throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "无效的会话标识");
            }
        }
    }

    /**
     * 导出
     *
     * @param all /
     * @param response /
     * @throws IOException /
     */
    public void download(List<OnlineUserDTO> all, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (OnlineUserDTO user : all) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("用户名", user.getUserName());
            map.put("部门", user.getDept());
            map.put("登录IP", user.getIp());
            map.put("登录地点", user.getAddress());
            map.put("浏览器", user.getBrowser());
            map.put("登录日期", user.getLoginTime());
            list.add(map);
        }
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=file.xlsx");
        ExcelUtil.write(list, response.getOutputStream());
    }

    /**
     * 查询用户
     *
     * @param key /
     * @return /
     */
    public OnlineUserDTO getOne(String key) {
        return redisUtils.get(key, OnlineUserDTO.class);
    }

    @Override
    public boolean isOnline(String loginKey) {
        return getOne(loginKey) != null;
    }

    /**
     * 根据用户名强退用户
     *
     * @param username /
     */
    public void kickOutForUsername(String username) {
        String loginKey = properties.getOnlineKey() + username + "*";
        redisUtils.scanDel(loginKey);
    }
}
