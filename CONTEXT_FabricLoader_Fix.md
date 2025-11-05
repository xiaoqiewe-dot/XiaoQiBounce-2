# FabricLoader 游戏提供程序错误修复

## 问题描述
运行 `runClient` 时出现以下错误：
```
[ERROR] [FabricLoader/GameProvider]: Minecraft game provider couldn't locate the game!
```

错误堆栈跟踪指向：
- `net.fabricmc.loader.impl.launch.knot.Knot.createGameProvider(Knot.java:212)`
- `net.fabricmc.loader.impl.launch.knot.Knot.init(Knot.java:130)`
- `net.fabricmc.loader.impl.launch.knot.KnotClient.main(KnotClient.java:23)`

## 根本原因
`src/main/resources/fabric.mod.json` 中的入口点配置错误。客户端模组（`environment: "client"`）应该使用 `"client"` 入口点，而不是 `"main"`。

Fabric 模组加载器根据配置的入口点类型来初始化模组。当入口点为 `"main"` 时，它尝试查找服务器入口点，这会导致游戏提供程序无法定位客户端游戏。

## 修复方案

### 修改文件：`src/main/resources/fabric.mod.json`

**修改前：**
```json
"entrypoints": {
  "main": [
    "net.ccbluex.liquidbounce.LiquidBounceInitializer"
  ]
},
```

**修改后：**
```json
"entrypoints": {
  "client": [
    "net.ccbluex.liquidbounce.LiquidBounceInitializer"
  ]
},
```

## 相关文件
- `src/main/resources/fabric.mod.json` - Fabric 模组配置文件
- `src/main/kotlin/net/ccbluex/liquidbounce/LiquidBounceInitializer.kt` - 实现 `ClientModInitializer` 接口

## 验证
修复后，Fabric Loader 将正确识别 `LiquidBounceInitializer` 作为客户端模组初始化器，并调用其 `onInitializeClient()` 方法来初始化 LiquidBounce 客户端。

## 恢复次数
第一次尝试：成功识别并修复问题
