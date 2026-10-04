package com.yulinlin.data.lang.json;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.concurrent.atomic.LongAdder;


public class LongAdderJsonFormat {

    public static Class<LongAdder> getType()  {
        return LongAdder.class;
    }

    public static JsonSerializer<LongAdder> serialize()  {
        return new JsonSerializer<LongAdder>(){
            @Override
            public void serialize(LongAdder dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeNumber(dateTime.longValue());
            }
        };
    }

    public static JsonDeserializer<LongAdder> deserialize()  {
        return new JsonDeserializer<LongAdder>(){
            @Override
            public LongAdder deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                Long text = jsonParser.getLongValue();
                LongAdder longAdder = new LongAdder();
                longAdder.add(text);
                return longAdder;
            }
        };
    }
}
