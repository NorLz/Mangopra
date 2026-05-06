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
        version = 3,
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
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingGrade1PricePerKg REAL");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingGrade2PricePerKg REAL");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingGrade3PricePerKg REAL");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN computedPricePerKg REAL");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingUnit TEXT");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingEffectiveDate TEXT");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingSourceLabel TEXT");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingRecordedAt TEXT");
            database.execSQL("ALTER TABLE analysis_sessions ADD COLUMN pricingSyncedAt INTEGER");
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
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
