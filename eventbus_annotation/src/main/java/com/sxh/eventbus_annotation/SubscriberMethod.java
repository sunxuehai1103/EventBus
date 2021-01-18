package com.sxh.eventbus_annotation;

import java.lang.reflect.Method;

/**
 * 事件订阅方法封装类，即封装下面代码段的信息
 *
 * @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
 * public void process(LoginEvent loginEvent) {
 *      //逻辑处理...
 * }
 */
public class SubscriberMethod {
    /**
     * 订阅方法名，如：process
     */
    private String methodName;
    /**
     * 订阅方法，用于最后的自动执行订阅方法，如：process对应的Method
     */
    private Method method;
    /**
     * 线程模式，如：ThreadMode.MAIN
     */
    private ThreadMode threadMode;
    /**
     * 事件对象Class，如：LoginEvent.class
     */
    private Class<?> eventTypeClass;
    /**
     * 是否粘性事件（实现思路：发送时存储，注册时判断粘性再激活）
     */
    private boolean sticky;

    public SubscriberMethod(Class subscriberClass, String methodName,
                            Class<?> eventTypeClass, ThreadMode threadMode, boolean sticky) {
        this.methodName = methodName;
        this.threadMode = threadMode;
        this.eventTypeClass = eventTypeClass;
        this.sticky = sticky;
        try {
            //通过方法名去反射拿到Method对象
            method = subscriberClass.getDeclaredMethod(methodName, eventTypeClass);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public String getMethodName() {
        return methodName;
    }

    public Method getMethod() {
        return method;
    }

    public ThreadMode getThreadMode() {
        return threadMode;
    }

    public Class<?> getEventTypeClass() {
        return eventTypeClass;
    }

    public boolean isSticky() {
        return sticky;
    }
}
