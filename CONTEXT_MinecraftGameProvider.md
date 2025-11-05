# Minecraft Game Provider 入口点修复记录

## 问题概述
- 在执行客户端启动任务时，Fabric Loader 抛出 `Minecraft game provider couldn't locate the game` 异常，导致开发环境无法进入游戏。

## 原因分析
- 入口点使用 Kotlin `object` 实现，但 `fabric.mod.json` 中仅以字符串形式注册，可能导致 Fabric Loader 在某些环境下无法通过 Kotlin 适配器正确解析该入口点，从而提前中止初始化流程。

## 修改内容
- `src/main/resources/fabric.mod.json`
  - 将 `client` 入口点由字符串改为对象形式，并显式指定 `adapter: "kotlin"`，确保 Fabric Loader 使用 Kotlin 适配器加载 `LiquidBounceInitializer`。

## 构建情况
- 尝试执行 `./gradlew runClient`，但因为当前环境缺少 JDK（`JAVA_HOME` 未设置）而无法完成构建，后续需在具备 JDK 的环境中复验。

## 恢复次数
- 0（构建未执行，等待具备 JDK 的环境进行验证）
