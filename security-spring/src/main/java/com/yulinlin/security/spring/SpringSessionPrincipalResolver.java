package com.yulinlin.security.spring;

import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.session.SessionSupport;
import org.springframework.security.core.userdetails.UserDetails;
import java.security.Principal;

/** userId 与原生 principal 的映射。分布式按用户查询需能反向解析原生索引名。 */
public interface SpringSessionPrincipalResolver {
    String userId(Object principal);
    Object principal(String userId);

    /** 默认 userId=原生用户名；IUserDetails 使用自身 userId。自定义身份映射时应一起替换。 */
    static SpringSessionPrincipalResolver defaults() {
        return new SpringSessionPrincipalResolver() {
            @Override public String userId(Object principal) {
                String id;
                if (principal instanceof IUserDetails user) id = user.getUserId();
                else if (principal instanceof UserDetails user) id = user.getUsername();
                else if (principal instanceof Principal user) id = user.getName();
                else if (principal instanceof String value) id = value;
                else throw new IllegalStateException("无法识别原生 principal，请提供 SpringSessionPrincipalResolver");
                return SessionSupport.requireText(id, "principal.userId");
            }
            @Override public Object principal(String userId) { return SessionSupport.requireText(userId, "userId"); }
        };
    }
}
