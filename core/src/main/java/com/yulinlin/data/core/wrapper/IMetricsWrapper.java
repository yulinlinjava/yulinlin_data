package com.yulinlin.data.core.wrapper;


import com.yulinlin.data.core.anno.MetricsEnum;
import com.yulinlin.data.core.node.IMetaNode;

public interface IMetricsWrapper<E,R extends IMetricsWrapper<E,R>> extends IMetaNode<R> {

    default R field(MetricsEnum metricsEnum,String name,String alias){
        switch (metricsEnum){
            case avg:{
                avg(name,alias);
                break;
            }case min:{
                min(name,alias);
                break;
            }case max:{
                max(name,alias);
                break;
            }case sum:{
                sum(name,alias);
                break;
            }case count:{
                count(name,alias);
                break;
            }case distinctCount:{
                distinctCount(name,alias);
                break;
            }
        }
        return (R)this;
    }

    //去重复求总数
     R distinctCount(String  name, String alias);

     R count(String  name, String alias);
    
     R sum(String  name, String alias);
    
    
     R avg(String  name, String alias);
    
     R min(String  name, String alias);

     R max(String  name, String alias);


   //子对象查询
   <N,NR extends IMetricsWrapper<N,NR>> NR object(String name);


   //子对象查询
   <N,NR extends IMetricsWrapper<N,NR>> NR  object(String name,Class<N> clazz);

    
}
