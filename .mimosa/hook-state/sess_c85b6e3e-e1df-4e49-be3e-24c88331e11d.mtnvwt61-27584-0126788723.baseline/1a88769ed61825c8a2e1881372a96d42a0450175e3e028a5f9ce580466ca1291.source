package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.palmnote.domain.model.EntityType
import androidx.compose.runtime.Immutable
import com.palmnote.domain.model.LinkType

@Entity(
    tableName = "cross_links",
    indices = [
        Index(value = ["sourceId"], name = "idx_links_source_id"),
        Index(value = ["sourceType", "sourceId"], name = "idx_links_source"),
        Index(value = ["targetType", "targetId"], name = "idx_links_target"),
        Index(value = ["linkType"], name = "idx_links_type")
    ],
    foreignKeys = [
        ForeignKey(
            entity = LifeItem::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
@Immutable
data class CrossLink(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceType: EntityType,
    val sourceId: Long,
    val targetType: EntityType,
    val targetId: Long,
    val linkType: LinkType,
    val metadata: String? = null,
    val isAutoLinked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
