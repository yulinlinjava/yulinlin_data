package com.yulinlin.data.lang.json;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yulinlin.data.lang.util.Coin;
import com.yulinlin.data.lang.util.Money;

import java.io.IOException;


public class CoinJsonFormat {

    public static Class<Coin> getType()  {
        return Coin.class;
    }

    public static JsonSerializer<Coin> serialize()  {
        return new JsonSerializer<Coin>(){
            @Override
            public void serialize(Coin dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeNumber(dateTime.getValue());
            }
        };
    }

    public static JsonDeserializer<Coin> deserialize()  {
        return new JsonDeserializer<Coin>(){
            @Override
            public Coin deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                double doubleValue;
                try {
                    doubleValue = jsonParser.getDoubleValue();
                }catch (Exception e){
                    doubleValue =  Double.parseDouble(     jsonParser.getText());

                }


                return Coin.of(doubleValue);
            }
        };
    }
}
