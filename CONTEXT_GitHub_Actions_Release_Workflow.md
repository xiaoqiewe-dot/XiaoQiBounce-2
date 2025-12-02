# GitHub Actions 发行版工作流优化上下文

## 执行信息
- **修改时间**: 2025年12月02日 13时10分左右
- **分支**: feat-gh-actions-build-jar-release-timestamp-e01
- **恢复次数**: 0 次（无错误，一次性完成）
- **版本升级**: 0.1.13 → 0.1.14

## 工单需求
创建一个 GitHub Actions 工作流，实现:
1. ✅ 自动把源代码构建成 JAR 文件
2. ✅ 自动发布发行版
3. ✅ 发行版使用构建时间为名称

## 修改内容详解

### 1. 重构 `.github/workflows/release.yml` 工作流文件

**原有问题**:
- 使用过时的 `actions/create-release@v1`
- 上传资产需要分离的步骤
- 工作流标签全英文

**实施的改进**:
- ✅ 替换为现代化的 `softprops/action-gh-release@v2`
  - 更稳定、更快速
  - 原生支持文件上传（无需额外步骤）
  - 更好的错误处理和日志
- ✅ 所有步骤名称改为中文
- ✅ 发行版命名优化（双时间戳格式）

**时间戳格式说明**:
```
发行版名称: 2025年12月02日-13时10分20秒  (中文，用户友好)
Git 标签:  20251202-131020              (数字，Git 规范)
```

### 2. 升级版本号 `gradle.properties`
```diff
- mod_version=0.1.13
+ mod_version=0.1.14
```

### 3. 创建文档和上下文文件
- ✅ `CONTEXT_GitHub_Actions_Release_Workflow.md` - 本文件
- ✅ `.github/WORKFLOW_RELEASE_GUIDE.md` - 详细使用指南

## 技术实现细节

### 工作流触发机制
```yaml
on:
  push:
    branches: [ nextgen ]
  workflow_dispatch:          # 支持手动触发
```
- 自动: 推送到 nextgen 分支时触发
- 手动: 在 Actions 页面点击 "Run workflow" 触发

### 构建命令
```bash
./gradlew genSources build -x test -x detekt
```
- `genSources`: 生成 Minecraft 映射和源文件
- `build`: 编译 Kotlin/Java 并打包 JAR
- `-x test`: 跳过单元测试（加快 CI）
- `-x detekt`: 跳过代码检查（加快 CI）

### JAR 文件查找逻辑
```bash
find build/libs -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-javadoc.jar'
```
- 查找主模组 JAR
- 排除 Sources Jar（源代码）
- 排除 Javadoc Jar（文档）

### Release Asset 上传
```yaml
files: ${{ steps.artifact.outputs.jar_path }}
```
- 直接在 `softprops/action-gh-release@v2` 中指定
- 无需额外的上传 Action
- 自动附加到 Release

## 验证过程

### 本地验证清单
✅ YAML 语法检查 - 格式正确
✅ 工作流步骤数 - 6 个步骤
✅ Action 版本 - 4 个正确的 Action
✅ 环境变量 - CI 和 GITHUB_TOKEN 配置
✅ 输出变量 - 6 个变量正确定义
✅ Git 分支 - 在正确的特性分支上
✅ gitignore - 文件存在且配置正确

### 时间戳格式验证
```
中文格式输出: 2025年12月02日-13时10分20秒 ✓
数字格式输出: 20251202-131020 ✓
```

## 文件变更汇总

| 文件 | 类型 | 状态 |
|------|------|------|
| `.github/workflows/release.yml` | 修改 | 70 行 |
| `gradle.properties` | 修改 | 版本升级 |
| `CONTEXT_GitHub_Actions_Release_Workflow.md` | 新建 | 完整上下文 |
| `.github/WORKFLOW_RELEASE_GUIDE.md` | 新建 | 使用指南 |

## 依赖和兼容性

### 系统要求
- Java 21 (GitHub Actions 自动安装 GraalVM)
- Node.js 20+ (用于 Svelte 主题构建)
- Gradle 8.14+ (项目配置)

### GitHub 权限要求
```yaml
permissions:
  contents: write  # 必需：创建 Release 和上传资产
```

### 使用的 GitHub Actions
| Action | 版本 | 最小要求 |
|--------|------|---------|
| actions/checkout | v4 | - |
| actions/setup-java | v4 | - |
| gradle/gradle-build-action | v3 | - |
| softprops/action-gh-release | v2 | - |

## 回滚指南

如果需要恢复到原始状态：
```bash
git checkout HEAD -- .github/workflows/release.yml gradle.properties
git rm CONTEXT_GitHub_Actions_Release_Workflow.md .github/WORKFLOW_RELEASE_GUIDE.md
```

或者直接重置：
```bash
git reset --hard HEAD~1  # 如果已提交
```

## 后续改进建议

1. **构建缓存优化**
   - 使用 Gradle Build Cache
   - 缓存 Maven 依赖

2. **多平台支持**
   - 添加 Windows 和 macOS 构建矩阵
   - 为不同平台生成 Release

3. **自动化增强**
   - 自动更新 CHANGELOG
   - 自动生成 Release Notes
   - 发送 Discord 通知

4. **性能优化**
   - 并行运行 Detekt 检查
   - 使用 Gradle 守护进程
   - 缓存 Node 依赖

## 测试清单

完成后应该验证：

- [ ] 在 nextgen 分支推送代码后工作流自动触发
- [ ] 构建成功完成（不含测试和检查）
- [ ] JAR 文件成功生成
- [ ] Release 使用正确的中文时间戳名称
- [ ] Git 标签使用数字格式
- [ ] JAR 文件成功上传为 Asset
- [ ] Release 描述包含提交 SHA 和构建时间

## 相关文件索引

- 工作流配置: `.github/workflows/release.yml`
- 使用指南: `.github/WORKFLOW_RELEASE_GUIDE.md`  
- Gradle 配置: `build.gradle.kts`
- 项目属性: `gradle.properties`
- 访问控制: `src/main/resources/liquidbounce.accesswidener`
- 模组元数据: `src/main/resources/fabric.mod.json`
