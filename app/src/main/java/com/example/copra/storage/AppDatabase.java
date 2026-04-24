package com.example.copra.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                AnalysisSessionEntity.class,
                AnalysisItemEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN classificationModelKey TEXT");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN classificationModelName TEXT");
            database.execSQL("ALTER TABLE analysis_items ADD COLUMN classificationModelKey TEXT");
            database.execSQL("ALTER TABLE analysis_items ADD COLUMN classificationModelName TEXT");
        }
    };

    public abstract AnalysisSessionDao analysisSessionDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "analysis_history.db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
