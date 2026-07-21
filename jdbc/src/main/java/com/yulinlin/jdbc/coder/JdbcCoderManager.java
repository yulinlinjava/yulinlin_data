package com.yulinlin.jdbc.coder;

import com.yulinlin.data.core.coder.MapCoderManager;

public class JdbcCoderManager extends MapCoderManager {


    public JdbcCoderManager() {
        super();

    }

    protected void init(){
        super.init();
        this.registerCoder(new CollectionCoder());

        this.registerCoder(new MapCoder());
        this.registerCoder(new ObjectCoder());

    }



}
