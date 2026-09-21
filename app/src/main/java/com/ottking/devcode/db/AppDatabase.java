package com.ottking.devcode.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.security.DatabaseKeyManager;

import java.util.concurrent.Executors;

@Database(entities = {CategoryEntity.class, ChannelEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "ott_king_secure.db";

    public abstract CategoryDao categoryDao();
    public abstract ChannelDao channelDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();

                    INSTANCE = Room.databaseBuilder(
                            appContext,
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

