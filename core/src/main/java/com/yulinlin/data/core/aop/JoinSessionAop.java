
package com.yulinlin.data.core.aop;

import com.yulinlin.data.core.anno.JoinSession;
import com.yulinlin.data.core.session.SessionUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;


@Aspect
@Order(9998)
public class JoinSessionAop {



    //annotation 方法标记
    // within 类头标记
    @Pointcut("@annotation(com.yulinlin.data.core.anno.JoinSession)"
            + "|| @within(com.yulinlin.data.core.anno.JoinSession)")
    public void aop() {

    }


    @Around("aop()")
    public Object doAroundAdvice(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        JoinSession joinDataSource = methodSignature.getMethod().getAnnotation(JoinSession.class);
        if(joinDataSource == null){
            joinDataSource = pjp.getTarget().getClass().getAnnotation(JoinSession.class);
        }
        SessionUtil.route().pushSession(joinDataSource.value(),joinDataSource.cluster());
        try {
            return pjp.proceed();
        } finally {
            SessionUtil.route().popSession();
        }


    }
}

