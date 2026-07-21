package com.yulinlin.data.lang.enums;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;


public interface IEnum<E> {
    String getLabel();

    E getValue();

}
