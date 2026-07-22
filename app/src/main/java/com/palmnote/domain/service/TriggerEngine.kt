package com.palmnote.domain.service

import android.content.Context
import com.palmnote.R
import com.palmnote.data.db.entity.CrossLink
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.ui.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Provider

data class TriggerRule(
    val triggerEvent: TriggerEvent,
    val condition: (LifeItem, JsonObject) -> Boolean,
    val actions: (LifeItem) -> List<TriggerAction>
)

enum class TriggerEvent {
    ITEM_CREATED, ITEM_STATUS_CHANGED, DEPOSIT_MADE
}

sealed class TriggerAction {
    data class CreateAutoLink(val targetType: EntityType, val linkType: LinkType, val targetId: Long = 0, val metadata: String? = null) : TriggerAction()
    data class UpdateStatus(val newStatus: String) : TriggerAction()
    data class ShowNotification(val title: String, val body: String) : TriggerAction()
    data class SetFieldValue(val key: String, val valueExpression: String) : TriggerAction()
}

class TriggerEngine(
    private val context: Context,
    private val itemRepoProvider: Provider<LifeItemRepository>,
    private val crossLinkRepo: CrossLinkRepository
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val itemRepo: LifeItemRepository by lazy { itemRepoProvider.get() }

    private val rules: List<TriggerRule> = listOf(
        TriggerRule(
            triggerEvent = TriggerEvent.DEPOSIT_MADE,
            condition = { _, data ->
                val target = data["targetAmount"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: 0.0
                val current = (data["currentAmount"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
                    ?: data["saved_amount"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: 0.0)
                target > 0 && current >= target
            },
            actions = { item -> listOf(
                TriggerAction.UpdateStatus("COMPLETED"),
                TriggerAction.ShowNotification(
                    context.getString(R.string.trigger_saving_goal_title),
                    context.getString(R.string.trigger_saving_goal_message, item.title)
                ),
                TriggerAction.CreateAutoLink(EntityType.ASSET, LinkType.PART_OF, targetId = item.id, metadata = """{"reason":"saving_completed"}""")
            )}
        ),
        TriggerRule(
            triggerEvent = TriggerEvent.ITEM_STATUS_CHANGED,
            condition = { item, _ -> item.status == "COMPLETED" },
            actions = { item -> listOf(
                TriggerAction.ShowNotification(
                    context.getString(R.string.trigger_status_updated_title),
                    context.getString(R.string.trigger_status_updated_message, item.title)
                )
            )}
        ),
        TriggerRule(
            triggerEvent = TriggerEvent.ITEM_CREATED,
            condition = { _, _ -> true },
            actions = { item -> listOf(
                TriggerAction.CreateAutoLink(EntityType.ITEM, LinkType.RELATED_TO, item.id, """{"auto":true}""")
            )}
        ),
    )

    fun evaluate(event: TriggerEvent, item: LifeItem) {
        scope.launch {
            try {
                val data = try { json.decodeFromString<JsonObject>(item.fieldsData) } catch (_: Exception) { JsonObject(emptyMap()) }
                val matched = rules.filter { it.triggerEvent == event && it.condition(item, data) }
                for (rule in matched) {
                    for (action in rule.actions(item)) {
                        executeAction(action, item, data)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private suspend fun executeAction(action: TriggerAction, item: LifeItem, data: JsonObject) {
        when (action) {
            is TriggerAction.UpdateStatus -> itemRepo.updateStatus(item.id, action.newStatus)
            is TriggerAction.ShowNotification -> {
                NotificationHelper.show(context, "trigger_${item.id}", action.title, action.body)
            }
            is TriggerAction.CreateAutoLink -> {
                if (action.targetId != item.id) {
                    crossLinkRepo.createLink(CrossLink(
                        sourceType = EntityType.ITEM, sourceId = item.id,
                        targetType = action.targetType, targetId = action.targetId,
                        linkType = action.linkType, metadata = action.metadata, isAutoLinked = true
                    ))
                }
            }
            is TriggerAction.SetFieldValue -> {
                val newData = data + (action.key to JsonPrimitive(action.valueExpression))
                itemRepo.updateFieldsData(item.id, newData.toString())
            }
        }
    }
}
