package com.surendramaran.yolov8tflite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ArcheryDao {
    @Insert
    fun insertSession(session: Session): Long

    @Update
    fun updateSession(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    fun getSession(sessionId: Long): Session?

    @Query("SELECT * FROM sessions ORDER BY dateTimestamp DESC")
    fun getAllSessions(): List<Session>

    @Query("SELECT * FROM sessions WHERE isDuel = 0 ORDER BY dateTimestamp DESC")
    fun getTrainingSessions(): List<Session>

    @Insert
    fun insertRound(round: Round): Long

    @Query("SELECT * FROM rounds WHERE sessionId = :sessionId ORDER BY roundNumber ASC")
    fun getRoundsForSession(sessionId: Long): List<Round>
    
    // Para la edición manual
    @Update
    fun updateRound(round: Round)
    
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    fun deleteSession(sessionId: Long)

    @Query("DELETE FROM rounds WHERE id = :roundId")
    fun deleteRound(roundId: Long)
}
