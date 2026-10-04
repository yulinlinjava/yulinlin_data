package com.yulinlin.mongodb.parse;

import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.bson.*;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BsonUtil {


    public static BsonDocument merge(List<BsonDocument> list){
        BsonDocument document = new BsonDocument();
        for (BsonDocument bsonDocument : list) {
            for (String s : bsonDocument.keySet()) {
                document.append(s,bsonDocument.get(s));
            }
        }
        return document;
    }

    public static BsonString toBsonKey(String key){
        return new BsonString("$"+key);
    }



    public static BsonValue toBsonValue(Object value){
        if(value == null){
            return new BsonNull();
        }
        if(value instanceof Boolean){
            return new BsonBoolean((boolean)value);
        }else if(value instanceof Integer){
            return new BsonInt32((int)value);
        }else if(value instanceof Long){
            return new BsonInt64((long)value);
        } else if(value instanceof Double){
            return new BsonDouble((double)value);
        }else if(value instanceof String){
            return new BsonString((String) value);
        }else if(value instanceof Collection){
            BsonArray bsonValues = new BsonArray();
            Collection coll = (Collection)value;
            for (Object o : coll) {
                bsonValues.add(
                    toBsonValue(o)
                );
            }
            return bsonValues;
        }else if(value instanceof Map){
            BsonDocument bsonDocument = new BsonDocument();
            Map<String,Object> map = (Map)value;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                bsonDocument.append(entry.getKey(),
                        toBsonValue(entry.getValue())
                );
            }
            return bsonDocument;
        }else {
            BsonDocument bsonDocument = new BsonDocument();
            for (Field field : ReflectionUtil.getAllDeclaredFields(value.getClass())) {
                Object val =  ReflectionUtil.invokeGetter(value,field);
                if(val != null){
                    bsonDocument.append(field.getName(),
                            toBsonValue(val)
                    );
                }
            }
            return bsonDocument;
        }
    }
}
