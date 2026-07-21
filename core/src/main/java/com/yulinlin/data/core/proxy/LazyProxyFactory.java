package com.yulinlin.data.core.proxy;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.event.IProxyEvent;
import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionUtil;
import com.yulinlin.data.core.util.*;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.ISelectWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * 查询懒加载代理
 * 懒加载必须基于自定义类
 */
@Slf4j
public class LazyProxyFactory implements IProxyFactory{



    private SyncProxyFactory syncProxyFactory;

    public LazyProxyFactory(SyncProxyFactory syncProxyFactory) {
        this.syncProxyFactory = syncProxyFactory;
    }

    @Override
    public Object getProxy(Object data) {
        return getLazyProxy(data);
    }



    //得到懒加载对象
    public Object getLazyProxy(Object target) {
        if(target instanceof Map){
            return target;
        }
        if(target instanceof List){
            return getProxyList((List)target);
        }else {
            return getProxyList(Arrays.asList(target)).get(0);
        }

    }


    /**
     * 得到代理列表
     * @param list
     * @return
     */
    public <E> List<E> getProxyList(List<E> list) {
        if(list.isEmpty()){
            return list;
        }
        if( list.get(0) instanceof Map){
            return list;
        }
        Class clazz =      list.get(0).getClass();
        List value =  handleClass(clazz, list);
        return  value;
    }


    //对象get
    private List invokeGetter(Object coll,String fieldName){
        return invokeGetter(Arrays.asList(coll),fieldName);
    }



    /**
     * 集合get
     * @param coll
     * @param fieldName
     * @return
     */
    private List invokeGetter(Collection coll,String fieldName){
        if(fieldName.startsWith("${")){
            fieldName = fieldName.substring(2,fieldName.length()-1);
        }else {
            return Arrays.asList(fieldName);
        }
        Set set  = new HashSet<>();

        for (Object row : coll) {
            Collection rows = new HashSet();
            if(fieldName.contains(".")){
                int y = fieldName.lastIndexOf(".");
                String key =  fieldName.substring(0,y);
                String value =  fieldName.substring(y+1,fieldName.length());

                Object val =  ReflectionUtil.invokeGetter(row,key);

                if(val == null){

                    continue;
                }
                if(val instanceof Collection){
                    rows.addAll((Collection)val);
                }else{
                    rows.add(val);
                }

                for (Object o : rows) {
                    Object fValue =  ReflectionUtil.invokeGetter(o,value);
                    if(fValue == null){
                        continue;
                    }
                    if(fValue instanceof Collection){
                        set.addAll((Collection)fValue);
                    }else{
                        set.add(fValue);
                    }

                }

            }else{

                Object val =  ReflectionUtil.invokeGetter(row,fieldName);
                if(val == null){
                    continue;
                }
                if(val instanceof Collection){
                    set.addAll((Collection)val);
                }else{
                    set.add(val);
                }

            }


        }

        return ListUtil.encodeCollection(set);


    }


    private void initField(Field field,Object bean,Object fValue){

        Class fieldClass = field.getType();
        Object value = null;
        if(fValue instanceof List){
            List coll = (List)fValue;
            if(List.class.isAssignableFrom(fieldClass)){
                value = fValue;
            }else if(Set.class.isAssignableFrom(fieldClass)){
                value = new HashSet<>(coll);
            }else if(!coll.isEmpty()){
                value = coll.get(0);

            }
        }else {
            if(fieldClass == Integer.class){
                value = fValue;
            }else if(fieldClass == Long.class){
                value = (Long)fValue;
            }
        }




        if(value != null){

            ReflectionUtil.invokeSetter(bean,field.getName(),value);
            if(bean instanceof IProxyEvent){
                IProxyEvent event = (IProxyEvent)bean;
                event.startInjection(field.getName(),value);
            }
        }

    }


    private static ThreadLocal<Boolean> cacheLocal = ThreadLocal.withInitial(() -> false);



    public static void cache(boolean cache){
        cacheLocal.set(cache);
    }

    public static boolean isCache(){
        return cacheLocal.get();
    }



    private Object handleField(Field field,List list,boolean cache){
        if(list.isEmpty()){
            return list;
        }
        JoinQuery joinQuery = AnnotationUtil.findAnnotation(field,JoinQuery.class);


        if(joinQuery == null){
            return  null;
        }

        JoinOrder[] orders = joinQuery.order();


        JoinSession joinDataSource = AnnotationUtil.findAnnotation(field,JoinSession.class);
        String session = null;
        if(joinDataSource != null){
             session =  joinDataSource.value();
        }

        if(session == null){
            session = SessionUtil.nowSession();
        }


        boolean isModel=false;
        Class<?> entityClass;
        if(joinQuery.model() == Object.class){
            entityClass = field.getType();
            if (Collection.class.isAssignableFrom(entityClass)) {
                entityClass = GenericUtil.getFieldGeneric(field, 0);
            }
        }else{

                entityClass = joinQuery.model();

                isModel=true;


        }




        if(
                isModel ||
                joinQuery.wheres().length > 0){

            for (Object bean : list) {
                BaseModelSelectWrapper modelSelectWrapper =
                        new BaseModelSelectWrapper(session,entityClass);

                modelSelectWrapper.cache(cache);

                if(joinQuery.size() > 0){
                    modelSelectWrapper.page(1, joinQuery.size());
                }

                boolean ok = false;

                if(isModel && joinQuery.wheres().length == 0){
                    List coll =  invokeGetter(bean,joinQuery.value());
                    modelSelectWrapper.in(joinQuery.primary(), coll);
                    ok=true;
                }else {
                    for (JoinWhere where : joinQuery.wheres()) {

                        List coll =  invokeGetter(bean,where.value());
                        if(ReflectionUtil.isEmpty(coll)){
                            continue;
                        }

                        Object value;

                        if(where.condition() == ConditionEnum.in){
                            value = coll;
                        }else {
                            value =  coll.get(0);
                        }

                        modelSelectWrapper.condition(where.name(),where.condition(),value);
                        ok =true;
                    }
                }



                if(ok == true){
                    if(isModel){
                        int fValue = modelSelectWrapper.count();
                        initField(field,bean,fValue);
                    }else {

                        if(orders.length > 0){
                            for (JoinOrder order : orders) {
                                modelSelectWrapper.orderBy(order.name(),order.asc());
                            }
                        }
                        List fValue = modelSelectWrapper.selectList();
                        initField(field,bean,fValue);
                    }

                }

            }





        }else {

            Collection set  =  invokeGetter(list,joinQuery.value());

            List<?> values   = new ArrayList<>();
            if(set.isEmpty()){

                if(set.isEmpty()){
                    log.warn(entityClass.getSimpleName()+":关联查询取值字段："+joinQuery.value()+"为空");
                }
            }else{
                BaseModelSelectWrapper modelSelectWrapper =
                        new BaseModelSelectWrapper(session,entityClass);

                modelSelectWrapper.cache(cache);
                modelSelectWrapper.in(joinQuery.primary(), set);

                if(orders.length > 0){
                    for (JoinOrder order : orders) {
                        modelSelectWrapper.orderBy(order.name(),order.asc());
                    }
                }
                values = modelSelectWrapper.selectList();
                if(values.size() > 0){
                    //执行同步增强
                    JoinSync joinSync = AnnotationUtil.findAnnotation(field,JoinSync.class);
                    //执行立刻加载
                    if(joinSync != null && SessionUtil.route().isOpenTransaction()){
                        values =  syncProxyFactory.getProxyList(session, values);
                    }
                }
            }

            //遍历对象列表
            for (Object row : list) {

                //获取值
                Collection keys =  invokeGetter(row,joinQuery.value());
                List fValue =  new ArrayList();

                for (Object val : values) {
                    Object primary =  ReflectionUtil.invokeGetter(val,joinQuery.primary());
                    if(keys.contains(primary)){
                        fValue.add(val);
                    }
                }
                initField(field,row,fValue);
            }

        }





        return list;
    }


    static Map<Class,Boolean> lazyCache = new HashMap<>();

    public  boolean isQuery(Class clazz){
        return lazyCache.computeIfAbsent(clazz,cla -> {
            List<Field> fs =  ReflectionUtil.getAllDeclaredFields(cla);
            for (Field f : fs) {
                JoinQuery joinLazy = AnnotationUtil.findAnnotation(f,JoinQuery.class);
                if(joinLazy != null){
                    return true;
                }
            }
            return false;
        });
    }


    private List handleClass(Class clazz,List list){
        if(isQuery(clazz) == false){
            return list;
        }

        boolean cache = isCache();


        List<Field> fs =  ReflectionUtil.getAllDeclaredFields(clazz);
        boolean isLazy = false;
        for (Field f : fs) {
            JoinQuery joinQuery = AnnotationUtil.findAnnotation(f,JoinQuery.class);
            if(joinQuery == null){
                continue;
            }
            JoinLazy joinLazy = AnnotationUtil.findAnnotation(f,JoinLazy.class);
            //执行立刻加载
            if(joinLazy == null){
                handleField(f,list,cache);
                //构造懒同步

            }else{
                isLazy = true;
            }
        }


        //构造代理
        if(isLazy && SessionUtil.route().isOpenTransaction()) {
            ArrayList arr = new ArrayList<>();
            for (Object row : list) {
                LazyProxy lazyProxy =   new  LazyProxy(row);
                Object proxy = lazyProxy.getProxyInstance();

                arr.add(proxy);
            }
            return  arr;
        }else {

            for (Object value : list) {
                if(value instanceof IProxyEvent){
                    IProxyEvent event = (IProxyEvent)value;
                    event.finishInjection();
                }
            }
        }

        return list;
    }




    /**
     * 字段代理
     */
    private class LazyProxy implements MethodInterceptor {

        private Set<String> methodGet;

        private Object target;
        private Object proxy;

        private boolean cache;

        public LazyProxy(Object target) {
            this.target = target;
            this.methodGet =new HashSet<>();
            this.cache=LazyProxyFactory.isCache();

        }

        @Override
        public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {


            //这里set到target了
            String methodName = method.getName();
            Object value =  methodProxy.invoke(target,objects);

             if(value == null && !methodGet.contains(methodName)){
                if(methodName.startsWith("is")){
                    methodName =  methodName.substring(2);
                }else if(methodName.startsWith("get")){
                    methodName = methodName.substring(3);
                }else{
                    return  value;
                }

                if(!SessionUtil.route().isOpenTransaction()){
                    log.error("事务没开启，禁止懒查询");
                    return null;
                }


                String fName = StringUtil.toLowerCaseFirstOne(methodName);

                Field f =  ReflectionUtil.findField(o.getClass(),fName);

                handleField( f,Arrays.asList(o),cache);
                methodGet.add(methodName);


                 //重新get
                 value =  method.invoke(target,objects);



             }





            return value;
        }

        public Object getProxyInstance() {
            if(proxy != null){
                return proxy;
            }
            Class clazz = ProxyUtil.getProxyClass( target.getClass());

            proxy =  ProxyUtil.getProxyInstance(clazz,this);


            return  proxy;
        }

    }
}