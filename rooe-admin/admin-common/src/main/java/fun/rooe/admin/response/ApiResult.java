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

import java.io.Serializable;

/**
 * 统一 API 响应对象。
 *
 * @param status    HTTP 状态码
 * @param code    业务响应码，"0" 表示成功
 * @param message 响应消息
 * @param data    响应数据
 * @param timestamp 错误响应时间戳
 * @param <T>     数据类型
 */
public record ApiResult<T>(Integer status, String code, String message, T data, Long timestamp) implements Serializable {

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, "0", "success", data, null);
    }

    public static ApiResult<Void> success() {
        return new ApiResult<>(200, "0", "success", null, null);
    }

    public static <T> ApiResult<T> failure(String code, String message) {
        return new ApiResult<>(400, code, message, null, null);
    }

    public static ApiResult<Void> error(String message) {
        return error(400, message);
    }

    public static ApiResult<Void> error(Integer status, String message) {
        return new ApiResult<>(status, String.valueOf(status), message, null, System.currentTimeMillis());
    }
}
