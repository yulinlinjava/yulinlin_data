package com.yulinlin.data.lang.event;

import java.util.*;
import java.util.concurrent.CompletableFuture;


public abstract class AbstractEventPublishManager<E extends AbstractEventPublishManager<E>> {

   private Map<Class,List<IEventHandler>> hashMap ;

    protected AbstractEventPublishManager() {
        this.hashMap = new HashMap<>();
    }

    public E register(Collection<IEventHandler> handlers){
        for (IEventHandler handler : handlers) {
            register(handler);
        }
        return (E)this;
    }
    public E register(IEventHandler handler){
        Class type =  handler.getEventClass();
        List<IEventHandler> handlers =  hashMap.get(type);
        if(handlers == null){
            handlers = new ArrayList<>();
            hashMap.put(type,handlers);
        }
        handlers.add(handler);
        return (E)this;
    }

    /**
     * 发布事件
     * @param event
     */
    public void publish(Object event){
       List<IEventHandler> handlers =  hashMap.get(event.getClass());
       if(handlers == null){
          return;
       }
        for (IEventHandler handler : handlers) {
                handler.handle(event);
        }
    }

    //发送异步事件
    public void asyncPublish(Object event){
        List<IEventHandler> handlers =  hashMap.get(event.getClass());
        for (IEventHandler handler : handlers) {
            CompletableFuture.runAsync(() -> {
                handler.handle(event);
            });
        }

    }

}
