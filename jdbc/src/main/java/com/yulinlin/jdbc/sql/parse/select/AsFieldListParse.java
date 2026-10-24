package com.yulinlin.jdbc.sql.parse.select;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.AsFieldListWrapper;

import java.util.StringJoiner;

public class AsFieldListParse implements IParse<AsFieldListWrapper> {
    @Override
    public String parse(AsFieldListWrapper fields, IParamsContext params, IParseManager manager) {
        StringJoiner sql = new StringJoiner(" , ");
        for (Object item : fields.getList()) sql.add(String.valueOf(manager.parse((INode) item, params)));
        return sql.toString();
    }
}
