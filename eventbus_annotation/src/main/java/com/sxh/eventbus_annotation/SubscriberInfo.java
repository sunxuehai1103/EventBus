package com.sxh.eventbus_annotation;

/**
 * 订阅信息接口
 */
public interface SubscriberInfo {

    //订阅的类，比如：MainActivity
    Class<?> getSubscriberClass();

    //获取订阅所属类中所有订阅事件的方法
    SubscriberMethod[] getSubscriberMethods();
}
