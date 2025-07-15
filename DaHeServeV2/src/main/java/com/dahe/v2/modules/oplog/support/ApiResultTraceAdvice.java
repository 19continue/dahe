package com.dahe.v2.modules.oplog.support;

import com.dahe.v2.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
/**
 * 响应追踪增强器。
 * 在统一 `Result` 出参写回时，将业务码和业务消息写入 request attribute，
 * 供 `OperationLogInterceptor` 在 afterCompletion 阶段落库使用。
 */
public class ApiResultTraceAdvice implements ResponseBodyAdvice<Object> {

    @Override
    /** 全局生效。 */
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    /** 捕获 Result 出参并写入操作日志追踪字段。 */
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(body instanceof Result) || !(request instanceof ServletServerHttpRequest)) {
            return body;
        }
        Result<?> result = (Result<?>) body;
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        if (servletRequest != null) {
            servletRequest.setAttribute(OperationLogTraceKeys.ATTR_RESULT_CODE, result.getCode());
            servletRequest.setAttribute(OperationLogTraceKeys.ATTR_RESULT_MESSAGE, result.getMessage());
        }
        return body;
    }
}
