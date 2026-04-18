package com.example.copra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.copra.storage.AnalysisItemEntity
import com.example.copra.storage.AnalysisSessionEntity
import com.example.copra.storage.AnalysisSessionWithItems
import com.example.copra.storage.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnalysisHistoryRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "AnalysisHistoryRepo"

        @Volatile
        private var INSTANCE: AnalysisHistoryRepository? = null

        fun getInstance(context: Context): AnalysisHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalysisHistoryRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).analysisSessionDao()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val historyRootDir: File
        get() = File(appContext.filesDir, "analysis_history")

    fun saveSession(
        sourceType: String,
        fullImage: Bitmap,
        items: List<CapturedDetection>,
        onComplete: ((Long) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val immutableItems = items.toList()
        executor.execute {
            try {
                val sessionId = saveSessionInternal(sourceType, fullImage, immutableItems)
                mainHandler.post { onComplete?.invoke(sessionId) }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to save session", throwable)
                mainHandler.post { onError?.invoke(throwable) }
            }
        }
    }

    fun loadRecentSessions(
        onComplete: (List<AnalysisHistorySession>) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        executor.execute {
            try {
                val sessions = dao.getAllSessions().map { entity ->
                    AnalysisHistorySession(
                        id = entity.id,
                        createdAt = entity.createdAt,
                        sourceType = entity.sourceType,
                        fullImagePath = entity.fullImagePath,
                        grade1Count = entity.grade1Count,
                        grade2Count = entity.grade2Count,
                        grade3Count = entity.grade3Count,
                        detectionCount = entity.detectionCount
                    )
                }
                mainHandler.post { onComplete(sessions) }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to load recent sessions", throwable)
                mainHandler.post { onError?.invoke(throwable) }
            }
        }
    }

    fun loadSession(
        sessionId: Long,
        onComplete: (AnalysisHistorySession?) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        executor.execute {
            try {
                val session = dao.getSessionWithItems(sessionId)?.toDomain()
                mainHandler.post { onComplete(session) }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to load session $sessionId", throwable)
                mainHandler.post { onError?.invoke(throwable) }
            }
        }
    }

    fun deleteSession(
        sessionId: Long,
        onComplete: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        executor.execute {
            try {
                val session = dao.getSessionWithItems(sessionId)?.toDomain()
                session?.let {
                    deleteSessionFiles(it)
                    dao.deleteSession(sessionId)
                }
                mainHandler.post { onComplete?.invoke() }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to delete session files for $sessionId", throwable)
                mainHandler.post { onError?.invoke(throwable) }
            }
        }
    }

    fun deleteAllSessions(
        onComplete: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        executor.execute {
            try {
                if (historyRootDir.exists()) {
                    historyRootDir.deleteRecursively()
                }
                dao.deleteAllSessions()
                mainHandler.post { onComplete?.invoke() }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to delete all sessions", throwable)
                mainHandler.post { onError?.invoke(throwable) }
            }
        }
    }

    fun decodeBitmap(path: String, reqWidth: Int = 0, reqHeight: Int = 0): Bitmap? {
        return try {
            if (reqWidth <= 0 || reqHeight <= 0) {
                BitmapFactory.decodeFile(path)
            } else {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(path, options)
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to decode bitmap at $path", throwable)
            null
        }
    }

    private fun saveSessionInternal(
        sourceType: String,
        fullImage: Bitmap,
        items: List<CapturedDetection>
    ): Long {
        val timestamp = System.currentTimeMillis()
        val safeSourceType = sourceType.lowercase(Locale.US)
        val sessionDir = File(appContext.filesDir, "analysis_history/session_${timestamp}_$safeSourceType")
        if (!sessionDir.exists() && !sessionDir.mkdirs()) {
            throw IllegalStateException("Unable to create history directory: ${sessionDir.absolutePath}")
        }

        val fullImageFile = File(sessionDir, "full_image.jpg")
        writeBitmap(fullImage, fullImageFile)

        val readyItems = items.filter { it.classificationStatus == ClassificationStatus.READY }
        val grade1Count = readyItems.count { gradeBucket(it.classificationLabel) == 1 }
        val grade2Count = readyItems.count { gradeBucket(it.classificationLabel) == 2 }
        val grade3Count = readyItems.count { gradeBucket(it.classificationLabel) == 3 }

        val sessionEntity = AnalysisSessionEntity().apply {
            createdAt = timestamp
            this.sourceType = sourceType
            fullImagePath = fullImageFile.absolutePath
            this.grade1Count = grade1Count
            this.grade2Count = grade2Count
            this.grade3Count = grade3Count
            detectionCount = items.size
        }
        val sessionId = dao.insertSession(sessionEntity)

        val itemEntities = items.mapIndexed { index, item ->
            val cropFile = File(sessionDir, "crop_${(index + 1).toString().padStart(3, '0')}.jpg")
            writeBitmap(item.crop, cropFile)

            AnalysisItemEntity().apply {
                this.sessionId = sessionId
                cropImagePath = cropFile.absolutePath
                sourceLeft = item.sourceRect.left
                sourceTop = item.sourceRect.top
                sourceRight = item.sourceRect.right
                sourceBottom = item.sourceRect.bottom
                classificationLabel = item.classificationLabel
                classificationConfidence = item.classificationConfidence
                classificationStatus = item.classificationStatus.name
                classificationMs = item.classificationMs
                displayOrder = index
            }
        }
        dao.insertItems(itemEntities)
        return sessionId
    }

    private fun AnalysisSessionWithItems.toDomain(): AnalysisHistorySession {
        return AnalysisHistorySession(
            id = session.id,
            createdAt = session.createdAt,
            sourceType = session.sourceType,
            fullImagePath = session.fullImagePath,
            grade1Count = session.grade1Count,
            grade2Count = session.grade2Count,
            grade3Count = session.grade3Count,
            detectionCount = session.detectionCount,
            items = items.sortedBy { it.displayOrder }.map { item ->
                AnalysisHistoryItem(
                    id = item.id,
                    sessionId = item.sessionId,
                    cropImagePath = item.cropImagePath,
                    sourceRect = RectF(
                        item.sourceLeft,
                        item.sourceTop,
                        item.sourceRight,
                        item.sourceBottom
                    ),
                    classificationLabel = item.classificationLabel,
                    classificationConfidence = item.classificationConfidence,
                    classificationStatus = item.classificationStatus
                        ?.let { status -> ClassificationStatus.valueOf(status) }
                        ?: ClassificationStatus.FAILED,
                    classificationMs = item.classificationMs,
                    displayOrder = item.displayOrder
                )
            }
        )
    }

    private fun writeBitmap(bitmap: Bitmap, targetFile: File) {
        FileOutputStream(targetFile).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw IllegalStateException("Failed to write bitmap to ${targetFile.absolutePath}")
            }
            output.flush()
        }
    }

    private fun deleteSessionFiles(session: AnalysisHistorySession) {
        val sessionDir = File(session.fullImagePath).parentFile
        if (sessionDir != null && sessionDir.exists()) {
            sessionDir.deleteRecursively()
        } else {
            runCatching { File(session.fullImagePath).delete() }
            session.items.forEach { item ->
                runCatching { File(item.cropImagePath).delete() }
            }
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun formatDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("MM/dd/yy hh:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    fun buildCountSummary(session: AnalysisHistorySession): String {
        return "I:${session.grade1Count}  II:${session.grade2Count}  III:${session.grade3Count}"
    }

    fun gradeBucket(label: String?): Int {
        if (label.isNullOrBlank()) return 0
        val normalized = label.trim().lowercase(Locale.US)
        val compact = normalized.replace(Regex("[^a-z0-9]"), "")
        return when {
            normalized.contains("grade iii") || compact.contains("gradeiii") ||
                compact.contains("gradec") || compact == "c" -> 3

            normalized.contains("grade ii") || compact.contains("gradeii") ||
                compact.contains("gradeb") || compact == "b" -> 2

            normalized.contains("grade i") || compact.contains("gradei") ||
                compact.contains("gradea") || compact == "a" -> 1

            else -> 0
        }
    }
}
