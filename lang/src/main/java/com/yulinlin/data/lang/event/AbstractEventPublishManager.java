package com.yulinlin.data.lang.event;

import com.yulinlin.data.lang.util.ThreadUtil;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;


public abstract class AbstractEventPublishManager<E extends AbstractEventPublishManager<E>> {

   private final ConcurrentMap<Class<?>, CopyOnWriteArrayList<IEventHandler<Object>>> handlersByType;

    protected AbstractEventPublishManager() {
        this.handlersByType = new ConcurrentHashMap<>();
    }

    public E register(Collection<IEventHandler> handlers){
        for (IEventHandler handler : handlers) {
            register(handler);
        }
        return (E)this;
    }
    @SuppressWarnings("unchecked")
    public E register(IEventHandler handler){
        Class<?> type = handler.getEventClass();
        handlersByType.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
                .add((IEventHandler<Object>) handler);
        return (E)this;
    }

    /**
     * 发布事件
     * @param event
     */
    public void publish(Object event){
       List<IEventHandler<Object>> handlers = handlersByType.get(event.getClass());
       if (handlers == null) return;
        for (IEventHandler<Object> handler : handlers) {
                handler.handle(event);
        }
    }

    //发送异步事件
    public void asyncPublish(Object event){
        List<IEventHandler<Object>> handlers = handlersByType.get(event.getClass());
        if (handlers == null) return;
        for (IEventHandler<Object> handler : handlers) {
            ThreadUtil.submit(() -> handler.handle(event));
        }

    }

}
