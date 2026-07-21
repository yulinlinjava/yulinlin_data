package com.yulinlin.elasticsearch.coder;

import com.yulinlin.data.core.coder.MapCoderManager;

public class ElasticCoderManager extends MapCoderManager {

    protected void init(){

        super.init();

        this.registerCoder(new MapCoder());
        this.registerCoder(new CollectionCoder());

        this.registerCoder(new ObjectCoder());
    }




}
