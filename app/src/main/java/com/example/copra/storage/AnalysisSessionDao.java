package com.example.copra.storage;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface AnalysisSessionDao {
    @Insert
    long insertSession(AnalysisSessionEntity session);

    @Insert
    void insertItems(List<AnalysisItemEntity> items);

    @Query("SELECT * FROM analysis_sessions ORDER BY createdAt DESC")
    List<AnalysisSessionEntity> getAllSessions();

    @Transaction
    @Query("SELECT * FROM analysis_sessions WHERE id = :sessionId LIMIT 1")
    AnalysisSessionWithItems getSessionWithItems(long sessionId);

    @Query("DELETE FROM analysis_sessions WHERE id = :sessionId")
    void deleteSession(long sessionId);

    @Query("DELETE FROM analysis_sessions")
    void deleteAllSessions();
}
