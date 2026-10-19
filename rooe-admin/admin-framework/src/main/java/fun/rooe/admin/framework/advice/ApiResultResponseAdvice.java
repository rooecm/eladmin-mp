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
package fun.rooe.admin.framework.advice;

import fun.rooe.admin.response.ApiResult;
import jakarta.servlet.ServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 统一封装 Controller 的成功响应。
 */
@RestControllerAdvice(basePackages = "fun.rooe.admin")
public class ApiResultResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Method method = returnType.getMethod();
        var status = returnType.getMethodAnnotation(ResponseStatus.class);
        if (status != null && status.code() == HttpStatus.NO_CONTENT) {
            return false;
        }
        // 仅直接写出响应的 void 方法跳过包装；返回 DTO 时仍可通过 response 设置响应头。
        if (method != null && method.getReturnType() == void.class && Arrays.stream(method.getParameterTypes())
                .anyMatch(ServletResponse.class::isAssignableFrom)) {
            return false;
        }
        return !ApiResult.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResult<?>) {
            return body;
        }
        return body == null ? ApiResult.success() : ApiResult.success(body);
    }
}
