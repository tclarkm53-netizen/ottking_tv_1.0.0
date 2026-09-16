package com.ottking.devcode.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.security.DatabaseKeyManager;

import net.sqlcipher.database.SupportFactory;

import java.util.concurrent.Executors;

@Database(entities = {CategoryEntity.class, ChannelEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "ott_king_encrypted.db";

    public abstract CategoryDao categoryDao();
    public abstract ChannelDao channelDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Context appContext = context.getApplicationContext();
                    byte[] passphrase = DatabaseKeyManager.getInstance(appContext).getDatabasePassphrase();
                    SupportFactory supportFactory = new SupportFactory(passphrase);

                    INSTANCE = Room.databaseBuilder(
                            appContext,
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .openHelperFactory(supportFactory)
                    .fallbackToDestructiveMigration()
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                if (INSTANCE != null) {
                                    INSTANCE.categoryDao().insertAll(ApiClient.getDefaultCategories());
                                    java.util.List<ChannelEntity> defaults = ApiClient.getDefaultChannels();
                                    java.util.List<ChannelEntity> encryptedList = new java.util.ArrayList<>();
                                    DatabaseKeyManager keyMgr = DatabaseKeyManager.getInstance(appContext);
                                    for (ChannelEntity ch : defaults) {
                                        encryptedList.add(new ChannelEntity(
                                                ch.id,
                                                ch.name,
                                                ch.logoUrl,
                                                keyMgr.encryptStreamUrl(ch.streamUrl),
                                                ch.categoryId,
                                                ch.isPremium,
                                                ch.streamType
                                        ));
                                    }
                                    INSTANCE.channelDao().insertAll(encryptedList);
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

