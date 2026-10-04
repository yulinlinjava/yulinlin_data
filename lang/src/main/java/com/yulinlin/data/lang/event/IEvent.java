package com.yulinlin.data.lang.event;

public interface IEvent {


    default void publish(){
        EventPublishManager.instance().publish(this);
    }


    default void asyncPublish(){
        EventPublishManager.instance().asyncPublish(this);
    }

}
