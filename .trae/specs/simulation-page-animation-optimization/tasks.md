# Tasks

- [x] Task 1: 修复动画速度计算逻辑
  - [x] SubTask 1.1: 修改 `PageDelegate.startScroll()` 方法，将 `duration` 直接设为 `animationSpeed`，与 Java 参考实现 `SimulationPageAnim.startAnim()` 中 `mScroller.startScroll(..., animationSpeed)` 行为对齐
  - [x] SubTask 1.2: 验证 `onAnimStart` 中的 `dx`/`dy` 计算与 Java 参考实现完全一致

- [x] Task 2: 消除热路径对象分配
  - [x] SubTask 2.1: 将 `getCross()` 方法改为复用预分配的 `crossPointF` 缓存对象，不再每次创建新 `PointF`
  - [x] SubTask 2.2: 将 `calcPoints()` 中的 `PointF(mTouchX, mTouchY)` 改为 `tempPointF.set(mTouchX, mTouchY)` 复用缓存对象
  - [x] SubTask 2.3: 将 `mBezierEnd1` 和 `mBezierEnd2` 的赋值改为直接写入现有对象而非替换引用

- [x] Task 3: 优化 Canvas 绘制操作
  - [x] SubTask 3.1: 在 `drawCurrentBackArea` 中，将 clipPath 和 drawBitmap 放入同一 try 块，clipPath 异常时跳过 drawBitmap
  - [x] SubTask 3.2: 在 `drawCurrentPageShadow` 中，将两个阴影绘制块的 clipPath 和 draw 操作合并到同一 try 块
  - [x] SubTask 3.3: 在 `drawNextPageAreaAndShadow` 中，将 clipPath 和 drawBitmap 放入同一 try 块

- [x] Task 4: 优化数学运算减少类型转换
  - [x] SubTask 4.1: 在 `drawCurrentPageShadow` 中，将 `25f * 1.414f * cos(degree)` 等计算改为直接使用 Double 运算，最后一次性转 Float
  - [x] SubTask 4.2: 在 `drawNextPageAreaAndShadow` 中，将 `atan2` + `toDegrees` 合并为一次计算
  - [x] SubTask 4.3: 在 `drawCurrentBackArea` 中，将 `hypot` 计算优化为直接使用 Float 运算

- [x] Task 5: 编译验证
  - [x] SubTask 5.1: 执行 `gradlew compileAppDebugKotlin` 确保无编译错误
  - [x] SubTask 5.2: 执行 `gradlew compileAppReleaseKotlin` 确保 Release 构建通过

# Task Dependencies
- [Task 2] depends on [Task 1]（先修复速度逻辑，再优化性能）
- [Task 3] depends on [Task 2]（先减少对象分配，再优化 Canvas 操作）
- [Task 4] depends on [Task 2]（可与 Task 3 并行）
- [Task 5] depends on [Task 1, Task 2, Task 3, Task 4]
