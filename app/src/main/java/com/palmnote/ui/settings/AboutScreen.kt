package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.about_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.about_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_description_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.about_features),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val features = listOf(
                        stringResource(R.string.about_feature_asset),
                        stringResource(R.string.about_feature_bill),
                        stringResource(R.string.about_feature_goal),
                        stringResource(R.string.about_feature_anniversary),
                        stringResource(R.string.about_feature_moment)
                    )

                    features.forEach { feature ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusActive,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(feature, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.about_tech_stack),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kotlin · Jetpack Compose · Room · Navigation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.about_legal),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToPrivacy() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.about_privacy_policy), style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToTerms() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.about_terms_of_service), style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.about_made_with),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isZh = context.resources.configuration.locales[0].language == "zh"
    val lines = getPrivacyPolicyLines(isZh)
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.about_privacy_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    style = if (isPrivacyHeading(line, isZh))
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isZh = context.resources.configuration.locales[0].language == "zh"
    val lines = getTermsOfServiceLines(isZh)
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.about_terms_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    style = if (isTermsHeading(line, isZh))
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

private fun isPrivacyHeading(line: String, isZh: Boolean): Boolean {
    return if (isZh) {
        line.startsWith("一、") || line.startsWith("二、") || line.startsWith("三、") || line.startsWith("四、") || line.startsWith("五、") || line.startsWith("六、") || line.startsWith("七、") || line.startsWith("八、") || line.startsWith("九、") || line.endsWith("隐私政策")
    } else {
        line.matches(Regex("^\\d+\\..*")) || line == "Privacy Policy"
    }
}

private fun isTermsHeading(line: String, isZh: Boolean): Boolean {
    return if (isZh) {
        line.startsWith("一、") || line.startsWith("二、") || line.startsWith("三、") || line.startsWith("四、") || line.startsWith("五、") || line.startsWith("六、") || line.startsWith("七、") || line.startsWith("八、") || line.startsWith("九、") || line.startsWith("十") || line.endsWith("用户服务协议")
    } else {
        line.matches(Regex("^\\d+\\..*")) || line == "Terms of Service"
    }
}

fun getPrivacyPolicyLines(isZh: Boolean = true): List<String> {
    return if (isZh) privacyPolicyLinesZh else privacyPolicyLinesEn
}

fun getTermsOfServiceLines(isZh: Boolean = true): List<String> {
    return if (isZh) termsOfServiceLinesZh else termsOfServiceLinesEn
}

internal val privacyPolicyLinesZh = """
隐私政策

最后更新日期：2026年7月6日
生效日期：2026年7月6日
应用名称：PalmNote（掌记）
开发者：个人开发者

PalmNote（以下简称"本应用"）非常重视用户的隐私保护。本隐私政策旨在向您说明本应用如何收集、使用、存储和保护您的个人信息。请在使用本应用前仔细阅读本政策。

一、信息收集

1.1 本应用不主动收集任何个人信息
本应用采用纯本地存储架构，所有数据仅存储在您的设备本地，不会上传至任何服务器或第三方服务。

1.2 本应用不收集的数据类型
- 设备标识符（IMEI、Android ID、序列号等）
- 位置信息（GPS、基站定位等）
- 通讯录、通话记录、短信内容
- 相机、麦克风数据（除非您主动使用附件功能）
- 应用使用行为、浏览记录
- 联网数据（本应用无需联网即可使用）

1.3 您主动提供的信息
仅当您主动使用以下功能时，本应用会处理相应数据：
- 拍照附件：调用系统相机，图片仅存储在应用私有目录
- 应用锁：生物识别数据仅用于本地验证，不会被存储或传输
- 日历同步：仅写入系统日历，不读取其他日历数据

1.4 第三方SDK清单
本应用不集成任何第三方SDK、广告SDK或数据分析SDK。

二、信息存储与安全

2.1 存储位置
所有数据存储在应用私有目录（/data/data/com.palmnote/），受Android系统沙箱保护，其他应用无法直接访问。

2.2 存储加密
- 数据库文件：存储在应用私有目录，受系统保护
- 备份文件：支持AES-GCM加密，密钥由用户设定
- 应用锁：PIN/图案密码经加盐哈希处理后存储

2.3 安全措施
- 应用锁支持PIN码、图案、生物识别（指纹/面部）
- 备份文件支持端到端加密
- 无网络通信，不存在数据传输风险

三、信息共享

本应用不会与任何第三方共享、出售或交换您的个人信息。本应用不包含任何第三方SDK、广告SDK或分析工具。

四、用户权利

4.1 访问权
您有权访问本应用中存储的所有个人数据。所有数据均存储在您的设备本地，您可随时查看。

4.2 更正权
您有权更正或修改本应用中存储的任何个人数据。所有数据均可在应用内直接编辑。

4.3 删除权
您有权删除本应用中的任何个人数据。您可以在应用内删除单条数据或批量数据，也可以通过卸载应用删除所有数据。

4.4 可携带权
您有权导出您的个人数据。本应用支持将数据导出为备份文件，您可以将备份文件迁移至其他设备或应用。

4.5 撤回同意权
如您不再同意本隐私政策，可以停止使用本应用并卸载，我们将不再处理您的个人信息。

五、法律依据

本应用处理您主动提供的信息（如拍照附件、应用锁设置等），是基于您的明确同意。您在使用相关功能时即表示同意本应用处理相应数据。

六、数据删除

6.1 应用内删除
您可以在应用内随时删除任何单条数据或批量数据。

6.2 卸载删除
卸载本应用将永久删除所有本地数据，此操作不可逆。

6.3 数据导出
您可以在应用内将数据导出为备份文件，导出后请妥善保管。

七、未成年人保护

本应用不面向16周岁以下未成年人提供服务，不会主动收集未成年人的个人信息。如发现无意中收集了未成年人信息，我们将及时删除。

八、隐私政策更新

我们可能会不时更新本隐私政策。更新后的政策将在应用内公布，并注明更新日期。建议您定期查看本政策以了解最新信息。

九、联系我们

如对本隐私政策有任何疑问、意见或建议，请通过以下方式联系我们：
- GitHub：https://github.com/PickGear/PalmNote/issues
- 邮箱：请通过GitHub Issues获取联系方式
""".trimIndent().split("\n").filter { it.isNotBlank() }

internal val privacyPolicyLinesEn = """
Privacy Policy

Last updated: July 6, 2026
Effective date: July 6, 2026
App name: PalmNote
Developer: Independent Developer

PalmNote (hereinafter referred to as "the App") values user privacy protection. This Privacy Policy explains how the App collects, uses, stores, and protects your personal information. Please read this policy carefully before using the App.

1. Information Collection

1.1 The App Does Not Actively Collect Personal Information
The App uses a pure local storage architecture. All data is stored only on your device and is not uploaded to any server or third-party service.

1.2 Types of Data Not Collected
- Device identifiers (IMEI, Android ID, serial numbers, etc.)
- Location information (GPS, cell tower positioning, etc.)
- Contacts, call logs, SMS content
- Camera, microphone data (unless you actively use attachment features)
- App usage behavior, browsing history
- Network data (the App works without internet connection)

1.3 Information You Provide
The App only processes data when you actively use the following features:
- Photo attachments: Uses the system camera; images are stored only in the app's private directory
- App lock: Biometric data is used only for local verification and is not stored or transmitted
- Calendar sync: Only writes to the system calendar and does not read other calendar data

1.4 Third-Party SDKs
The App does not integrate any third-party SDKs, advertising SDKs, or analytics SDKs.

2. Information Storage and Security

2.1 Storage Location
All data is stored in the app's private directory (/data/data/com.palmnote/), protected by Android's sandbox system. Other apps cannot directly access it.

2.2 Storage Encryption
- Database files: Stored in the app's private directory, protected by the system
- Backup files: Support AES-GCM encryption with user-defined keys
- App lock: PIN/pattern passwords are stored as salted hashes

2.3 Security Measures
- App lock supports PIN, pattern, and biometric (fingerprint/face) authentication
- Backup files support end-to-end encryption
- No network communication means no data transmission risks

3. Information Sharing

The App does not share, sell, or exchange your personal information with any third party. The App does not contain any third-party SDKs, advertising SDKs, or analytics tools.

4. User Rights

4.1 Access Rights
You have the right to access all personal data stored in the App. All data is stored locally on your device and can be viewed at any time.

4.2 Correction Rights
You have the right to correct or modify any personal data stored in the App. All data can be edited directly within the App.

4.3 Deletion Rights
You have the right to delete any personal data in the App. You can delete individual or batch data within the App, or delete all data by uninstalling the App.

4.4 Data Portability
You have the right to export your personal data. The App supports exporting data as backup files, which you can transfer to other devices or apps.

4.5 Withdrawal of Consent
If you no longer agree to this Privacy Policy, you may stop using the App and uninstall it. We will no longer process your personal information.

5. Legal Basis

The App processes information you actively provide (such as photo attachments and app lock settings) based on your explicit consent. By using these features, you consent to the App processing the corresponding data.

6. Data Deletion

6.1 In-App Deletion
You can delete any individual or batch data within the App at any time.

6.2 Uninstall Deletion
Uninstalling the App will permanently delete all local data. This operation is irreversible.

6.3 Data Export
You can export data as backup files within the App. Please keep backup files secure after export.

7. Minor Protection

The App does not provide services to minors under 16 years of age and does not actively collect personal information from minors. If we inadvertently collect minor information, we will delete it promptly.

8. Privacy Policy Updates

We may update this Privacy Policy from time to time. Updated policies will be published within the App with the update date noted. We recommend checking this policy periodically for the latest information.

9. Contact Us

If you have any questions, suggestions, or concerns about this Privacy Policy, please contact us through:
- GitHub: https://github.com/PickGear/PalmNote/issues
- Email: Please obtain contact information through GitHub Issues
""".trimIndent().split("\n").filter { it.isNotBlank() }

internal val termsOfServiceLinesZh = """
用户服务协议

最后更新日期：2026年7月6日
生效日期：2026年7月6日
应用名称：PalmNote（掌记）
开发者：个人开发者

欢迎使用PalmNote（以下简称"本应用"）。请您在使用本应用前仔细阅读并充分理解本协议的全部内容。一旦您开始使用本应用，即视为您已阅读并同意本协议的全部条款。

一、服务说明

1.1 服务内容
本应用是一款本地生活记录工具，提供记账、资产管理、生活记录等功能。

1.2 开源许可
本应用为免费开源软件，基于GNU通用公共许可证v3.0（GPL-3.0）发布。您可自由使用、修改和分发本软件，但需遵守GPL-3.0许可证条款。

1.3 使用方式
本应用采用纯本地存储架构，无需注册账号，无需联网即可使用。

1.4 账号说明
本应用不提供账号注册、登录服务。所有数据存储在您的设备本地，不与任何云端账号关联。您应自行负责设备安全和数据备份。

1.5 服务可用性
本应用为离线应用，不依赖网络服务。开发者不承诺应用的持续可用性，但会尽力维护应用的正常运行。如因系统升级等原因需要暂停服务，开发者将提前通知。

二、用户权利与义务

2.1 用户权利
- 自由使用本应用的各项功能
- 随时导出或删除自己的数据
- 基于GPL-3.0许可证修改和分发本软件
- 对本应用提出意见和建议

2.2 用户义务
- 妥善保管设备及应用锁密码，防止他人访问您的数据
- 定期备份重要数据，以防数据丢失
- 不利用本应用进行任何违反法律法规的活动
- 不对本应用进行逆向工程、反编译或破解
- 不利用本应用从事商业活动（除非遵守GPL-3.0许可证）

三、数据所有权

3.1 您在应用中创建的所有数据（包括但不限于账单、资产、生活记录等）完全归您所有。

3.2 开发者不拥有、不访问、不控制您的数据。所有数据仅存储在您的设备本地。

3.3 您可随时导出或删除自己的数据，开发者不会以任何方式阻止或限制您的数据操作。

四、知识产权

4.1 本应用的源代码、界面设计、图标等受著作权法保护。

4.2 基于GPL-3.0许可证，您可以自由使用、修改和分发本软件，但需保留原始版权声明和许可证。

4.3 本应用中使用的第三方库的知识产权归各自所有者所有。

五、免责声明

5.1 本应用按"现状"提供，开发者不对以下情况负责：
- 因设备故障、系统崩溃、存储空间不足导致的数据丢失
- 因用户操作不当（如误删除、未备份）导致的数据损坏
- 因不可抗力（如设备丢失、损坏、被盗）导致的数据丢失
- 因使用本应用产生的任何直接或间接损失

5.2 开发者不对因使用本应用而产生的任何利润损失、业务中断、数据丢失等后果承担责任。

5.3 您应自行承担使用本应用的风险，包括但不限于数据备份、设备安全等。

六、服务变更与终止

6.1 服务变更
开发者有权随时修改、暂停或终止本应用的服务，恕不另行通知。

6.2 用户终止
您可以随时停止使用本应用并删除应用，开发者不会以任何方式阻止或限制。

七、不可抗力

因不可抗力（包括但不限于自然灾害、政府行为、战争、罢工、网络故障、电力中断等）导致本应用无法正常运行或数据丢失的，开发者不承担任何责任。

八、协议修改

8.1 修改权利
开发者保留随时修改本协议的权利。修改后的协议将在应用内公布，并注明更新日期。

8.2 修改接受
继续使用本应用即视为您接受修改后的协议。如不同意修改，请停止使用本应用。

九、争议解决

因本协议引起的或与本协议有关的任何争议，应首先通过友好协商解决。协商不成的，任何一方均有权向开发者所在地人民法院提起诉讼。

十、法律适用

本协议的订立、效力、解释、履行、修改和终止均适用中华人民共和国法律。

十一、联系方式

如对本协议有任何疑问，请通过以下方式联系我们：
- GitHub：https://github.com/PickGear/PalmNote/issues
- 邮箱：请通过GitHub Issues获取联系方式
""".trimIndent().split("\n").filter { it.isNotBlank() }

internal val termsOfServiceLinesEn = """
Terms of Service

Last updated: July 6, 2026
Effective date: July 6, 2026
App name: PalmNote
Developer: Independent Developer

Welcome to PalmNote (hereinafter referred to as "the App"). Please read and fully understand all the contents of this agreement before using the App. Once you start using the App, it is deemed that you have read and agreed to all the terms of this agreement.

1. Service Description

1.1 Service Content
The App is a local life recording tool that provides features such as bookkeeping, asset management, and life recording.

1.2 Open Source License
The App is free open source software released under the GNU General Public License v3.0 (GPL-3.0). You may freely use, modify, and distribute this software, but must comply with GPL-3.0 license terms.

1.3 Usage
The App uses a pure local storage architecture. No account registration is required, and no internet connection is needed to use it.

1.4 Account Description
The App does not provide account registration or login services. All data is stored locally on your device and is not associated with any cloud account. You are responsible for device security and data backup.

1.5 Service Availability
The App is an offline application that does not depend on network services. The developer does not guarantee continuous availability but will do their best to maintain normal operation. If service needs to be suspended due to system upgrades or other reasons, the developer will provide advance notice.

2. User Rights and Obligations

2.1 User Rights
- Freely use all features of the App
- Export or delete your data at any time
- Modify and distribute this software based on GPL-3.0 license
- Provide feedback and suggestions for the App

2.2 User Obligations
- Properly secure your device and app lock password to prevent unauthorized access
- Regularly back up important data to prevent data loss
- Do not use the App for any illegal activities
- Do not reverse engineer, decompile, or crack the App
- Do not use the App for commercial purposes (unless complying with GPL-3.0 license)

3. Data Ownership

3.1 All data you create in the App (including but not limited to bills, assets, life records, etc.) belongs entirely to you.

3.2 The developer does not own, access, or control your data. All data is stored only on your device.

3.3 You may export or delete your data at any time. The developer will not prevent or restrict your data operations in any way.

4. Intellectual Property

4.1 The App's source code, interface design, icons, etc. are protected by copyright law.

4.2 Based on GPL-3.0 license, you may freely use, modify, and distribute this software, but must retain original copyright notices and licenses.

4.3 Intellectual property rights of third-party libraries used in the App belong to their respective owners.

5. Disclaimer

5.1 The App is provided "as is," and the developer is not responsible for:
- Data loss caused by device failure, system crashes, or insufficient storage
- Data corruption caused by improper user operations (such as accidental deletion, lack of backup)
- Data loss caused by force majeure (such as device loss, damage, theft)
- Any direct or indirect damages arising from using the App

5.2 The developer is not liable for any loss of profits, business interruption, data loss, or other consequences arising from using the App.

5.3 You assume all risks of using the App, including but not limited to data backup, device security, etc.

6. Service Changes and Termination

6.1 Service Changes
The developer reserves the right to modify, suspend, or terminate the App's services at any time without notice.

6.2 User Termination
You may stop using the App and delete it at any time. The developer will not prevent or restrict this in any way.

7. Force Majeure

The developer shall not be liable for any failure of the App to operate normally or data loss caused by force majeure (including but not limited to natural disasters, government actions, war, strikes, network failures, power outages, etc.).

8. Agreement Modifications

8.1 Modification Rights
The developer reserves the right to modify this agreement at any time. Modified agreements will be published within the App with the update date noted.

8.2 Acceptance of Modifications
Continued use of the App constitutes acceptance of the modified agreement. If you do not agree to the modifications, please stop using the App.

9. Dispute Resolution

Any disputes arising from or related to this agreement shall first be resolved through friendly negotiation. If negotiation fails, either party has the right to file a lawsuit with the people's court at the developer's location.

10. Governing Law

The formation, validity, interpretation, performance, modification, and termination of this agreement shall be governed by the laws of the People's Republic of China.

11. Contact Information

If you have any questions about this agreement, please contact us through:
- GitHub: https://github.com/PickGear/PalmNote/issues
- Email: Please obtain contact information through GitHub Issues
""".trimIndent().split("\n").filter { it.isNotBlank() }
