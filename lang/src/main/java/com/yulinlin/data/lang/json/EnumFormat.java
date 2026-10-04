package com.yulinlin.data.lang.json;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.*;
import com.yulinlin.data.lang.enums.IEnum;
import com.yulinlin.data.lang.util.Coin;

import java.io.IOException;
import java.lang.reflect.Constructor;


public class EnumFormat {

    public static Class<Enum> getType()  {
        return Enum.class;
    }

    public static JsonSerializer<Enum> serialize()  {
        return new JsonSerializer<Enum>(){
            @Override
            public void serialize(Enum dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeObject(dateTime.name());
            }
        };
    }

    public static JsonDeserializer<Enum> deserialize()  {
        return new JsonDeserializer<Enum>(){
            @Override
            public Enum deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                String text = jsonParser.getText();
                JavaType contentType = ctxt.getContextualType();
                if(contentType != null){
                    Class<?> targetType = contentType.getRawClass();
                    IEnum[] enums = (IEnum[]) targetType.getEnumConstants();
                    for (IEnum iEnum : enums) {
                        if(text.equals(iEnum.getValue().toString())){
                            return (Enum)iEnum;
                        }
                    }

                }else {

                }

                return null;
            }
        };
    }
}
