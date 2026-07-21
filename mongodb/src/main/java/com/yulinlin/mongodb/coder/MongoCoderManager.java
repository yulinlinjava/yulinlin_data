package com.yulinlin.mongodb.coder;

import com.yulinlin.data.core.coder.MapCoderManager;

public class MongoCoderManager extends MapCoderManager {

    protected void init(){

        super.init();

        this.registerCoder(new CollectionCoder());
        this.registerCoder(new MapCoder());
        this.registerCoder(new ObjectCoder());
    }




}
