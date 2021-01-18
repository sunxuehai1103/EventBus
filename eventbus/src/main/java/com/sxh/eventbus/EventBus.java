package com.sxh.eventbus;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sxh.eventbus_annotation.ISubscriberInfoIndex;
import com.sxh.eventbus_annotation.SubscriberInfo;
import com.sxh.eventbus_annotation.SubscriberMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EventBus框架使用入口
 */
public class EventBus {

    private volatile static EventBus sInstance;
    /**
     * APT生成索引类文件的实例，这个很重要，需要通过addIndex方法设置进去，否则无法从APT文件里找，这样就避免使用反射了
     */
    private ISubscriberInfoIndex subscriberInfoIndexes;
    /**
     * 订阅者订阅事件类型集合，比如：订阅者MainActivity订阅了哪些Event
     * key：订阅者，如MainActivity.class
     * value：订阅事件的类型集合，如LoginEvent、LogoutEvent、UserInfoChangeEvent等
     */
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    /**
     * 方法缓存
     * key：订阅者，如MainActivity.class
     * value：订阅方法集合
     */
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
    /**
     * 事件订阅方法集合
     * key：事件类型，如LoginEvent.class
     * value：所有订阅者中订阅该事件的方法集合，如MainActivity里的onEvent(LoginEvent event)方法、SecondActivity里的onEvent(LoginEvent event)方法
     */
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    /**
     * 粘性事件缓存
     * key：事件类型对应的class，如LoginEvent.class
     * value：如LoginEvent
     */
    private final Map<Class<?>, Object> stickyEvents;
    /**
     * 切换到主线程，使用handler实现
     */
    private final Handler handler;
    /**
     * 切换到子线程，使用线程池实现
     */
    private final ExecutorService executorService;

    private EventBus() {
        //初始化
        typesBySubscriber = new HashMap<>();
        subscriptionsByEventType = new HashMap<>();
        stickyEvents = new HashMap<>();
        handler = new Handler(Looper.getMainLooper());
        executorService = Executors.newCachedThreadPool();
    }

    public static EventBus getDefault() {
        if (sInstance == null) {
            synchronized (EventBus.class) {
                if (sInstance == null) {
                    sInstance = new EventBus();
                }
            }
        }
        return sInstance;
    }

    /**
     * 添加APT生成索引类的实例对象，避免去反射
     */
    public void addIndex(ISubscriberInfoIndex index) {
        subscriberInfoIndexes = index;
    }

    /**
     * 订阅
     *
     * @param subscriber 订阅者，如MainActivity
     */
    public void register(Object subscriber) {
        //获取订阅者的class，如MainActivity.class
        Class<?> subscriberClass = subscriber.getClass();
        //寻找订阅者（MainActivity.class）订阅方法集合
        List<SubscriberMethod> subscriberMethods = findSubscriberMethods(subscriberClass);
        //加锁，保证线程安全
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                //遍历订阅方法集合，依次订阅
                subscribe(subscriber, subscriberMethod);
            }
        }
    }

    /**
     * 寻找订阅者（如MainActivity.class）里订阅方法集合
     */
    private List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        //第一步：从方法缓存中读取
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        //找到了缓存，直接返回
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        //第二步：从APT生成的类文件中寻找
        subscriberMethods = findUsingInfo(subscriberClass);
        if (subscriberMethods != null) {
            //找到了，直接放入方法缓存，下次就不用去文件里找了
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
        }
        return subscriberMethods;
    }

    /**
     * 实际订阅方法
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        //获取订阅方法事件类型，如：LoginEvent.class
        Class<?> eventType = subscriberMethod.getEventTypeClass();
        //创建Subscription对象，用于临时存储订阅者和订阅方法
        Subscription subscription = new Subscription(subscriber, subscriberMethod);
        //读取事件订阅方法集合缓存
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            // 初始化集合
            subscriptions = new CopyOnWriteArrayList<>();
            // 存入缓存
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(subscription)) {
                Log.e("sxh", subscriber.getClass() + "重复注册粘性事件！");
                // 执行多次粘性事件，但不添加到集合，避免订阅方法多次执行
                sticky(subscriberMethod, eventType, subscription);
                return;
            }
        }
        //添加
        subscriptions.add(subscription);

        //订阅者类型集合，比如：订阅者MainActivity订阅了哪些类型的事件
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            //存入缓存
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        //将事件存入订阅者对应的List列表里
        subscribedEvents.add(eventType);
        //处理粘性事件
        sticky(subscriberMethod, eventType, subscription);
    }

    /**
     * 粘性事件单独处理
     *
     * @param subscriberMethod
     * @param eventType
     * @param subscription
     */
    private void sticky(SubscriberMethod subscriberMethod, Class<?> eventType, Subscription subscription) {
        //粘性事件是指之前发送了某个粘性事件，当时订阅者还未订阅，这时候只要订阅者一订阅，就可以收到之前发出的事件
        if (subscriberMethod.isSticky()) {
            //从粘性事件的缓存里找
            Object stickyEvent = stickyEvents.get(eventType);
            //如果存在该事件，就直接触发当前订阅者订阅该粘性事件的方法
            if (stickyEvent != null) {
                postToSubscription(subscription, stickyEvent);
            }
        }
    }

    /**
     * 从APT生成的类文件中寻找订阅方法集合
     */
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        //检查是否添加了APT文件生成的索引，即APT生成类的实例化对象
        if (subscriberInfoIndexes == null) {
            throw new RuntimeException("未添加索引，请调用addIndex()方法添加APT生成索引类的实例对象");
        }
        //在索引对象里，找订阅方法集合，比如用MainActivity.class去找MainActivity所有订阅方法
        SubscriberInfo info = subscriberInfoIndexes.getSubscriberInfo(subscriberClass);
        //返回订阅方法的集合
        if (info != null) return Arrays.asList(info.getSubscriberMethods());
        return null;
    }

    /**
     * 是否订阅
     *
     * @param subscriber
     * @return
     */
    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /**
     * 解除订阅
     *
     * @param subscriber
     */
    public synchronized void unregister(Object subscriber) {
        //从缓存中移除
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            //移除前清空集合
            subscribedTypes.clear();
            typesBySubscriber.remove(subscriber);
        }
    }

    /**
     * 发送粘性事件
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            //加入粘性事件缓存集合
            stickyEvents.put(event.getClass(), event);
        }
        // 这里就是解决源码里的bug：只要参数匹配，粘性/非粘性订阅方法全部执行
        // post(event);
    }

    /**
     * 获取指定类型的粘性事件
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            //直接在粘性事件缓存找
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * 移除指定类型的粘性事件
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            //直接在粘性事件缓存移除
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * 移除所有粘性事件
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            //直接清理粘性事件缓存
            stickyEvents.clear();
        }
    }

    /**
     * 发送事件
     *
     * @param event
     */
    public void post(Object event) {
        postSingleEventForEventType(event, event.getClass());
    }

    /**
     * 指定事件类型发布
     */
    private void postSingleEventForEventType(Object event, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        //加锁，保证线程安全
        synchronized (this) {
            //从事件订阅方法集合里找出所有订阅该事件的方法
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                //依次遍历执行
                postToSubscription(subscription, event);
            }
        }
    }

    /**
     * 执行订阅事件的方法，主要处理线程切换工作
     *
     * @param subscription 订阅者封装对象，包含MainActivity里的onEvent(LoginEvent event)方法
     * @param event        事件对象
     */
    private void postToSubscription(final Subscription subscription, final Object event) {
        //线程切换操作
        switch (subscription.subscriberMethod.getThreadMode()) {
            case POSTING:
                //订阅、发布在同一线程，直接执行
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    //订阅在主线程，发布在主线程，直接执行
                    invokeSubscriber(subscription, event);
                } else {
                    //订阅在主线程，发布在子线程，通过handler切换到主线程
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            invokeSubscriber(subscription, event);
                        }
                    });
                }
                break;
            case ASYNC:
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    //订阅在子线程，发布在主线程，通过缓存线程池切到子线程去执行
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            invokeSubscriber(subscription, event);
                        }
                    });
                } else {
                    //订阅在是子线程，发布在子线程，直接执行
                    invokeSubscriber(subscription, event);
                }
                break;
            default:
                throw new IllegalStateException("线程配置异常：" + subscription.subscriberMethod.getThreadMode());
        }
    }

    /**
     * 执行订阅方法，即onEvent(LoginEvent event)
     */
    private void invokeSubscriber(Subscription subscription, Object event) {
        try {
            //最终通过反射执行方法
            subscription.subscriberMethod.getMethod().invoke(subscription.subscriber, event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
