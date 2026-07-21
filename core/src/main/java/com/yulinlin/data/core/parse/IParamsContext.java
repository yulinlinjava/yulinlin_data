package com.yulinlin.data.core.parse;


import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.util.Template;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public interface IParamsContext {

    String putGetKey(Object value);

    void put(String key,Object value);

    default void put(Map<String,Object> data){
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if(entry.getValue() == null){
                continue;
            }
            put(entry.getKey(),entry.getValue());
        }
    }

  default Object encode(Object value){
      return getDataBuffer().encode(value);
  }

    default Collection<Object> encodeList(Collection<Object> list){
        return list.stream().map(this::encode).collect(Collectors.toList());
    }



     IDataBuffer getDataBuffer() ;


  AliasContent getAliasContent();

     String toColumn(String name);

     RequestType getRequestType() ;




     String toKey(String name);

    Object getRoot();

  default  Object parse(String path){
      Map root = getDataBuffer().toMap();
      return Template.render(path,root);



    }

}
