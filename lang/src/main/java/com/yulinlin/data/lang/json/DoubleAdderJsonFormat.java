package com.yulinlin.data.lang.json;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.concurrent.atomic.DoubleAdder;


public class DoubleAdderJsonFormat {

    public static Class<DoubleAdder> getType()  {
        return DoubleAdder.class;
    }

    public static JsonSerializer<DoubleAdder> serialize()  {
        return new JsonSerializer<DoubleAdder>(){
            @Override
            public void serialize(DoubleAdder dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeNumber(dateTime.doubleValue());
            }
        };
    }

    public static JsonDeserializer<DoubleAdder> deserialize()  {
        return new JsonDeserializer<DoubleAdder>(){
            @Override
            public DoubleAdder deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                double text = jsonParser.getDoubleValue();
                DoubleAdder adder = new DoubleAdder();
                adder.add(text);
                return adder;
            }
        };
    }
}
