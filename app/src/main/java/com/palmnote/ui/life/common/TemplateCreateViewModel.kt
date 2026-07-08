package com.palmnote.ui.life.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import javax.inject.Inject

@HiltViewModel
class TemplateCreateViewModel @Inject constructor(
    private val templateRepo: LifeTemplateRepository
) : ViewModel() {

    fun createTemplate(
        name: String,
        category: String,
        description: String,
        icon: String,
        color: String,
        fields: List<TemplateField>,
        layout: String
    ) {
        viewModelScope.launch {
            try {
                val fieldsConfig = JsonArray(fields.map { field ->
                    buildJsonObject {
                        put("key", field.key)
                        put("label", field.label)
                        put("type", field.type)
                        put("required", field.required)
                        if (field.options.isNotEmpty()) {
                            put("options", JsonArray(field.options.map { JsonPrimitive(it) }))
                        }
                    }
                })

                val statusFlowConfig = buildJsonObject {
                    put("statuses", buildJsonArray {
                        addJsonObject {
                            put("key", "ACTIVE")
                            put("label", "\u8FDB\u884C\u4E2D")
                            put("color", color)
                            put("isDefault", true)
                        }
                        addJsonObject {
                            put("key", "COMPLETED")
                            put("label", "\u5DF2\u5B8C\u6210")
                            put("color", "#50C890")
                        }
                        addJsonObject {
                            put("key", "ARCHIVED")
                            put("label", "\u5DF2\u5F52\u6863")
                            put("color", "#85808a")
                        }
                    })
                    put("transitions", buildJsonArray {
                        addJsonObject {
                            put("from", "ACTIVE")
                            put("to", "COMPLETED")
                            put("trigger", "manual")
                        }
                        addJsonObject {
                            put("from", "COMPLETED")
                            put("to", "ARCHIVED")
                            put("trigger", "manual")
                        }
                    })
                }

                val linkConfig = buildJsonObject {
                    put("allowBillLink", true)
                    put("allowAssetLink", true)
                    put("allowItemLink", true)
                }

                templateRepo.insertTemplate(LifeTemplate(
                    name = name,
                    category = category,
                    icon = icon,
                    color = color,
                    description = description,
                    fieldsConfig = fieldsConfig.toString(),
                    layoutType = layout,
                    availableLayouts = JsonArray(listOf(JsonPrimitive(layout))).toString(),
                    statusFlowConfig = statusFlowConfig.toString(),
                    linkConfig = linkConfig.toString(),
                    isBuiltin = false
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
