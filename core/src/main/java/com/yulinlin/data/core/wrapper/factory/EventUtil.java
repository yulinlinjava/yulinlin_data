package com.yulinlin.data.core.wrapper.factory;


import com.yulinlin.data.core.model.BaseModel;

class EventUtil {

    public static int insert=0;

    public static int update=1;

    public static int delete=2;

    public static int select=3;

    public static int group=4;

    public static void before(Object obj,int mode){
        if(obj instanceof BaseModel){
            BaseModel  model = (BaseModel)obj;
            if(mode == insert){
                model.insertBefore();
            }else if(mode == update){
                model.updateBefore();
            }else if(mode == delete){
                model.deleteBefore();
            }else if(mode == select){
                model.selectBefore();
            }else if(mode == group){
                model.groupBefore();
            }
        }


    }


}
