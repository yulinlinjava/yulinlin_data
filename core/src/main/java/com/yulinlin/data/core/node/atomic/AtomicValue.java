package com.yulinlin.data.core.node.atomic;

import com.yulinlin.data.core.node.MetaNode;


public  class AtomicValue extends MetaNode {





    private Object value;



    private OperateEnum operate;


    public AtomicValue(Object name, Object value, OperateEnum operate) {
        super(name);

        this.value = value;
        this.operate = operate;
    }




    public Object getValue() {
        return value;
    }

    public OperateEnum getOperate() {
        return operate;
    }



    public static AtomicValue instance(Object name, Object value){
        return new AtomicValue(name,value,OperateEnum.store);
    }

    public static AtomicValue incInstance(Object name,Object value){
        return new AtomicValue(name,value, OperateEnum.inc);
    }

    public static AtomicValue decInstance(Object name,Object value){
        return new AtomicValue(name,value,OperateEnum.dec);
    }


    //描述修改操作分类用
    public enum OperateEnum {

        //普通存储字段
        store("存储"),
        //唯一主键
        inc("自增"),
        //乐观锁实现
        dec("自减"),
        ;

        private String label;


        OperateEnum(String label) {
            this.label = label;
        }


        public String getLabel() {
            return label;
        }
    }


}
