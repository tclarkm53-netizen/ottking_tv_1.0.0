package com.ottking.devcode.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.ottking.devcode.network.ApiClient;

import java.util.concurrent.Executors;

@Database(entities = {CategoryEntity.class, ChannelEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract CategoryDao categoryDao();
    public abstract ChannelDao channelDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "ott_king_database"
                    )
                    .fallbackToDestructiveMigration()
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                if (INSTANCE != null) {
                                    INSTANCE.categoryDao().insertAll(ApiClient.getDefaultCategories());
                                    INSTANCE.channelDao().insertAll(ApiClient.getDefaultChannels());
                                }
                            });
                        }
                    })
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
