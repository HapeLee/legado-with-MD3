# 仿真翻页动画流畅度优化 Spec

## Why
仿真翻页动画整体速度偏慢，且在页面右上和右下区域出现明显卡顿。当前 Kotlin 实现与 Java 参考实现 `SimulationPageAnim.java` 存在关键差异，导致动画性能和视觉体验下降。

## What Changes
- 修复动画速度计算逻辑，与 Java 参考实现对齐
- 减少热路径中的对象分配，降低 GC 压力
- 优化 Canvas 绘制操作，减少不必要的 save/restore 和 clipPath 调用
- 优化 Float↔Double 类型转换，减少数学运算开销
- 针对右上/右下区域卡顿进行专项优化

## Impact
- Affected code: `SimulationPageDelegate.kt`（主要修改文件）
- Affected code: `PageDelegate.kt`（动画速度计算逻辑）
- Affected specs: 仿真翻页动画的视觉体验和性能

## ADDED Requirements

### Requirement: 动画速度对齐 Java 参考实现
系统 SHALL 在 `onAnimStart` 中直接将 `animationSpeed` 作为 Scroller 的 duration 传入，而非通过距离比例重新计算。

#### Scenario: 翻页动画速度
- **WHEN** 用户触发翻页动画
- **THEN** 动画持续时间等于 `animationSpeed`，与 Java 参考实现 `SimulationPageAnim.startAnim()` 行为一致

### Requirement: 减少热路径对象分配
系统 SHALL 在每帧绘制过程中避免创建新的 `PointF` 对象，复用预分配的缓存对象。

#### Scenario: getCross 方法零分配
- **WHEN** `calcPoints()` 调用 `getCross()` 计算交点
- **THEN** 使用预分配的 `crossPointF` 缓存对象存储结果，不创建新的 `PointF`

### Requirement: 优化 Canvas 绘制操作
系统 SHALL 减少不必要的 Canvas 状态保存/恢复操作，优化 clipPath 调用顺序。

#### Scenario: drawCurrentBackArea 绘制优化
- **WHEN** 绘制翻页背面区域
- **THEN** Canvas save/restore 与 clipPath 操作在同一 try 块内完成，避免 clipPath 失败后仍执行 drawBitmap

### Requirement: 右上/右下区域卡顿修复
系统 SHALL 确保当 `mCornerX = viewWidth`（右侧翻页）时，贝塞尔曲线计算和绘制操作不会产生额外的性能开销。

#### Scenario: 右侧翻页流畅度
- **WHEN** 用户从屏幕右侧触发翻页
- **THEN** 动画帧率与左侧翻页一致，无明显卡顿

## MODIFIED Requirements

### Requirement: startScroll 动画持续时间计算
原实现：`duration = (animationSpeed * abs(dx)) / viewWidth`
修改为：`duration = animationSpeed`（直接使用，与 Java 参考实现一致）

### Requirement: getCross 交点计算
原实现：每次调用创建新 `PointF` 对象
修改为：复用预分配的 `crossPointF` 缓存对象，避免 GC 压力

### Requirement: calcPoints 中的 PointF 创建
原实现：`getCross(PointF(mTouchX, mTouchY), ...)` 每次创建临时对象
修改为：使用 `tempPointF.set(mTouchX, mTouchY)` 复用缓存对象
