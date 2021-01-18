package com.sxh.eventbus;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.sxh.eventbus_annotation.Subscribe;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //重要！！！添加APT生成索引类的实例对象
        EventBus.getDefault().addIndex(new EventBusIndex());
        EventBus.getDefault().register(this);
    }

    @Subscribe
    public void onEventLogin(LoginEvent loginEvent) {
        Log.i("sxh","------------- 接收到了LoginEvent");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    public void jumpToSecondActivity(View view) {
        Intent intent = new Intent(MainActivity.this, SecondActivity.class);
        startActivity(intent);
    }

    public void sendStickyEvent(View view) {
        EventBus.getDefault().postSticky(new LogoutEvent());
    }
}