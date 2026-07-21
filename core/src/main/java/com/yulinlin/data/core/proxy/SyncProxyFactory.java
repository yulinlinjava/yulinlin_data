package com.yulinlin.data.core.proxy;


import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.model.BaseModelUpdateWrapper;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListener;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
 class SyncProxyFactory implements IProxyFactory,TransactionListener {


    private static ThreadLocal<Map<Object,SyncProxy>> threadLocal =ThreadLocal.withInitial(() ->  {
        return  new HashMap<>();
    });

    private static       Map<Object, SyncProxy> getCache(){
        Map<Object, SyncProxy> cache = threadLocal.get();
        return cache;
    }

    public <E> E getProxy(String session, E data) {
        if(!SessionUtil.route().isOpenTransaction()){
                throw new NoticeException("需要开启事务");
        }

        SyncProxy proxy = getCache().computeIfAbsent(data,key -> {
           return new SyncProxy( data,session);

        });

        return (E)proxy.getProxyInstance();


    }

    @Override
    public <E> E getProxy(E data) {

        return (E)getProxy(SessionUtil.nowSession(),data);

    }


    public <E> List<E> getProxyList(String session, List<E> data) {
        return data.stream().map( row  -> getProxy(session,row)).collect(Collectors.toList());
    }

    @Override
    public <E> List<E> getProxyList(List<E> data) {
        return getProxyList(SessionUtil.nowSession(),data);
    }



    public void startTransaction() {

    }



    public void commitTransaction() {
        Collection<SyncProxy> list =getCache().values();


        List<SyncProxy> updateList =   list.stream().filter(row -> row.getSize() > 0).collect(Collectors.toList());

        Map<String, List<SyncProxy>> collect = updateList.stream().collect(Collectors.groupingBy(SyncProxy::getSession));

        for (Map.Entry<String, List<SyncProxy>> entry : collect.entrySet()) {

            //根据class分组，批量更新
            Map<? extends Class<?>, List<SyncProxy>> collect2 = entry.getValue().stream().collect(Collectors.groupingBy(row -> row.getData().getClass()));

            for (Map.Entry<? extends Class<?>, List<SyncProxy>> listEntry : collect2.entrySet()) {
                List datas =  listEntry.getValue().stream().map(row -> row.getData()).collect(Collectors.toList());
                new BaseModelUpdateWrapper(entry.getKey(),datas)
                        .execute();
            }



        }


        getCache().clear();


    }


    public void rollbackTransaction() {
        getCache().clear();

    }




    /**
     * 字段代理
     */
    private static class SyncProxy implements MethodInterceptor {

        private Object target;

        private Object data;

        private Object proxy;

        private int size = 0;

        private String session;



        public SyncProxy(Object target,String session) {
            this.target = target;
            this.session =session;

            this.data =copy();

        }


        public String getSession() {
            return session;
        }

        private Object copy(){
            //得到真实类型
            Class clazz = ProxyUtil.getProxyClass(target.getClass());
            Object data = ReflectionUtil.newInstance(clazz);
            List<Field> fs =  ReflectionUtil.getAllDeclaredFields(clazz);
            for (Field f : fs) {

                JoinMeta joinMeta =
                        AnnotationUtil.findAnnotation(f,JoinMeta.class);

                boolean isCopy = false;
                if(joinMeta != null && joinMeta.primaryKey()){
                    isCopy = true;
                }

                if(isCopy){
                    Object value =  ReflectionUtil.invokeGetter(target,f.getName());
                    if(value == null){
                        continue;
                    }
                    ReflectionUtil.invokeSetter(data,f.getName(),value);
                }



            }
            return  data;
        }

        @Override
        public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
            //可能触发懒加载
            Object value =  methodProxy.invoke(target,objects);
            //Object value =  method.invoke(target,objects);

            String methodName = method.getName();
            //对拷贝对象也做执行一次
            if(SessionUtil.route().isOpenTransaction() && methodName.startsWith("set")){
                if(objects[0] != null){
                    methodProxy.invoke(data,objects);
                    size++;
                }

            }

            return value;
        }

        public Object getProxyInstance() {
            if(proxy != null){
                return proxy;
            }
            Class clazz =  ProxyUtil.getProxyClass(target.getClass());
            proxy =  ProxyUtil.getProxyInstance(clazz,this);
            return proxy;
        }

        public int getSize() {
            return size;
        }

        public Object getData() {
            return data;
        }
    }

}
