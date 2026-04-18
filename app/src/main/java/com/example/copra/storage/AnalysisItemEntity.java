package com.example.copra.storage;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "analysis_items",
        foreignKeys = @ForeignKey(
                entity = AnalysisSessionEntity.class,
                parentColumns = "id",
                childColumns = "sessionId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("sessionId")
        }
)
public class AnalysisItemEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long sessionId;
    public String cropImagePath;
    public float sourceLeft;
    public float sourceTop;
    public float sourceRight;
    public float sourceBottom;
    public String classificationLabel;
    public Float classificationConfidence;
    public String classificationStatus;
    public Long classificationMs;
    public int displayOrder;
}
