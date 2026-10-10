package com.yulinlin.data.core.aop;

import com.yulinlin.data.core.proxy.EntityProxyService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;

/** Applies one auto-update policy to every entity query inside an annotated call. */
@Aspect
@Order(9998)
public class JoinSyncAop {
    private final EntityProxyService proxyService;

    public JoinSyncAop(EntityProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @Pointcut("@annotation(com.yulinlin.data.core.anno.JoinSync)"
            + "|| @within(com.yulinlin.data.core.anno.JoinSync)")
    public void aop() { }

    @Around("aop()")
    public Object autoUpdate(ProceedingJoinPoint point) throws Throwable {
        proxyService.enterAutoUpdate();
        try {
            return point.proceed();
        } finally {
            proxyService.exitAutoUpdate();
        }
    }
}
