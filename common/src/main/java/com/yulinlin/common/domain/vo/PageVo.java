package com.yulinlin.common.domain.vo;

import com.yulinlin.data.lang.util.Page;

import java.util.List;


public class PageVo<E> extends Page<E> {


    public PageVo() {
    }

    public PageVo(List<E> list) {
        super(list);
    }

    public PageVo(List<E> list, int total) {
        super(list, total);
    }
    public PageVo(List<E> list, long total) {
        super(list, (int)total);
    }
    public static <E> PageVo<E> newInstance(List<E> list){
        return  new PageVo<>(list,list.size());
    }

    public static <E> PageVo<E> newInstance(List<E> list,long total){
        return  new PageVo<>(list,total);
    }

}


