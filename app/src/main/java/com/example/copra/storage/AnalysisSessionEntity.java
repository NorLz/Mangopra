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
    public Double pricingGrade1PricePerKg;
    public Double pricingGrade2PricePerKg;
    public Double pricingGrade3PricePerKg;
    public Double computedPricePerKg;
    public String pricingUnit;
    public String pricingEffectiveDate;
    public String pricingSourceLabel;
    public String pricingRecordedAt;
    public Long pricingSyncedAt;
    public Double averageClassificationMs;
    public Long minClassificationMs;
    public Long maxClassificationMs;
    public Integer latencySampleCount;
}
