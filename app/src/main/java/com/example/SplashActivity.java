package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.database.AppDatabase;
import com.example.database.ChannelDao;
import com.example.utils.SampleData;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Prepopulate database safely
        try {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            ChannelDao dao = db.channelDao();
            if (dao.getChannelCount() == 0) {
                dao.insertAll(SampleData.getInitialChannels());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        }, 2200);
    }
}
