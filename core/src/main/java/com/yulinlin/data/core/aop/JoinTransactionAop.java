package com.yulinlin.data.core.aop;

import com.yulinlin.data.core.session.SessionUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;


@Aspect
@Order(9999)
public class JoinTransactionAop {


    @Pointcut("@annotation(com.yulinlin.data.core.anno.JoinTransaction)"
            + "|| @within(com.yulinlin.data.core.anno.JoinTransaction)")
    public void aop() {

    }

    @Around("aop()")
    public Object doAroundAdvice(ProceedingJoinPoint pjp) throws Throwable {
     /*   MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        JoinTransaction joinTransaction = methodSignature.getMethod().getAnnotation(JoinTransaction.class);
        if(joinTransaction == null){
            joinTransaction = pjp.getTarget().getClass().getAnnotation(JoinTransaction.class);
        }*/

        SessionUtil.route().startTransaction();
        Object val = null;
        try {
             val =  pjp.proceed();
        }catch (Throwable e){

            SessionUtil.route().rollbackTransaction();
            throw e;
        }
        SessionUtil.route().commitTransaction();
        return val;
    }
}
