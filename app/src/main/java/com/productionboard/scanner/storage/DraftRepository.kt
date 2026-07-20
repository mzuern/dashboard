package com.productionboard.scanner.storage

import android.content.Context
import com.productionboard.scanner.domain.ReviewRow

/** Keeps the in-progress review draft persisted so an app kill/backgrounding mid-review doesn't lose a scan. */
class DraftRepository(context: Context) {
    private val dao = ReportDatabase.get(context).draftDao()

    suspend fun load(): List<ReviewRow>? = dao.get()?.let { RowsJsonConverter.decode(it.rowsJson) }

    suspend fun save(rows: List<ReviewRow>) {
        dao.upsert(DraftEntity(rowsJson = RowsJsonConverter.encode(rows), updatedAt = System.currentTimeMillis()))
    }

    suspend fun clear() = dao.clear()
}

/** Local-only history of previously generated reports (never uploaded, never auto-sent). */
class ReportHistoryRepository(context: Context) {
    private val dao = ReportDatabase.get(context).reportHistoryDao()

    fun recent() = dao.recent()

    suspend fun latest(): ReportHistoryEntity? = dao.latest()

    suspend fun record(subject: String, body: String, rowCount: Int) {
        dao.insert(ReportHistoryEntity(subject = subject, body = body, createdAt = System.currentTimeMillis(), rowCount = rowCount))
    }

    suspend fun clearAll() = dao.clearAll()
}
