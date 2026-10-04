package com.yulinlin.data.core.wrapper;



public interface IExecuteWrapper<R>  {


    R table(String name);

    R table(String name, String alias);


}
