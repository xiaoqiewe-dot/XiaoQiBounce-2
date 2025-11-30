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

## 后续编译问题修复

### 问题 1：导入错误
CI 构建时出现以下编译错误：
- `ModuleFly.kt:80` - Unresolved reference 'FlyTpAscend'
- `ModuleFly.kt:82` - Unresolved reference 'FlyHeypixelStyle'
- `ModuleFly.kt:83` - Unresolved reference 'FlyDashFlight'

**原因**：根据之前的修复（见 `CONTEXT_CI_Gradle_Detekt_Fix.md`），这些类的包路径被修改到了 `modes.grim` 子包，但导入语句未更新。

**修复**：在 `ModuleFly.kt` 中添加正确的导入语句：
```kotlin
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.grim.FlyDashFlight
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.grim.FlyHeypixelStyle
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.grim.FlyTpAscend
```

### 问题 2：重复注解
CI 构建时出现错误：
- `EntityExtensions.kt:482` - This annotation is not repeatable

**原因**：使用了多个独立的 `@Suppress` 注解，但 Kotlin 中应该将多个抑制规则放在同一个注解中。

**修复**：将两个 `@Suppress` 注解合并为一个：
```kotlin
@Suppress("NestedBlockDepth", "detekt.UnusedParameter")
```

### 版本号更新
- `gradle.properties`: 将 `mod_version` 从 `0.1.10` 更新到 `0.1.11`

## 后续 Detekt 静态分析问题修复

### 问题概述
第二次 CI 构建失败，detekt 检测到 18 个加权问题：
- 17 个 `MaxLineLength` 错误 - 行长度超过限制
- 1 个 `ReturnCount` 错误 - 函数返回语句过多

### 修复详情

#### 1. MaxLineLength 修复（17 处）
**原则**：将超长行拆分为多行，提取中间变量

受影响文件：
- `FlyHeypixelStyle.kt:60` - 将长的 `setVelocity` 调用拆分为多行
- `ModuleFollowBot.kt:107` - 提取调试名称为变量
- `ModuleParticles.kt:66,74,85` - 拆分 lambda 和字符串拼接
- `ModuleItemImageReplace.kt:111` - 分步构建 Identifier 路径
- `ModuleBlinkGrim.kt:45,59` - 提取 lambda 为变量，添加 `TransferOrigin` 导入
- `NoFallGrimSafe.kt:85` - 拆分速度计算为多行
- `INTBotMode.kt:85` - 提取状态效果检查为变量
- `ModuleNotebot.kt:62` - 提取 sound 为变量
- `ModuleSwordBlock.kt:107` - 提取数据包构造为变量
- `VelocityDelayedKnockback.kt:91` - 拆分注释为多行
- `VelocityGrimCombat.kt:229,282,295,336,367` - 提取调试消息为变量

#### 2. ReturnCount 修复（1 处）
**文件**：`ModuleFollowBot.kt:184` - `attemptAutoBridgeTowards` 函数

**问题**：函数有 5 个 return 语句（第 186, 188, 191, 198, 202 行），超过限制 4 个

**修复**：合并条件检查减少 return 次数
```kotlin
// 合并延迟和空中检查
val notReady = now - lastBridgeMs < bridgeDelayMs || !isOverVoid(goal)
if (notReady) return

// 合并地面和支撑块检查
val canPlace = canStandOn(placePos).not() &&
    world.getBlockState(supportPos).isAir.not()
if (!canPlace) return
```

现在函数只有 4 个 return 语句：
1. `if (!autoBridge) return`
2. `if (notReady) return`
3. `if (!canPlace) return`
4. `val slot = ... ?: return`

### 版本号更新
- `gradle.properties`: 将 `mod_version` 从 `0.1.11` 更新到 `0.1.12`

## 构建情况
- ✅ 第一次 CI 构建成功通过（MCEF 依赖和编译错误修复）
- ⏳ 第二次修复 detekt 问题，待 CI 验证
- 修改已提交到分支 `fix-mcef-dependency-resolution`

## 恢复次数
- 2（第一次修复导入和注解问题，第二次修复 detekt 代码风格问题）

## 相关文件
- `/home/engine/project/gradle.properties` - 依赖版本配置
- `/home/engine/project/build.gradle.kts` - Gradle 构建脚本（包含依赖声明）
