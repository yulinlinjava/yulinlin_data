package com.yulinlin.data.lang.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yulinlin.data.lang.enums.IEnum;
import com.yulinlin.data.lang.util.DateTime;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Date;


public class EnumSerializer extends JsonSerializer<Object> {


    @Override
    public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {

        if(o instanceof IEnum){
            IEnum e = (IEnum)o ;
            jsonGenerator.writeString(e.getLabel());
        }

    }
}
