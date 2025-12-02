# GitHub Actions 自动构建和发行版工作流 - 上下文文件

## 任务描述
创建一个GitHub工作流，用于自动构建JAR文件并发布发行版，发行版使用构建时间作为名称。

## 解决方案
创建了 `.github/workflows/auto-build-release.yml` 工作流文件，具有以下特性：

### 工作流配置
- **触发条件**: 
  - 推送到 `ci-gh-actions-build-jar-release-timestamp` 分支
  - 手动触发 (`workflow_dispatch`)
- **运行环境**: Ubuntu latest
- **权限**: 需要 `contents: write` 权限来创建发行版

### 构建流程
1. **检出代码**: 使用 `actions/checkout@v4` 检出仓库和递归子模块
2. **设置Java环境**: 使用 GraalVM JDK 21
3. **权限设置**: 为 `src-theme` 目录授予执行权限
4. **Gradle缓存**: 缓存Gradle依赖以加速构建
5. **项目构建**: 执行 `genSources build -x test -x detekt` 任务

### 发行版管理
- **时间戳格式**: `YYYYMMDD-HHMMSS` (UTC时间)
- **发行版标签**: 使用时间戳作为标签名
- **发行版名称**: `构建版本 YYYYMMDD-HHMMSS`
- **JAR文件重命名**: `XiaoQiBounce-YYYYMMDD-HHMMSS.jar`
- **发行版描述**: 包含提交SHA和构建时间信息

### 输出产物
1. **GitHub发行版**: 包含重命名后的JAR文件
2. **GitHub Actions Artifacts**: 上传构建产物，保留30天

## 技术要点
- 使用最新的GitHub Actions版本 (`softprops/action-gh-release@v2`)
- 自动处理JAR文件重命名，避免版本号冲突
- 包含详细的构建日志和错误处理
- 使用Gradle缓存优化构建性能

## 文件变更
- **新增**: `.github/workflows/auto-build-release.yml` - 自动构建和发行版工作流

## 版本信息
- 基于项目版本: `mod_version=0.1.14` (已从0.1.13升级)
- 项目名称: `archives_base_name=XiaoQiBounce`
- Minecraft版本: `1.21.4`
- Fabric Loader版本: `0.16.14`

## 恢复记录
- 初始创建工作流文件，无恢复操作
- 修正了JAR文件路径和重命名逻辑
- 测试了构建产物定位逻辑，验证文件查找和重命名功能正常
- 确认YAML语法正确，GitHub Actions版本兼容