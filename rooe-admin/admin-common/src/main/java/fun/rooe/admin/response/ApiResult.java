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
package fun.rooe.admin.response;

import fun.rooe.admin.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一 API 响应对象。
 *
 * @param <T> 数据类型
 */
@Getter
@AllArgsConstructor
public class ApiResult<T> implements Serializable {

    /**
     * 业务响应码，"0" 表示成功
     */
    private final String code;

    /**
     * 响应消息
     */
    private final String message;

    /**
     * 响应数据
     */
    private final T data;

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>("0", "success", data);
    }

    public static ApiResult<Void> success() {
        return new ApiResult<>("0", "success", null);
    }

    public static <T> ApiResult<T> failure(String code, String message) {
        return new ApiResult<>(code, message, null);
    }

    public static <T> ApiResult<T> failure(ErrorCode errorCode) {
        return failure(errorCode.getCode(), errorCode.getMessage());
    }

}
