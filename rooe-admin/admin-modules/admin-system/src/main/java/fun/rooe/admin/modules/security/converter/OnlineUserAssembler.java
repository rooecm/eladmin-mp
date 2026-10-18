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
package fun.rooe.admin.modules.security.converter;

import fun.rooe.admin.modules.security.domain.dto.JwtUserDTO;
import fun.rooe.admin.modules.security.domain.dto.OnlineUserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;

/**
 * Converts authenticated principals and HTTP request metadata into online-session
 * response objects.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OnlineUserAssembler {

    /**
     * Builds the cached online-session payload describing a logged-in principal.
     */
    @Mapping(target = "uid", source = "id")
    @Mapping(target = "userName", expression = "java(jwtUserDto.getUsername())")
    @Mapping(target = "nickName", source = "jwtUserDto.user.nickName")
    @Mapping(target = "dept", expression = "java(deptName)")
    @Mapping(target = "browser", source = "browser")
    @Mapping(target = "ip", source = "ip")
    @Mapping(target = "address", source = "address")
    @Mapping(target = "key", source = "key")
    @Mapping(target = "loginTime", source = "loginTime")
    OnlineUserDTO toOnlineUser(
            JwtUserDTO jwtUserDto,
            String id,
            String deptName,
            String browser,
            String ip,
            String address,
            String key,
            LocalDateTime loginTime);
}
