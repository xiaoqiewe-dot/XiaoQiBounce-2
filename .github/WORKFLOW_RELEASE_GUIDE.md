# GitHub Actions 自动构建和发行版工作流指南

## 概述

本工作流自动化了以下流程:
1. 源代码构建为 JAR 文件
2. 自动发布 GitHub Release
3. 使用构建时间作为发行版名称

---

## 工作流触发条件

### 自动触发
- **事件**: 推送代码到 `nextgen` 分支
- **条件**: 必须是对 `nextgen` 分支的直接推送

### 手动触发
- **位置**: GitHub 仓库 → Actions 标签 → "构建并发布发行版" 工作流
- **操作**: 点击 "Run workflow" 按钮，选择 `nextgen` 分支

---

## 发行版命名规则

工作流使用 **两种时间戳格式**:

### 发行版名称（用户可见）
```
2025年12月02日-13时10分20秒
```
- 格式: `%Y年%m月%d日-%H时%M分%S秒`
- 优点: 中文、易读、用户友好
- 用途: GitHub Release 页面显示

### Git 标签名称（规范格式）
```
20251202-131020
```
- 格式: `%Y%m%d-%H%M%S`
- 优点: 纯数字、符合 Git 标签规范
- 用途: Git 标签、无特殊字符

---

## 工作流执行流程

### 第 1 步: 代码检出
```yaml
检出仓库和子模块
```
- 获取最新代码
- 递归检出所有子模块

### 第 2 步: 环境配置
```yaml
配置 JDK 21
授予 src-theme 权限
```
- 安装 GraalVM JDK 21（用于最佳 Minecraft 兼容性）
- 设置 src-theme 目录权限（必要用于主题构建）

### 第 3 步: 构建项目
```bash
./gradlew genSources build -x test -x detekt
```
- `genSources`: 生成必要的源文件
- `build`: 编译和打包
- `-x test`: 跳过单元测试（加快构建）
- `-x detekt`: 跳过代码质量检查（加快构建）

### 第 4 步: 准备发行版元数据
```bash
BUILD_TIME=$(date -u +"%Y年%m月%d日-%H时%M分%S秒")
BUILD_TIME_TAG=$(date -u +"%Y%m%d-%H%M%S")
```
- 生成两种时间戳格式
- 创建发行版描述（包含提交 SHA）

### 第 5 步: 定位构建产物
```bash
find build/libs -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-javadoc.jar'
```
- 查找主 JAR 文件
- 排除 sources 和 javadoc JAR

### 第 6 步: 创建发行版并上传
```yaml
使用 softprops/action-gh-release@v2
```
- 创建 GitHub Release
- 上传 JAR 文件为 Release Asset

---

## 版本号管理

### 升级版本号

编辑 `gradle.properties` 文件:

```properties
# 修改前
mod_version=0.1.13

# 修改后
mod_version=0.1.14
```

### 版本号规范
- 格式: `主版本.次版本.修订版本`
- 示例: `0.1.14`
- 推荐: 遵循语义化版本管理

---

## Release Asset（发行资产）

工作流创建的 Release 包含:

### 主要资产
- **XiaoQiBounce-0.1.14.jar** - 构建的模组 JAR 文件

### Release 信息
- **标签**: `20251202-131020`
- **标题**: `2025年12月02日-13时10分20秒`
- **描述**: 
  ```
  自动构建版本 - 提交: abc123def456 - 构建时间: 2025年12月02日-13时10分20秒 (UTC)
  ```

---

## 构建时间示例

| 时间 | 发行版名称 | Git 标签 |
|------|-----------|---------|
| 2025-12-02 13:10:20 | 2025年12月02日-13时10分20秒 | 20251202-131020 |
| 2026-01-15 08:30:45 | 2026年01月15日-08时30分45秒 | 20260115-083045 |
| 2026-06-20 23:59:59 | 2026年06月20日-23时59分59秒 | 20260620-235959 |

---

## 使用的 GitHub Actions

| Action | 版本 | 用途 |
|--------|------|------|
| `actions/checkout` | v4 | 检出代码 |
| `actions/setup-java` | v4 | 配置 Java 环境 |
| `gradle/gradle-build-action` | v3 | 运行 Gradle 构建 |
| `softprops/action-gh-release` | v2 | 创建 GitHub Release |

---

## 故障排除

### 构建失败

**症状**: 工作流显示红色 ✗

**检查清单**:
1. 确保 `nextgen` 分支中的代码可编译
2. 检查 Java 版本是否为 21
3. 查看 Gradle 构建日志

### JAR 文件未找到

**症状**: "Unable to locate jar artifact in build/libs"

**解决方案**:
1. 确保构建成功完成
2. 验证 `build.gradle.kts` 配置正确
3. 检查 `build/libs` 目录内容

### Release 未创建

**症状**: 工作流通过但 Release 未显示

**检查事项**:
1. GitHub Token 权限是否正确（需要 `contents: write`）
2. 检查工作流权限配置
3. 查看 Actions 日志中的错误信息

---

## 权限要求

工作流需要以下 GitHub 权限:

```yaml
permissions:
  contents: write  # 创建 Release 和上传资产
```

这是自动设置的，无需手动配置。

---

## 环境变量

工作流使用的环境变量:

| 变量 | 值 | 说明 |
|------|-----|------|
| `CI` | `true` | 标记为 CI 环境 |
| `GITHUB_TOKEN` | `${{ secrets.GITHUB_TOKEN }}` | GitHub API 认证 |

---

## 发行版检查清单

每次创建发行版时,请确保:

- [ ] 版本号已在 `gradle.properties` 中更新
- [ ] 代码已合并到 `nextgen` 分支
- [ ] 所有测试都通过（在本地）
- [ ] 提交信息清晰明了
- [ ] 工作流执行成功
- [ ] Release 页面显示正确的 JAR 文件

---

## 相关文件

- 工作流配置: `.github/workflows/release.yml`
- Gradle 配置: `build.gradle.kts`
- 属性文件: `gradle.properties`
- 版本历史: 查看 GitHub Releases 页面

---

## 更新日志

### v1.0 (2025-12-02)
- 创建发行版工作流
- 支持构建时间戳命名
- 使用现代化 softprops/action-gh-release@v2
- 版本升级至 0.1.14
