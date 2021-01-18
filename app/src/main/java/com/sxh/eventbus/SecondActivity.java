package com.sxh.eventbus;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.sxh.eventbus_annotation.Subscribe;

import androidx.appcompat.app.AppCompatActivity;

public class SecondActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        EventBus.getDefault().register(this);
    }

    public void sendLoginEvent(View view) {
        EventBus.getDefault().post(new LoginEvent());
    }

    @Subscribe(sticky = true)
    public void onEventLogout(LogoutEvent logoutEvent) {
        Log.i("sxh","------------- 接收到了LogoutEvent");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }
}