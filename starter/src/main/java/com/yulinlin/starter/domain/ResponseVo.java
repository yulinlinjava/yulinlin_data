package com.yulinlin.starter.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @deprecated Use {@link R}.
 */
@Schema(description = "响应体")
@Deprecated
public class ResponseVo<E> extends R<E> {

    public ResponseVo() {
    }

    public ResponseVo(E data, int code, String msg) {
        super(data, code, msg);
    }

    public static   <E> ResponseVo<E> newInstance(E data){
        return newInstance(data, SUCCESS_CODE);
    }

    public static   <E> ResponseVo<E> newInstance(E data,int code){
        return newInstance(data,code,null);
    }

    public   static  <E> ResponseVo<E> newInstance(E data,String msg){
        return newInstance(data, SUCCESS_CODE,msg);
    }

    public  static  <E> ResponseVo<E> newInstance(E data,int code,String msg){
        return new ResponseVo<>(data,code,msg);
    }


}
