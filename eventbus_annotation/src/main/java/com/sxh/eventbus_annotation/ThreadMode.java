package com.sxh.eventbus_annotation;

/**
 * 订阅线程
 */
public enum ThreadMode {

    //发布线程
    POSTING,

    //主线程
    MAIN,

    //异步线程
    ASYNC
}
