# Security Policy

PalmNote 是一款纯本地应用，不联网、不发送用户数据。
PalmNote is a fully local application — it does not connect to any network or send user data.

## 支持的版本 / Supported Versions

| 版本 / Version | 支持状态 / Supported |
|---------------|-------------------|
| 1.3.x | ✅ |
| 1.2.x | ✅ |
| < 1.2 | ❌ |

## 报告漏洞 / Reporting a Vulnerability

如果发现安全相关问题，请通过 GitHub Private Vulnerability Reporting 提交：
If you find a security issue, please report it via:

https://github.com/PickGear/PalmNote/security/advisories/new

不建议在公开 Issue 中描述敏感安全问题。
Please do not disclose security vulnerabilities in public issues.

### 处理流程 / Response Process

1. 确认漏洞后会在 7 天内回复 / We will respond within 7 days
2. 修复后发布新版本 / A fix will be released in a new version
3. 可选：在 release notes 中致谢报告者 / Optional: credit the reporter in release notes

## 安全设计 / Security Design

- **数据库加密 / Database**: SQLCipher 全库加密（AES-256）/ SQLCipher full-database encryption (AES-256)
- **应用锁 / App Lock**: PIN 使用 PBKDF2-SHA256（25,000 次迭代）哈希 / PBKDF2-SHA256 (25k iterations)
- **密码本 / Password Vault**: 字段级 AES-256-GCM 加密 + 独立主密码（密钥包裹模式）/ field-level AES-256-GCM with a separate master password (key wrapping)
- **备份加密 / Backup Encryption**: AES-GCM + PBKDF2
- **数据存储 / Data Storage**: 纯本地 Room 数据库 + DataStore / Local-only Room + DataStore
- **网络权限 / Network**: 未声明 `INTERNET` 权限，应用无法联网 / No `INTERNET` permission declared — the app cannot access the network
- **OCR / OCR**: PaddleOCR PP-OCRv6 纯本地离线推理 / fully on-device inference (ONNX Runtime)
