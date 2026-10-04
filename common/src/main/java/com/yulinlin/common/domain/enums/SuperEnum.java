package com.yulinlin.common.domain.enums;

import com.yulinlin.data.lang.enums.IEnum;

public interface SuperEnum<E> extends IEnum<E> {




    default EnumItem getEnumItem(){
        return new EnumItem(this.getLabel(),this.getValue(),this.getData());
    }

    default Object getData(){
        return null;
    }

}
