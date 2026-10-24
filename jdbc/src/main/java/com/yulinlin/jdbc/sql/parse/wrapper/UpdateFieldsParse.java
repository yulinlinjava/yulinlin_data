package com.yulinlin.jdbc.sql.parse.wrapper;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.UpdateFieldsWrapper;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

import java.util.StringJoiner;

public class UpdateFieldsParse implements IParse<UpdateFieldsWrapper> {
    @Override
    public String parse(UpdateFieldsWrapper fields, IParamsContext params, IParseManager manager) {
        StringJoiner sql = new StringJoiner(" , ");
        for (Object item : fields.getList()) {
            AtomicValue field = (AtomicValue) item;
            String column = AliasUtil.parse(field, params);
            boolean arithmetic = field.getOperate() == AtomicValue.OperateEnum.inc
                    || field.getOperate() == AtomicValue.OperateEnum.dec;
            String value = arithmetic && field.getValue() instanceof Number number
                    ? params.putNumberGetKey(number) : params.putGetKey(field.getValue());
            String expression = switch (field.getOperate()) {
                case inc -> column + " + " + value;
                case dec -> column + " - " + value;
                case store -> value;
            };
            sql.add(column + " = " + expression);
        }
        return sql.toString();
    }
}
