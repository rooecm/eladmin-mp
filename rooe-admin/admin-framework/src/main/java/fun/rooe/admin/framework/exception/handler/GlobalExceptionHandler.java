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
package fun.rooe.admin.framework.exception.handler;

import lombok.extern.slf4j.Slf4j;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.exception.EntityExistException;
import fun.rooe.admin.exception.EntityNotFoundException;
import fun.rooe.admin.response.ApiResult;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.springframework.http.HttpStatus.*;

/**
 * @author Zheng Jie
 * @date 2018-11-23
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有不可知的异常
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResult<Void>> handleException(Throwable e){
        // 打印堆栈信息
        log.error(ExceptionUtils.getStackTrace(e));
        return buildResponseEntity(ApiResult.error(e.getMessage()));
    }

    /**
     * BadCredentialsException
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResult<Void>> badCredentialsException(BadCredentialsException e){
        // 打印堆栈信息
        String message = "坏的凭证".equals(e.getMessage()) ? "用户名或密码不正确" : e.getMessage();
        log.error(message);
        return buildResponseEntity(ApiResult.error(message));
    }

    /**
     * 处理自定义异常
     */
	@ExceptionHandler(value = BadRequestException.class)
	public ResponseEntity<ApiResult<Void>> badRequestException(BadRequestException e) {
        // 打印堆栈信息
        log.error(ExceptionUtils.getStackTrace(e));
        return buildResponseEntity(ApiResult.error(e.getStatus(),e.getMessage()));
	}

    /**
     * 处理 EntityExist
     */
    @ExceptionHandler(value = EntityExistException.class)
    public ResponseEntity<ApiResult<Void>> entityExistException(EntityExistException e) {
        // 打印堆栈信息
        log.error(ExceptionUtils.getStackTrace(e));
        return buildResponseEntity(ApiResult.error(e.getMessage()));
    }

    /**
     * 处理 EntityNotFound
     */
    @ExceptionHandler(value = EntityNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> entityNotFoundException(EntityNotFoundException e) {
        // 打印堆栈信息
        log.error(ExceptionUtils.getStackTrace(e));
        return buildResponseEntity(ApiResult.error(NOT_FOUND.value(),e.getMessage()));
    }

    /**
     * 处理所有接口数据验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
        // 打印堆栈信息
        log.error(ExceptionUtils.getStackTrace(e));
        ObjectError objectError = e.getBindingResult().getAllErrors().get(0);
        String message = objectError.getDefaultMessage();
        if (objectError instanceof FieldError) {
            message = ((FieldError) objectError).getField() + ": " + message;
        }
        return buildResponseEntity(ApiResult.error(message));
    }

    /**
     * 统一返回
     */
    private ResponseEntity<ApiResult<Void>> buildResponseEntity(ApiResult<Void> apiResult) {
        return new ResponseEntity<>(apiResult, HttpStatus.valueOf(apiResult.status()));
    }
}
