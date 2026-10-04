package com.yulinlin.data.core.request;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryRequest<E> extends BaseRequest<E> {

    private INode wrapper;



    private QueryRequest(Class entityClass, INode wrapper) {
        super(entityClass);
        this.wrapper = wrapper;
    }

    private QueryRequest(Class entityClass, Class fromClass, INode wrapper) {
        super(entityClass, fromClass);
        this.wrapper = wrapper;
    }

    public QueryRequest copy(INode wrapper){
        QueryRequest request =  new QueryRequest(this.getEntityClass(),this.getFromClass(),wrapper);
        request.setRoot(getRoot());
        request.setSession(getSession());
        return request;
    }



    //查询
    public   List<E> selectList(){

        return SessionUtil.route().select(this);

    }

    public   Page<E> selectPage(){

        return SessionUtil.route().page(this);

    }


    public Integer count(){

        return SessionUtil.route().count(this);
    }

    public   E selectOne(){


        List<E> es = selectList();
        if(es.size() == 0){
            return null;
        }
        return es.get(0);


    }

    public  <K> Map<K,List<E>> selectByGroup(String key){
        List<E> list = this.selectList();

        Map<Object,List<E>> map =  new LinkedHashMap<>();

        for (Object row : list){
            Object k =  ReflectionUtil.invokeGetter(row,key);
            List<E> vals  =  map.get(k);
            if(vals == null){
                vals = new ArrayList<>();
                map.put(k,vals);
            }
            vals.add((E)row);
        }

        return  (Map<K,List<E>>)map;



    }


    public  <K>  Map<K,E> selectByMap( String key){

        List<E> list = this.selectList();


        Map<Object,E> map =  new LinkedHashMap<>();

        for (Object row : list){
            Object k =  ReflectionUtil.invokeGetter(row,key);
            map.put(k,(E)row);
        }

        return ( Map<K,E> ) map;

    }


    public INode getWrapper() {
        return wrapper;
    }



    public static <E> QueryRequest<E> newInstance(Class<E> clazz, INode node){
        QueryRequest executeRequest = new QueryRequest(clazz,clazz,node);
        return executeRequest;
    }
}
