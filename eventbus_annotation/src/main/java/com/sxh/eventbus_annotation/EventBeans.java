package com.sxh.eventbus_annotation;

/**
 * 所有事件集合,比如MainActivity里包含了很多使用@Subscribe注解的方法
 */
public class EventBeans implements SubscriberInfo {

    /**
     * 订阅者对象Class，如：MainActivity.class
     */
    private final Class subscriberClass;
    /**
     * 订阅方法数组
     */
    private final SubscriberMethod[] methods;

    public EventBeans(Class subscriberClass, SubscriberMethod[] methods) {
        this.subscriberClass = subscriberClass;
        this.methods = methods;
    }

    @Override
    public Class<?> getSubscriberClass() {
        return subscriberClass;
    }

    @Override
    public synchronized SubscriberMethod[] getSubscriberMethods() {
        return methods;
    }
}
