# MCEF 依赖解析修复记录

## 问题概述
- CI 构建失败，Gradle 无法解析 `mcef` 依赖
- 错误信息：`Could not find mcef-3.1.1-1.21.4.jar (com.github.CCBlueX:mcef:3.1.1-1.21.4)`
- 在 Jitpack 和其他 Maven 仓库中均无法找到该版本的 JAR 文件

## 原因分析
- 通过 Jitpack API 查询发现，虽然 `3.1.1-1.21.4` 在构建列表中显示为 "ok"，但实际的 JAR 文件不存在（HTTP 404）
- 这可能是由于 Jitpack 构建过程中出现问题，导致构建记录存在但产物文件缺失

## 修改内容

### 1. 更新 MCEF 版本
- `gradle.properties`: 将 `mcef_version` 从 `3.1.1-1.21.4` 更新到 `3.1.2-1.21.4`
- 验证 `3.1.2-1.21.4` 版本在 Jitpack 上可用（HTTP 200，文件大小 240168 字节）

### 2. 升级项目版本号
- `gradle.properties`: 将 `mod_version` 从 `0.1.9` 更新到 `0.1.10`

## 依赖可用性验证
```bash
# 验证旧版本不可用
curl -I https://jitpack.io/com/github/CCBlueX/mcef/3.1.1-1.21.4/mcef-3.1.1-1.21.4.jar
# 结果：HTTP/2 404

# 验证新版本可用
curl -I https://jitpack.io/com/github/CCBlueX/mcef/3.1.2-1.21.4/mcef-3.1.2-1.21.4.jar
# 结果：HTTP/2 200, content-length: 240168
```

## 其他可用的 MCEF 版本（针对 Minecraft 1.21.4）
根据 Jitpack API 查询，以下版本也可用：
- `3.0.7-1.21.4`
- `3.0.8-1.21.4`
- `3.1.0-1.21.4`
- `3.1.2-1.21.4` （当前使用）

## 构建情况
- 由于当前环境中没有配置 Java/JDK（JAVA_HOME 未设置），无法在本地执行完整构建验证
- 修改已提交到分支 `fix-mcef-dependency-resolution`，待 CI 环境验证

## 恢复次数
- 0（等待 CI 构建验证）

## 相关文件
- `/home/engine/project/gradle.properties` - 依赖版本配置
- `/home/engine/project/build.gradle.kts` - Gradle 构建脚本（包含依赖声明）
