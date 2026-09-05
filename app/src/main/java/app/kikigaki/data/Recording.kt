package app.kikigaki.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val filePath: String,
    val status: String // "recording", "processing", "done"
)
