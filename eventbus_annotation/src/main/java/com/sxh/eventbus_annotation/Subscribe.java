package com.sxh.eventbus_annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Subscribe {

    //线程模式，默认推荐POSTING，避免切换线程的操作
    ThreadMode threadMode() default ThreadMode.POSTING;

    //是否使用粘性事件
    boolean sticky() default false;

    //事件订阅优先级，在同一个线程中。数值越大优先级越高。
    int priority() default 0;
}
