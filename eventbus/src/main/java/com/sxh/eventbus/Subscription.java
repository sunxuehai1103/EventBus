package com.sxh.eventbus;


import com.sxh.eventbus_annotation.SubscriberMethod;

/**
 * 封装在EventBus类中使用的订阅方法
 */
final class Subscription {

    /**
     * 订阅者
     * 如：MainActivity.class
     */
    final Object subscriber;
    /**
     * 订阅的方法
     * 如：onEvent(LoginEvent event)
     */
    final SubscriberMethod subscriberMethod;

    Subscription(Object subscriber, SubscriberMethod subscriberMethod) {
        this.subscriber = subscriber;
        this.subscriberMethod = subscriberMethod;
    }

    @Override
    public boolean equals(Object object) {
        // 必须重写方法，检测激活粘性事件重复调用（同一对象注册多个）
        if (object instanceof Subscription) {
            Subscription otherSubscription = (Subscription) object;
            // 删除官方：subscriber == otherSubscription.subscriber判断条件
            // 原因：粘性事件Bug，多次调用和移除时重现
            return subscriberMethod.equals(otherSubscription.subscriberMethod);
        } else {
            return false;
        }
    }
}
