package com.yulinlin.starter.domain;

import com.yulinlin.data.lang.security.CryptoDataResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "响应体")
@Data
public class R<E> implements CryptoDataResponse {

    public static final int SUCCESS_CODE = 200;
    public static final int ERROR_CODE = 500;

    @Schema(description = "数据")
    private E data;

    @Schema(description = "状态码")
    private int code = SUCCESS_CODE;

    //是否加密
    private boolean crypt = false;


    private boolean ok = true;

    @Schema(description = "消息")
    private String msg;


    @Schema(description = "时间戳")
    private long timestamp = System.currentTimeMillis();

    public R() {
    }

    public R(E data, int code, String msg) {
        this.data = data;
        this.code = code;
        this.msg = msg;
        this.ok = code >= 200 && code < 300;
    }

    public void setCode(int code) {
        this.code = code;
        this.ok = code >= 200 && code < 300;
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

    public static R<Void> ok() {
        return ok(null);
    }

    public static <E> R<E> ok(E data) {
        return ok(data, null);
    }

    public static <E> R<E> ok(E data, String msg) {
        return new R<>(data, SUCCESS_CODE, msg);
    }

    public static <E> R<E> fail(String msg) {
        return fail(ERROR_CODE, msg);
    }

    public static <E> R<E> fail(int code, String msg) {
        R<E> response = new R<>(null, code, msg);
        response.ok = false;
        return response;
    }

    /** @deprecated Use {@link #ok(Object)} or {@link #fail(int, String)}. */
    @Deprecated
    public static   <E> R<E> newInstance(E data){
        return ok(data);
    }

    /** @deprecated Use {@link #ok(Object)} or {@link #fail(int, String)}. */
    @Deprecated
    public static   <E> R<E> newInstance(E data, int code){
        return newInstance(data,code,null);
    }

    /** @deprecated Use {@link #ok(Object, String)}. */
    @Deprecated
    public   static  <E> R<E> newInstance(E data, String msg){
        return ok(data,msg);
    }

    /** @deprecated Use {@link #ok(Object, String)} or {@link #fail(int, String)}. */
    @Deprecated
    public  static  <E> R<E> newInstance(E data, int code, String msg){
        return new R<>(data,code,msg);
    }


}
