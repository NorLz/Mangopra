package com.example.copra.storage;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class AnalysisSessionWithItems {
    @Embedded
    public AnalysisSessionEntity session;

    @Relation(parentColumn = "id", entityColumn = "sessionId")
    public List<AnalysisItemEntity> items;
}
