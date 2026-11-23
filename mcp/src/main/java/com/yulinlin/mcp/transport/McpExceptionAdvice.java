package com.yulinlin.mcp.transport;
import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.management.McpManagementController;
import com.yulinlin.starter.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={McpTransport.class,McpManagementController.class})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="yulinlin.mcp",name="enabled",havingValue="true")
public class McpExceptionAdvice implements org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<Object> {
 @Override public boolean supports(org.springframework.core.MethodParameter p,Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> c){return true;}
 @Override public Object beforeBodyWrite(Object body,org.springframework.core.MethodParameter p,org.springframework.http.MediaType type,Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter,org.springframework.http.server.ServerHttpRequest request,org.springframework.http.server.ServerHttpResponse response){response.getHeaders().setCacheControl("no-store");return body;}

 @ExceptionHandler(McpException.class) public ResponseEntity<?> mcp(McpException e){var builder=ResponseEntity.status(e.status()).header("Cache-Control","no-store");if(e.status()==401)builder.header("WWW-Authenticate","Bearer");return builder.body(R.fail(e.status(),e.getMessage()));}
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().header("Cache-Control","no-store").body(R.fail(400,e.getMessage()));}
}
