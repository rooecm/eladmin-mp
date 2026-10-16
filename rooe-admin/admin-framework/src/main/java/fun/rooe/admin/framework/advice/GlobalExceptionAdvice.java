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

import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.error.ErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.response.ApiResult;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> businessException(BusinessException e) {
        log.warn("Business failure", e);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResult.failure(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler({AuthenticationException.class, JwtException.class})
    public ResponseEntity<ApiResult<Void>> authenticationException(RuntimeException e) {
        if (e instanceof AuthenticationServiceException) {
            // The DAO provider can wrap known rejections thrown while loading user details.
            if (e.getCause() instanceof AuthenticationException cause
                    && !(cause instanceof AuthenticationServiceException)) {
                e = cause;
            } else {
                return handleException(e);
            }
        }
        if (e instanceof DisabledException) {
            return failure(HttpStatus.FORBIDDEN, AuthErrorCode.ACCOUNT_DISABLED);
        }
        ErrorCode code = e instanceof BadCredentialsException || e instanceof UsernameNotFoundException
                ? AuthErrorCode.BAD_CREDENTIALS : AuthErrorCode.SESSION_INVALID;
        return failure(HttpStatus.UNAUTHORIZED, code);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> accessDeniedException(AccessDeniedException e) {
        return failure(HttpStatus.FORBIDDEN, CommonErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler({BindException.class, ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResult<Void>> validationException(Exception e) {
        Stream<String> messages;
        if (e instanceof BindException binding) {
            messages = binding.getAllErrors().stream()
                    .filter(error -> error instanceof FieldError field && field.isBindingFailure()
                            || StringUtils.hasText(error.getDefaultMessage()))
                    .map(error -> error instanceof FieldError field
                            ? field.getField() + ": " + (field.isBindingFailure() ? "参数类型不正确" : field.getDefaultMessage())
                            : error.getDefaultMessage());
        } else if (e instanceof ConstraintViolationException constraint) {
            messages = constraint.getConstraintViolations().stream()
                    .map(error -> error.getPropertyPath() + ": " + error.getMessage());
        } else {
            HandlerMethodValidationException validation = (HandlerMethodValidationException) e;
            if (validation.isForReturnValue()) {
                return handleException(e);
            }
            messages = validation.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage);
        }
        String message = messages.filter(StringUtils::hasText).sorted().findFirst()
                .orElse(CommonErrorCode.INVALID_ARGUMENT.getMessage());
        return failure(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class,
            ServletRequestBindingException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResult<Void>> requestException(Exception e) {
        return failure(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> uploadSizeException(MaxUploadSizeExceededException e) {
        return failure(HttpStatus.BAD_REQUEST, CommonErrorCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResult<Void>> duplicateKeyException(DuplicateKeyException e) {
        log.warn("Duplicate data", e);
        return failure(HttpStatus.CONFLICT, CommonErrorCode.DATA_ALREADY_EXISTS);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e) {
        log.error("Unhandled system exception", e);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResult<Void>> failure(HttpStatus status, ErrorCode errorCode) {
        return failure(status, errorCode, errorCode.getMessage());
    }

    private ResponseEntity<ApiResult<Void>> failure(HttpStatus status, ErrorCode errorCode, String message) {
        return ResponseEntity.status(status).body(ApiResult.failure(errorCode.getCode(), message));
    }
}
