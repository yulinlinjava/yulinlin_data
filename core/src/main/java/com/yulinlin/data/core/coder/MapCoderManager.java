package com.yulinlin.data.core.coder;

import com.yulinlin.data.core.coder.impl.*;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.lang.util.DateTime;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MapCoderManager implements ICoderManager {

     private Map<Class,ICoder> cache = new ConcurrentHashMap<>();

     private Set<ICoder> serializeSet = new HashSet<>();

     public MapCoderManager() {
          init();
     }


     protected void init(){
          registerCoder(new CodeObjectCoder());
          registerCoder(new DoubleAdderCoder());
          this.registerCoder(new DateTimeCoder());
          this.registerCoder(new BigIntegerCoder());
          this.registerCoder(new BigDecimalCoder());
          this.registerCoder(new LongAdderCoder());
          this.registerCoder(new FloatCoder());
          this.registerCoder(new EnumCoder());

          this.registerCoder(new ByteArrayCoder());
          this.registerCoder(new InputStreamCoder());
          this.registerCoder(new DateCoder());

          this.registerCoder(new LocalDateCoder());
          this.registerCoder(new LocalDateTimeCoder());
          this.registerCoder(new LocalTimeCoder());

          this.registerCoder(new StringCoder());
          this.registerCoder(new IntegerCoder());
          this.registerCoder(new DoubleCoder());
          this.registerCoder(new BooleanCoder());
          this.registerCoder(new LongCoder());


          this.registerCoder(new ListStringCoder());


     }

     @Override
     public IDataBuffer createEncoderBuffer() {
          return new MapDataBuffer(true,this);
     }

     @Override
     public IDataBuffer createDecoderBuffer() {
          return new MapDataBuffer(false,this);
     }

     public void registerCoder(Collection<ICoder> serializes){
          for (ICoder s: serializes){
               registerCoder(s);
          }
     }

     //注册一个类型拦截器
     public void registerCoder(ICoder serialize){
          serializeSet.add(serialize);
     }


     public ICoder getCoder(Class clazz) {
          ICoder coder =  cache.computeIfAbsent(clazz,(type) -> {
               for (ICoder iTypeSerialize : serializeSet) {
                    if (iTypeSerialize.getTypeClass() == type) {
                         return iTypeSerialize;
                    }
               }
               List<ICoder> collect = serializeSet.stream().filter(row -> row.check(type))
                       .sorted((x, y) -> {
                            return y.priority() - x.priority();
                       }).collect(Collectors.toList());

               if(collect.isEmpty()){
                    throw new NoticeException("缺少类型编码器:"+type);
               }

               return collect.get(0);
          });
          return coder;
     }





     @Override
     public Object decodeObject(IDataBuffer buffer, Class clazz) {
          return buffer.decode(clazz);
     }

     public static int bufferSize =256*1024;

     @Override
     public List<Object> decodeObject(List<IDataBuffer> buffers, Class clazz) {
          if(buffers.size() < bufferSize){
               return buffers.stream().map(row -> decodeObject(row,clazz)).collect(Collectors.toList());
          }else {
               return buffers.parallelStream().map(row -> decodeObject(row,clazz)).collect(Collectors.toList());
          }
     }


}
