package com.yulinlin.jdbc.sql.parse.from;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.node.IMetaNode;
import com.yulinlin.data.core.wrapper.impl.AbstractWrapper;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class StoreParse implements IParse<Store> {

    @Override
    public String parse(Store condition, IParamsContext params, IParseManager parseManager) {
        var names = com.yulinlin.jdbc.sql.SqlParamsContext.nameParse(params);
        String table = names.reference(params.parse(condition.getName()).toString());
      if(AliasUtil.supportAlias(params)){
          if(condition.getAlias() != null){

              return table +" " +names.alias(condition.getAlias());
          }
      }
        return table;
    }
}
