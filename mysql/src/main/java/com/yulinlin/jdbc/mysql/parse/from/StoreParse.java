package com.yulinlin.jdbc.mysql.parse.from;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.core.wrapper.impl.AbstractWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class StoreParse implements IParse<Store> {

    @Override
    public String parse(Store condition, IParamsContext params, IParseManager parseManager) {
        String table =  params.parse(condition.getName()).toString();
      if(AliasUtil.supportAlias(params)){
          if(condition.getAlias() != null){

              return table +" " +condition.getAlias();
          }
      }
        return table;
    }
}
