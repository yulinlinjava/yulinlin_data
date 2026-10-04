package com.yulinlin.data.lang.json;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yulinlin.data.lang.util.DateTime;

import java.io.IOException;


public class DateTimeJsonFormat  {

    public static Class<DateTime> getType()  {
        return DateTime.class;
    }

    public static JsonSerializer<DateTime> serialize()  {
        return new JsonSerializer<DateTime>(){
            @Override
            public void serialize(DateTime dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(dateTime.toString());
            }
        };
    }

    public static JsonDeserializer<DateTime> deserialize()  {
        return new JsonDeserializer<DateTime>(){
            @Override
            public DateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                String text = jsonParser.getText();
                return DateTime.parse(text);
            }
        };
    }
}
