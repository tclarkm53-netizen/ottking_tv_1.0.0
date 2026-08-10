package com.ottking.mobile.devcode;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.mobile.devcode.adapter.ChannelAdapter;
import com.ottking.mobile.devcode.database.AppDatabase;
import com.ottking.mobile.devcode.database.ChannelDao;
import com.ottking.mobile.devcode.database.ChannelEntity;

import java.util.List;

public class TvActivity extends AppCompatActivity {

    private RecyclerView rvTvGrid;
    private ChannelDao channelDao;
    private ChannelAdapter channelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv);

        rvTvGrid = findViewById(R.id.rvTvGrid);
        channelDao = AppDatabase.getInstance(this).channelDao();

        List<ChannelEntity> channels = channelDao.getAllChannels();
        channelAdapter = new ChannelAdapter(this, channels, true, null);
        rvTvGrid.setLayoutManager(new GridLayoutManager(this, 3));
        rvTvGrid.setAdapter(channelAdapter);
    }
}
