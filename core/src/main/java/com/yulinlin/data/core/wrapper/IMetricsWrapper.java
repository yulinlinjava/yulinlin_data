package com.yulinlin.data.core.wrapper;


import com.yulinlin.data.core.anno.MetricsEnum;
import com.yulinlin.data.core.node.IMetaNode;

public interface IMetricsWrapper<E> extends IMetaNode<IMetricsWrapper<E>> {

    default IMetricsWrapper<E> field(MetricsEnum metricsEnum,String name,String alias){
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
        return this;
    }

    //去重复求总数
     IMetricsWrapper<E> distinctCount(String  name, String alias);

     IMetricsWrapper<E> count(String  name, String alias);
    
     IMetricsWrapper<E> sum(String  name, String alias);
    
    
     IMetricsWrapper<E> avg(String  name, String alias);
    
     IMetricsWrapper<E> min(String  name, String alias);

     IMetricsWrapper<E> max(String  name, String alias);


    
}
