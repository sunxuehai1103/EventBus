package com.sxh.eventbus_compiler;

/**
 * 定义注解处理器里的常量
 */
public class Constants {

    public static final String SUBSCRIBE_ANNOTATION_TYPES = "com.sxh.eventbus_annotation.Subscribe";

    // APT生成类文件所属包名
    public static final String PACKAGE_NAME = "packageName";

    // APT生成类文件的类名
    public static final String CLASS_NAME = "className";

    // 所有的事件订阅方法，生成索引接口
    public static final String I_SUBSCRIBERINFO_INDEX = "com.sxh.eventbus_annotation.ISubscriberInfoIndex";

    // 全局属性名
    public static final String FIELD_NAME = "SUBSCRIBER_INDEX";

    // putItem方法的参数对象名
    public static final String PUTITEM_PARAMETER_NAME = "subscriberInfo";

    // 加入Map集合方法名
    public static final String PUTITEM_METHOD_NAME = "putItem";

    // getSubscriberInfo方法的参数对象名
    public static final String GETSUBSCRIBERINFO_PARAMETER_NAME = "subscriberClass";

    // 通过订阅者对象（MainActivity.class）获取所有订阅方法的方法名
    public static final String GETSUBSCRIBERINFO_METHOD_NAME = "getSubscriberInfo";
}
