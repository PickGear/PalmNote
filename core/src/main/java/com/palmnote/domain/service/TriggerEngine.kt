package com.palmnote.domain.service

import android.content.Context
import com.palmnote.R
import com.palmnote.data.db.entity.CrossLink
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import com.palmnote.domain.model.Money
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.util.AppLogger
import com.palmnote.ui.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
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
    ITEM_CREATED,
    ITEM_STATUS_CHANGED,
    DEPOSIT_MADE
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
    private val crossLinkRepo: CrossLinkRepository,
    private val scope: CoroutineScope
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val itemRepo: LifeItemRepository by lazy { itemRepoProvider.get() }

    private val rules: List<TriggerRule> = listOf(
        TriggerRule(
            triggerEvent = TriggerEvent.DEPOSIT_MADE,
            condition = { _, data ->
                val target = data["targetAmount"]?.let { (it as? JsonPrimitive)?.content?.let { v -> Money.parse(v)?.cents } } ?: 0L
                val current = (
                    data["currentAmount"]?.let { (it as? JsonPrimitive)?.content?.let { v -> Money.parse(v)?.cents } }
                        ?: data["saved_amount"]?.let { (it as? JsonPrimitive)?.content?.let { v -> Money.parse(v)?.cents } } ?: 0L
                    )
                target > 0 && current >= target
            },
            actions = { item ->
                // ⚠️ 这里原先还有一条 `CreateAutoLink(ASSET, targetId = item.id)` ——
                // 那是**错的**：拿「条目的 id」当「资产的 id」去建关联，会造出一条指向
                // 不存在资产的幽灵关联（详情页的关联计数会凭空 +1）。
                // 存钱达标要的是"完成 + 提醒"，与资产并无确定对应关系，故去掉该动作。
                listOf(
                    TriggerAction.UpdateStatus("COMPLETED"),
                    TriggerAction.ShowNotification(
                        context.getString(R.string.trigger_saving_goal_title),
                        context.getString(R.string.trigger_saving_goal_message, item.title)
                    )
                )
            }
        ),
        TriggerRule(
            triggerEvent = TriggerEvent.ITEM_STATUS_CHANGED,
            condition = { item, _ -> item.status == "COMPLETED" },
            // ⚠️ **当前不可达**：`TriggerEvent.ITEM_STATUS_CHANGED` 既没有发布方，
            // `TriggerEventConsumer` 也没有对应的分支。保留是因为它的动作（完成时提醒）
            // 本身合理 —— 但接它之前要先想清楚"每次完成都弹一条通知"是不是用户想要的。
            actions = { item ->
                listOf(
                    TriggerAction.ShowNotification(
                        context.getString(R.string.trigger_status_updated_title),
                        context.getString(R.string.trigger_status_updated_message, item.title)
                    )
                )
            }
        ),
    )

    fun evaluate(event: TriggerEvent, item: LifeItem) {
        scope.launch {
            try {
                val data = try {
                    json.decodeFromString<JsonObject>(item.fieldsData)
                } catch (e: Exception) {
                    AppLogger.w("TriggerEngine", "decode fieldsData failed", e)
                    JsonObject(emptyMap())
                }
                val matched = rules.filter { it.triggerEvent == event && it.condition(item, data) }
                for (rule in matched) {
                    for (action in rule.actions(item)) {
                        executeAction(action, item, data)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("TriggerEngine", "evaluate failed", e)
            }
        }
    }

    /** 通过 itemId 评估触发器（EventBus 模式） */
    fun evaluate(event: TriggerEvent, itemId: Long) {
        scope.launch {
            try {
                val itemRepo = itemRepoProvider.get()
                val item = itemRepo.getItemById(itemId) ?: return@launch
                evaluate(event, item)
            } catch (e: Exception) {
                AppLogger.e("TriggerEngine", "evaluate by id failed", e)
            }
        }
    }

    private suspend fun executeAction(action: TriggerAction, item: LifeItem, data: JsonObject) {
        when (action) {
            is TriggerAction.UpdateStatus -> itemRepo.updateStatus(item.id, action.newStatus)
            is TriggerAction.ShowNotification -> {
                NotificationHelper.show(context, "trigger_${item.id}", action.title, action.body)
            }
            is TriggerAction.CreateAutoLink -> {
                // targetId = 0 表示“关联当前记录”，而不是自关联；显式指定时才检查是否为自身。
                val targetId = action.targetId.takeIf { it > 0 } ?: item.id
                if (action.targetType != EntityType.ITEM || targetId != item.id) {
                    crossLinkRepo.createLink(
                        CrossLink(
                            sourceType = EntityType.ITEM,
                            sourceId = item.id,
                            targetType = action.targetType,
                            targetId = targetId,
                            linkType = action.linkType,
                            metadata = action.metadata,
                            isAutoLinked = true
                        )
                    )
                }
            }
            is TriggerAction.SetFieldValue -> {
                val newData = data + (action.key to JsonPrimitive(action.valueExpression))
                itemRepo.updateFieldsData(item.id, newData.toString())
            }
        }
    }
}
