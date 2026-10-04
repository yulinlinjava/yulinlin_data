package com.yulinlin.data.lang.json;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Money;

import java.io.IOException;


public class MoneyJsonFormat {

    public static Class<Money> getType()  {
        return Money.class;
    }

    public static JsonSerializer<Money> serialize()  {
        return new JsonSerializer<Money>(){
            @Override
            public void serialize(Money dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeNumber(dateTime.getValue());
            }
        };
    }

    public static JsonDeserializer<Money> deserialize()  {
        return new JsonDeserializer<Money>(){
            @Override
            public Money deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                double doubleValue;
                try {
                    doubleValue = jsonParser.getDoubleValue();
                }catch (Exception e){
                    doubleValue =  Double.parseDouble(     jsonParser.getText());

                }

                return Money.of(doubleValue);
            }
        };
    }
}
