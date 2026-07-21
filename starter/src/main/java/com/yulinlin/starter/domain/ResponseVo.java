package com.yulinlin.starter.domain;

import io.swagger.annotations.ApiModel;

@ApiModel("响应体")
public class ResponseVo<E> extends R<E> {

    public ResponseVo() {
    }

    public ResponseVo(E data, int code, String msg) {
        super(data, code, msg);
    }

    public static   <E> ResponseVo<E> newInstance(E data){
        return newInstance(data,200);
    }

    public static   <E> ResponseVo<E> newInstance(E data,int code){
        return newInstance(data,code,null);
    }

    public   static  <E> ResponseVo<E> newInstance(E data,String msg){
        return newInstance(data,200,msg);
    }

    public  static  <E> ResponseVo<E> newInstance(E data,int code,String msg){
        ResponseVo vo =  new ResponseVo(data,code,msg);
        return vo;
    }


}
