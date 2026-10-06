package com.yulinlin.starter.domain;

import com.yulinlin.data.lang.security.CryptoDataResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "响应体")
@Data
public class R<E> implements CryptoDataResponse {

    @Schema(description = "数据")
    private E data;

    @Schema(description = "状态码")
    private int code = 200;

    //是否加密
    private boolean crypt = false;


    private boolean ok ;

    @Schema(description = "消息")
    private String msg;


    @Schema(description = "时间挫")
    private long timestamp;

    public R() {
    }

    public R(E data, int code, String msg) {
        this.data = data;
        this.code = code;
        this.msg = msg;
        this.ok = code == 200;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public E getData() {
        return data;
    }

    //加密后调用
    @Override
    @SuppressWarnings("unchecked")
    public void onCryptAfter(Object data){
        this.data = (E) data;
        this.crypt = true;
    }


    public static   <E> R<E> newInstance(E data){
        return newInstance(data,200);
    }

    public static   <E> R<E> newInstance(E data, int code){
        return newInstance(data,code,null);
    }

    public   static  <E> R<E> newInstance(E data, String msg){
        return newInstance(data,200,msg);
    }

    public  static  <E> R<E> newInstance(E data, int code, String msg){
        R vo =  new R(data,code,msg);
        return vo;
    }


}
