package com.yulinlin.data.lang.json;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class YulinlinSimpModule extends SimpleModule {

    public YulinlinSimpModule() {
        addSerializer(DateTimeJsonFormat.getType(), DateTimeJsonFormat.serialize());
        addDeserializer(DateTimeJsonFormat.getType(),DateTimeJsonFormat.deserialize());


        addSerializer(MoneyJsonFormat.getType(), MoneyJsonFormat.serialize());
        addDeserializer(MoneyJsonFormat.getType(),MoneyJsonFormat.deserialize());

        addSerializer(CoinJsonFormat.getType(), CoinJsonFormat.serialize());
        addDeserializer(CoinJsonFormat.getType(),CoinJsonFormat.deserialize());

        addSerializer(DoubleAdderJsonFormat.getType(), DoubleAdderJsonFormat.serialize());
        addDeserializer(DoubleAdderJsonFormat.getType(),DoubleAdderJsonFormat.deserialize());


        addSerializer(LongAdderJsonFormat.getType(), LongAdderJsonFormat.serialize());
        addDeserializer(LongAdderJsonFormat.getType(),LongAdderJsonFormat.deserialize());

    }


}
