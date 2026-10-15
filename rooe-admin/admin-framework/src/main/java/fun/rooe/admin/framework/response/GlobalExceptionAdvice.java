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
package fun.rooe.admin.framework.response;

import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.exception.EntityExistException;
import fun.rooe.admin.exception.EntityNotFoundException;
import fun.rooe.admin.response.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Zheng Jie
 * @date 2018-11-23
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    /**
     * 处理所有不可知的异常
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResult<Void>> handleException(Throwable e) {
        log.error("系统异常", e);
        return buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    /**
     * BadCredentialsException
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResult<Void>> badCredentialsException(BadCredentialsException e) {
        String message = "坏的凭证".equals(e.getMessage()) ? "用户名或密码不正确" : e.getMessage();
        log.error(message);
        return buildResponseEntity(HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * 处理自定义异常
     */
    @ExceptionHandler(value = BadRequestException.class)
    public ResponseEntity<ApiResult<Void>> badRequestException(BadRequestException e) {
        log.error(e.getMessage());
        return buildResponseEntity(HttpStatus.valueOf(e.getStatus()), e.getMessage());
    }

    /**
     * 处理 EntityExist
     */
    @ExceptionHandler(value = EntityExistException.class)
    public ResponseEntity<ApiResult<Void>> entityExistException(EntityExistException e) {
        log.error(e.getMessage());
        return buildResponseEntity(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * 处理 EntityNotFound
     */
    @ExceptionHandler(value = EntityNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> entityNotFoundException(EntityNotFoundException e) {
        log.error(e.getMessage());
        return buildResponseEntity(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 处理所有接口数据验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        ObjectError objectError = e.getBindingResult().getAllErrors().get(0);
        String message = objectError.getDefaultMessage();
        if (objectError instanceof FieldError) {
            message = ((FieldError) objectError).getField() + ": " + message;
        }
        log.error(message);
        return buildResponseEntity(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 统一返回
     */
    private ResponseEntity<ApiResult<Void>> buildResponseEntity(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResult.error(status.value(), message));
    }
}
