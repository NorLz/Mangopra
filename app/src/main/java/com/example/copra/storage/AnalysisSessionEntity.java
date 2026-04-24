package com.example.copra.storage;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "analysis_sessions")
public class AnalysisSessionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long createdAt;
    public String sourceType;
    public String fullImagePath;
    public String classificationModelKey;
    public String classificationModelName;
    public int grade1Count;
    public int grade2Count;
    public int grade3Count;
    public int detectionCount;
}
