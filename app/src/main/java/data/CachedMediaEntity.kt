package com.fireflicker.fireplex2.data

import androidx.room.Entity

@Entity(
    tableName = "cached_media",
    primaryKeys = ["categoryKey", "mediaKey"]
)
data class CachedMediaEntity(
    val categoryKey: String,
    val mediaKey: String,
    val position: Int,
    val cachedAt: Long,
    val ratingKey: String,
    val parentRatingKey: String,
    val grandparentRatingKey: String,
    val key: String,
    val title: String,
    val type: String,
    val summary: String,
    val thumb: String,
    val art: String,
    val year: String,
    val contentRating: String,
    val durationMs: Long,
    val viewOffsetMs: Long,
    val addedAt: Long,
    val partKey: String
) {
    fun toMediaItem(): PlexMediaItem {
        return PlexMediaItem(
            ratingKey = ratingKey,
            parentRatingKey = parentRatingKey,
            grandparentRatingKey = grandparentRatingKey,
            key = key,
            title = title,
            type = type,
            summary = summary,
            thumb = thumb,
            art = art,
            year = year,
            contentRating = contentRating,
            durationMs = durationMs,
            viewOffsetMs = viewOffsetMs,
            addedAt = addedAt,
            partKey = partKey,
            subtitles = emptyList()
        )
    }

    companion object {
        fun from(categoryKey: String, position: Int, item: PlexMediaItem): CachedMediaEntity {
            return CachedMediaEntity(
                categoryKey = categoryKey,
                mediaKey = item.ratingKey.ifBlank { item.key.ifBlank { "$position:${item.title}" } },
                position = position,
                cachedAt = System.currentTimeMillis(),
                ratingKey = item.ratingKey,
                parentRatingKey = item.parentRatingKey,
                grandparentRatingKey = item.grandparentRatingKey,
                key = item.key,
                title = item.title,
                type = item.type,
                summary = item.summary,
                thumb = item.thumb,
                art = item.art,
                year = item.year,
                contentRating = item.contentRating,
                durationMs = item.durationMs,
                viewOffsetMs = item.viewOffsetMs,
                addedAt = item.addedAt,
                partKey = item.partKey
            )
        }
    }
}
