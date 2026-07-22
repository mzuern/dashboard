package com.productionboard.scanner.storage

import android.content.Context
import com.productionboard.scanner.domain.ReviewRow

/** Keeps the in-progress candidate rows persisted so an app kill/backgrounding mid-review doesn't lose the current photo batch. */
class DraftRepository(context: Context) {
    private val dao = DraftDatabase.get(context).draftDao()

    suspend fun load(): List<ReviewRow>? = dao.get()?.let { RowsJsonConverter.decode(it.rowsJson) }

    suspend fun save(rows: List<ReviewRow>) {
        dao.upsert(DraftEntity(rowsJson = RowsJsonConverter.encode(rows), updatedAt = System.currentTimeMillis()))
    }

    suspend fun clear() = dao.clear()
}
