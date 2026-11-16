package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsDao {
    @Query("SELECT * FROM cached_audio ORDER BY createdAt DESC")
    fun observeAllCachedAudio(): Flow<List<CachedAudioEntity>>

    @Query("SELECT * FROM cached_audio WHERE cacheKey = :key LIMIT 1")
    suspend fun getCachedAudioByKey(key: String): CachedAudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedAudio(entity: CachedAudioEntity)

    @Query("UPDATE cached_audio SET hitCount = hitCount + 1 WHERE cacheKey = :key")
    suspend fun incrementCacheHit(key: String)

    @Query("DELETE FROM cached_audio WHERE cacheKey = :key")
    suspend fun deleteCachedAudioByKey(key: String)

    @Query("DELETE FROM cached_audio")
    suspend fun clearAllCachedAudio()

    @Query("SELECT COUNT(*) FROM cached_audio WHERE providerUsed = 'GEMINI_CLOUD' AND createdAt >= :sinceEpochMs")
    suspend fun countCloudRequestsSince(sinceEpochMs: Long): Int

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 150")
    fun observeDiagnosticLogs(): Flow<List<DiagnosticLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosticLog(log: DiagnosticLogEntity)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearDiagnosticLogs()

    @Query("SELECT * FROM pronunciation_rules ORDER BY id ASC")
    fun observePronunciationRules(): Flow<List<PronunciationRuleEntity>>

    @Query("SELECT * FROM pronunciation_rules WHERE enabled = 1 ORDER BY LENGTH(thaiPattern) DESC")
    suspend fun getActivePronunciationRules(): List<PronunciationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPronunciationRule(rule: PronunciationRuleEntity)

    @Update
    suspend fun updatePronunciationRule(rule: PronunciationRuleEntity)

    @Query("DELETE FROM pronunciation_rules WHERE id = :id")
    suspend fun deletePronunciationRule(id: Long)

    @Query("SELECT COUNT(*) FROM pronunciation_rules")
    suspend fun countPronunciationRules(): Int

    @Query("SELECT * FROM voice_profiles ORDER BY id ASC")
    fun observeVoiceProfiles(): Flow<List<VoiceProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceProfile(profile: VoiceProfileEntity)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteVoiceProfile(id: Long)

    @Query("SELECT COUNT(*) FROM voice_profiles")
    suspend fun countVoiceProfiles(): Int
}
