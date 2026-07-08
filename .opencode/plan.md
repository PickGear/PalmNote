# 修复 Moment.kt 硬编码中文字符串

## 问题分析

`Moment.kt` 是一个 Room `@Entity` data class，无法直接访问 `Context`，因此无法直接使用 `getString()`。硬编码的中文字符串位于以下几个计算属性中：

- `moodText` (行48-56): "开心", "不错", "一般", "难过", "糟糕"
- `weatherText` (行69-78): "晴", "多云", "雨", "雪", "大风", "雾"
- `categoryText` (行80-92): "旅行", "美食", "家庭", "工作", "朋友", "爱好", "自然", "宠物", "其他"
- `displayDate` (行110-122): "今天", "昨天", "X月X日"

## 参考方案

`Asset.kt` 已有类似处理模式（行105-122）：在 data class 外部定义 **扩展函数**，接收 `Context` 参数来返回本地化字符串。

## 修改计划

### 1. `values/strings.xml` — 添加 Moment 相关字符串

在 `</resources>` 前添加 Moment 模块字符串资源：

```xml
<!-- Moment - Mood -->
<string name="moment_mood_great">开心</string>
<string name="moment_mood_good">不错</string>
<string name="moment_mood_ok">一般</string>
<string name="moment_mood_bad">难过</string>
<string name="moment_mood_terrible">糟糕</string>

<!-- Moment - Weather -->
<string name="moment_weather_sunny">晴</string>
<string name="moment_weather_cloudy">多云</string>
<string name="moment_weather_rainy">雨</string>
<string name="moment_weather_snowy">雪</string>
<string name="moment_weather_windy">大风</string>
<string name="moment_weather_foggy">雾</string>

<!-- Moment - Category -->
<string name="moment_category_travel">旅行</string>
<string name="moment_category_food">美食</string>
<string name="moment_category_family">家庭</string>
<string name="moment_category_work">工作</string>
<string name="moment_category_friend">朋友</string>
<string name="moment_category_hobby">爱好</string>
<string name="moment_category_nature">自然</string>
<string name="moment_category_pet">宠物</string>
<string name="moment_category_custom">其他</string>

<!-- Moment - Date -->
<string name="moment_date_today">今天</string>
<string name="moment_date_yesterday">昨天</string>
<string name="moment_date_format">%1$d月%2$d日</string>
```

### 2. `values-en/strings.xml` — 添加英文翻译

在 `</resources>` 前添加对应英文字符串：

```xml
<!-- Moment - Mood -->
<string name="moment_mood_great">Happy</string>
<string name="moment_mood_good">Good</string>
<string name="moment_mood_ok">Okay</string>
<string name="moment_mood_bad">Sad</string>
<string name="moment_mood_terrible">Terrible</string>

<!-- Moment - Weather -->
<string name="moment_weather_sunny">Sunny</string>
<string name="moment_weather_cloudy">Cloudy</string>
<string name="moment_weather_rainy">Rainy</string>
<string name="moment_weather_snowy">Snowy</string>
<string name="moment_weather_windy">Windy</string>
<string name="moment_weather_foggy">Foggy</string>

<!-- Moment - Category -->
<string name="moment_category_travel">Travel</string>
<string name="moment_category_food">Food</string>
<string name="moment_category_family">Family</string>
<string name="moment_category_work">Work</string>
<string name="moment_category_friend">Friends</string>
<string name="moment_category_hobby">Hobby</string>
<string name="moment_category_nature">Nature</string>
<string name="moment_category_pet">Pets</string>
<string name="moment_category_custom">Other</string>

<!-- Moment - Date -->
<string name="moment_date_today">Today</string>
<string name="moment_date_yesterday">Yesterday</string>
<string name="moment_date_format">%1$s %2$d</string>
```

### 3. `Moment.kt` — 添加 Context 扩展函数

- 在 `Moment.kt` 文件底部（data class 之外），添加扩展函数（与 Asset.kt 行105-122 同模式）
- 保留原有 `moodText`/`weatherText`/`categoryText`/`displayDate` 属性（兼容无 Context 场景）
- 新增 `getMoodText(context)`/`getWeatherText(context)`/`getCategoryText(context)`/`getDisplayDate(context)` 扩展函数

```kotlin
import android.content.Context
import com.palmnote.R
import java.util.Calendar

fun Moment.getMoodText(context: Context): String = when (mood) {
    "GREAT" -> context.getString(R.string.moment_mood_great)
    "GOOD" -> context.getString(R.string.moment_mood_good)
    "OK" -> context.getString(R.string.moment_mood_ok)
    "BAD" -> context.getString(R.string.moment_mood_bad)
    "TERRIBLE" -> context.getString(R.string.moment_mood_terrible)
    else -> ""
}

fun Moment.getWeatherText(context: Context): String = when (weather) {
    "SUNNY" -> context.getString(R.string.moment_weather_sunny)
    "CLOUDY" -> context.getString(R.string.moment_weather_cloudy)
    "RAINY" -> context.getString(R.string.moment_weather_rainy)
    "SNOWY" -> context.getString(R.string.moment_weather_snowy)
    "WINDY" -> context.getString(R.string.moment_weather_windy)
    "FOGGY" -> context.getString(R.string.moment_weather_foggy)
    else -> ""
}

fun Moment.getCategoryText(context: Context): String = when (category) {
    "TRAVEL" -> context.getString(R.string.moment_category_travel)
    "FOOD" -> context.getString(R.string.moment_category_food)
    "FAMILY" -> context.getString(R.string.moment_category_family)
    "WORK" -> context.getString(R.string.moment_category_work)
    "FRIEND" -> context.getString(R.string.moment_category_friend)
    "HOBBY" -> context.getString(R.string.moment_category_hobby)
    "NATURE" -> context.getString(R.string.moment_category_nature)
    "PET" -> context.getString(R.string.moment_category_pet)
    "CUSTOM" -> context.getString(R.string.moment_category_custom)
    else -> ""
}

fun Moment.getDisplayDate(context: Context): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    val nowCal = Calendar.getInstance()
    return when {
        cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) ->
            context.getString(R.string.moment_date_today)
        cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) - 1 ->
            context.getString(R.string.moment_date_yesterday)
        else -> context.getString(
            R.string.moment_date_format,
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
```

### 4. 后续 UI 层调用者迁移

需要检查 UI 层哪些地方调用了 `moment.moodText`、`moment.weatherText`、`moment.categoryText`、`moment.displayDate`，将它们改为调用新的扩展函数版本。这些调用方本身有 Context，可以传入。

## 涉及修改的文件

| 文件 | 修改内容 |
|------|----------|
| `app/src/main/res/values/strings.xml` | 添加 Moment 模块字符串资源（约30条） |
| `app/src/main/res/values-en/strings.xml` | 添加 Moment 模块英文字符串资源（约30条） |
| `app/src/main/java/com/palmnote/data/db/entity/Moment.kt` | 添加 `import` 和4个 Context 扩展函数 |

## 验证

1. `./gradlew assembleDebug` 编译通过
2. 检查 UI 层调用处是否需要迁移到扩展函数版本
3. 切换语言验证英文翻译生效
