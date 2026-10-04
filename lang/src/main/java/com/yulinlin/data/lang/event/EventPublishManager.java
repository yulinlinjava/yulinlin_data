package com.yulinlin.data.lang.event;

public class EventPublishManager extends AbstractEventPublishManager<EventPublishManager> {

    private static final EventPublishManager eventPublishManager = new EventPublishManager();

    private EventPublishManager() {

    }

    public static EventPublishManager instance(){
        return eventPublishManager;
    }


}
