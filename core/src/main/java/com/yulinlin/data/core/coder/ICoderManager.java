package com.yulinlin.data.core.coder;

import java.lang.reflect.Field;
import java.util.List;

public interface ICoderManager {

      IDataBuffer createEncoderBuffer();

      IDataBuffer createDecoderBuffer();

      ICoder getCoder(Class clazz) ;


      //解码
      Object decodeObject(IDataBuffer buffer , Class clazz);

      List<Object> decodeObject(List<IDataBuffer> buffers , Class clazz);
}
