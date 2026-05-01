# 仿真翻页动画流畅度优化 - 验证清单

- [x] 动画速度：`PageDelegate.startScroll()` 中 duration 直接使用 animationSpeed，与 Java 参考实现一致
- [x] 零分配：`getCross()` 方法使用 `out` 参数写入缓存对象，不再创建新 `PointF`
- [x] 零分配：`calcPoints()` 中使用 `tempPointF.set()` 复用缓存对象
- [x] 零分配：`mBezierEnd1`/`mBezierEnd2` 直接写入现有对象属性而非替换引用
- [x] Canvas 优化：`drawCurrentBackArea` 中 clipPath 和 drawBitmap 在同一 try 块
- [x] Canvas 优化：`drawCurrentPageShadow` 中 clipPath 和 draw 在同一 try 块
- [x] Canvas 优化：`drawNextPageAreaAndShadow` 中 clipPath 和 drawBitmap 在同一 try 块
- [x] 数学优化：`drawCurrentPageShadow` 中 Double 运算最后一次性转 Float
- [x] 数学优化：`drawNextPageAreaAndShadow` 中 atan2+toDegrees 合并计算
- [x] 数学优化：`drawCurrentBackArea` 中 hypot 使用 Float 运算
- [x] 编译通过：`compileAppDebugKotlin` 无错误
- [x] 编译通过：`compileAppReleaseKotlin` 无错误
- [ ] 右上/右下翻页：从屏幕右侧触发翻页时动画流畅无卡顿（需设备验证）
- [ ] 整体速度：翻页动画速度与 Java 参考实现感知一致（需设备验证）
