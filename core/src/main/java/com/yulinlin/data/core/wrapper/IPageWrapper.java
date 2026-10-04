package com.yulinlin.data.core.wrapper;

//存储接口
public interface IPageWrapper<R extends IPageWrapper<R>> {

    R page(int pageNumber, int pageSize);


}
