
package com.yulinlin.jdbc.aop;

import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;


@Aspect
@Order(9999)
public class SpringTransactionAop {



    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)"
            + "|| @within(org.springframework.transaction.annotation.Transactional)")
    public void aop() {

    }


    @Around("aop()")
    public Object doAroundAdvice(ProceedingJoinPoint pjp) throws Throwable {

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

