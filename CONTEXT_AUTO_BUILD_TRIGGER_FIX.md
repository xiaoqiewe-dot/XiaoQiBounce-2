# GitHub Actions 自动构建触发器修复记录

## 问题描述
用户报告自动构建功能不工作，GitHub Actions 工作流没有被自动触发执行。

## 问题根源
在 `.github/workflows/build.yml` 文件的第 3-5 行，`on:` 触发器配置存在格式错误：

```yaml
on:
  push:
  pull_request:
```

这种写法在 GitHub Actions 中是**无效的**。`push:` 和 `pull_request:` 后面需要明确的值或空对象，否则工作流不会被触发。

## 修复方案
将触发器配置修改为正确的 YAML 格式，明确指定要监听所有分支的推送和拉取请求：

```yaml
on:
  push:
    branches:
      - '**'
  pull_request:
    branches:
      - '**'
```

这样配置后：
- 任何分支的 `push` 事件都会触发构建
- 任何针对任何分支的 `pull_request` 事件都会触发构建

## 修改的文件
1. `.github/workflows/build.yml` - 修复 `on:` 触发器配置
2. `gradle.properties` - 版本号从 `0.1.14` 升级到 `0.1.15`

## 技术细节
### GitHub Actions 触发器的正确格式
GitHub Actions 中 `on:` 字段支持以下几种格式：

1. **数组格式**（最简洁）：
   ```yaml
   on: [push, pull_request]
   ```

2. **带空对象的格式**：
   ```yaml
   on:
     push: {}
     pull_request: {}
   ```

3. **带分支过滤的格式**（本次采用）：
   ```yaml
   on:
     push:
       branches:
         - '**'
     pull_request:
       branches:
         - '**'
   ```

4. **精确指定分支**：
   ```yaml
   on:
     push:
       branches:
         - main
         - develop
     pull_request:
       branches:
         - main
   ```

原来的错误格式（`push:` 和 `pull_request:` 后面什么都没有）会被解析为 null 或空值，导致 GitHub Actions 无法确定触发条件，因此不会执行工作流。

## 工作流说明
修复后的 `build.yml` 包含两个 job：

1. **build** - 主构建任务
   - 在所有分支的 push 和 PR 时触发
   - 编译项目但跳过测试和 detekt（`-x test -x detekt`）
   - 仅在 `nextgen` 分支成功构建后上传构建产物到 API

2. **verify-pr** - PR 验证任务
   - 在所有分支的 push 和 PR 时触发
   - 完整编译项目（包括 detekt 静态分析）
   - 上传 detekt SARIF 报告到 GitHub CodeQL

## 版本升级
- `gradle.properties`: `mod_version` 从 `0.1.14` 升级到 `0.1.15`

## 恢复次数
- 0（一次性修复，等待 CI 验证）

## 构建状态
- 本地未执行完整构建（当前环境缺少 JAVA_HOME 配置）
- 配置文件修改不影响代码逻辑，仅修复了工作流触发条件
- 交由 finish 步骤进行最终验证

## 预期效果
修复后，每次推送代码到任何分支或创建/更新 Pull Request 时，GitHub Actions 都会自动触发两个构建任务并执行完整的 CI/CD 流程。

## 验证方法
1. 将修复提交并推送到 GitHub
2. 检查 GitHub Actions 页面是否自动创建了新的工作流运行
3. 确认 `build` 和 `verify-pr` 两个 job 都被触发执行
