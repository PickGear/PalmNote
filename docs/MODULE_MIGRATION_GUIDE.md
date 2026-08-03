# 模块拆分文件迁移指南

## 模块结构

```
PalmNote/
├── app/                    # 入口 + 导航 + 设置 + 搜索 + 备份 + Worker
├── core/                   # 共享基础设施
│   ├── database/           # Entity + DAO + Migration + DbKeyStore
│   ├── domain/             # Repository 接口 + EventBus + Service + Util
│   └── ui/                 # 通用组件 + 主题 + Widget + Lock
├── feature/
│   ├── bills/              # 记账模块
│   ├── asset/              # 物品模块
│   └── life/               # 生活模块
└── ppocr-sdk/              # OCR（已独立）
```

## 迁移步骤（在 Android Studio 中执行）

### 第 1 步：core/database（Entity + DAO + Migration）

**移动文件：**
```
app/src/main/java/com/palmnote/data/db/ → core/src/main/java/com/palmnote/core/database/
```

**更新 import：**
```
com.palmnote.data.db.entity.Xxx → com.palmnote.core.database.entity.Xxx
com.palmnote.data.db.dao.Xxx → com.palmnote.core.database.dao.Xxx
com.palmnote.data.db.AppDatabase → com.palmnote.core.database.AppDatabase
com.palmnote.data.db.DbKeyStore → com.palmnote.core.database.DbKeyStore
com.palmnote.data.db.EncryptedOpenHelperFactory → com.palmnote.core.database.EncryptedOpenHelperFactory
```

### 第 2 步：core/domain（Repository 接口 + EventBus + Service + Util）

**移动文件：**
```
app/src/main/java/com/palmnote/domain/ → core/src/main/java/com/palmnote/core/domain/
```

**更新 import：**
```
com.palmnote.domain.repository.Xxx → com.palmnote.core.domain.repository.Xxx
com.palmnote.domain.event.Xxx → com.palmnote.core.domain.event.Xxx
com.palmnote.domain.service.Xxx → com.palmnote.core.domain.service.Xxx
com.palmnote.domain.model.Xxx → com.palmnote.core.domain.model.Xxx
com.palmnote.domain.util.Xxx → com.palmnote.core.domain.util.Xxx
```

### 第 3 步：core/ui（通用组件 + 主题）

**移动文件：**
```
app/src/main/java/com/palmnote/ui/components/ → core/src/main/java/com/palmnote/core/ui/components/
app/src/main/java/com/palmnote/ui/theme/ → core/src/main/java/com/palmnote/core/ui/theme/
app/src/main/java/com/palmnote/ui/lock/ → core/src/main/java/com/palmnote/core/ui/lock/
app/src/main/java/com/palmnote/ui/widget/ → core/src/main/java/com/palmnote/core/ui/widget/
```

### 第 4 步：feature/bills

**移动文件：**
```
app/src/main/java/com/palmnote/ui/bills/ → feature/bills/src/main/java/com/palmnote/feature/bills/ui/
app/src/main/java/com/palmnote/feature/bills/usecase/ → feature/bills/src/main/java/com/palmnote/feature/bills/usecase/
app/src/main/java/com/palmnote/data/repository/BillRepositoryImpl.kt → feature/bills/src/main/java/com/palmnote/feature/bills/repository/
app/src/main/java/com/palmnote/data/export/ → feature/bills/src/main/java/com/palmnote/feature/bills/export/
app/src/main/java/com/palmnote/data/ocr/BillOcrParser.kt → feature/bills/src/main/java/com/palmnote/feature/bills/ocr/
```

### 第 5 步：feature/asset

**移动文件：**
```
app/src/main/java/com/palmnote/ui/asset/ → feature/asset/src/main/java/com/palmnote/feature/asset/ui/
app/src/main/java/com/palmnote/feature/asset/usecase/ → feature/asset/src/main/java/com/palmnote/feature/asset/usecase/
app/src/main/java/com/palmnote/data/repository/AssetRepositoryImpl.kt → feature/asset/src/main/java/com/palmnote/feature/asset/repository/
```

### 第 6 步：feature/life

**移动文件：**
```
app/src/main/java/com/palmnote/ui/life/ → feature/life/src/main/java/com/palmnote/feature/life/ui/
app/src/main/java/com/palmnote/feature/life/usecase/ → feature/life/src/main/java/com/palmnote/feature/life/usecase/
app/src/main/java/com/palmnote/data/repository/*Life*.kt → feature/life/src/main/java/com/palmnote/feature/life/repository/
app/src/main/java/com/palmnote/data/repository/*Achievement*.kt → feature/life/src/main/java/com/palmnote/feature/life/repository/
app/src/main/java/com/palmnote/data/repository/*CrossLink*.kt → feature/life/src/main/java/com/palmnote/feature/life/repository/
app/src/main/java/com/palmnote/data/repository/*FocusRecord*.kt → feature/life/src/main/java/com/palmnote/feature/life/repository/
app/src/main/java/com/palmnote/data/repository/*Plan*.kt → feature/life/src/main/java/com/palmnote/feature/life/repository/
app/src/main/java/com/palmnote/data/repository/*Mood*.kt → feature/life/src/main/java/com/palmnote/feature/life/repository/
app/src/main/java/com/palmnote/data/repository/*Moment*.kt → feature/life/src/main/java/com/palmnote/feature/life/repository/
```

### 第 7 步：保留在 app 模块的文件

```
app/src/main/java/com/palmnote/MainActivity.kt
app/src/main/java/com/palmnote/PalmNoteApp.kt
app/src/main/java/com/palmnote/ui/navigation/
app/src/main/java/com/palmnote/ui/dashboard/
app/src/main/java/com/palmnote/ui/search/
app/src/main/java/com/palmnote/ui/settings/
app/src/main/java/com/palmnote/ui/backup/
app/src/main/java/com/palmnote/ui/notification/
app/src/main/java/com/palmnote/data/datastore/PreferencesManager.kt
app/src/main/java/com/palmnote/data/lock/AppLockManager.kt
app/src/main/java/com/palmnote/data/backup/
app/src/main/java/com/palmnote/data/worker/
app/src/main/java/com/palmnote/data/sync/
app/src/main/java/com/palmnote/di/HiltModules.kt
```

## 注意事项

1. **逐模块迁移**：每迁移一个模块后验证编译通过再继续
2. **import 路径**：使用 Android Studio 的 Refactor > Move 功能自动更新引用
3. **Hilt Module**：每个 feature 模块需要自己的 Hilt Module 文件
4. **Room Schema**：core 模块的 schemas 目录需要配置正确
5. **feature 之间不依赖**：feature 模块之间不能互相 import
