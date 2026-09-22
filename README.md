# TradePocket

TradePocket 是一个开源 Android 记账应用，主要用于记录和统计 HSBC Trade25 的小额证券交易。

当前版本聚焦于汇丰香港 Easy Invest / Trade25 的交易记录：

- 手动录入买入和卖出交易
- 从 HSBC 交易确认邮件中自动解析交易记录
- 统计持仓、交易成本、已实现收益和回报表现
- 支持按股票、币种和时间范围查看交易数据
- 数据保存在设备本地

目前暂不支持其他券商或证券平台的小额交易自动导入。

## 项目状态

项目仍在早期开发阶段，功能和数据模型可能发生变化。使用自动同步前，请先确认邮箱配置和解析结果；TradePocket 不构成投资建议。

## 开发环境

- Android Studio
- JDK 17
- Android SDK 34
- Android Gradle Plugin / Gradle Wrapper（以项目配置为准）

## 构建与测试

在项目根目录运行：

```bash
./gradlew test
./gradlew assembleDebug
```

Windows PowerShell 可运行：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## 隐私说明

邮箱登录信息和交易数据仅用于应用本地功能。请不要将真实邮箱密码、个人交易结单或其他敏感资料提交到公开仓库。

仓库特意忽略本地的邮箱模板和电子结单目录；测试所需的脱敏样例保存在 `app/src/test/resources/emails/`。

## 许可证

项目许可证待补充。
