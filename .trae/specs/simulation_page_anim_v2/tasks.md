# 仿真二翻页动画实现 - 任务分解与优先级

## [x] 任务1: 添加仿真二翻页动画类型常量
- **优先级**: P0
- **依赖**: 无
- **描述**: 
  - 在PageAnim.kt中添加新的动画类型常量simulationPageAnimV2
  - 更新@IntDef注解以包含新的动画类型
- **验收标准**: AC-1
- **测试需求**:
  - `human-judgment` TR-1.1: 确认PageAnim.kt中已添加simulationPageAnimV2常量
  - `human-judgment` TR-1.2: 确认@IntDef注解已更新

## [x] 任务2: 实现仿真二翻页动画委托类
- **优先级**: P0
- **依赖**: 任务1
- **描述**:
  - 创建SimulationPageDelegateV2类，继承自HorizontalPageDelegate
  - 实现基于贝塞尔曲线的翻页效果，参考SimulationPageAnim.java
  - 添加长按检测和速度调整弹窗功能
- **验收标准**: AC-2, AC-3
- **测试需求**:
  - `human-judgment` TR-2.1: 确认SimulationPageDelegateV2类已创建
  - `human-judgment` TR-2.2: 确认翻页动画效果符合预期
  - `human-judgment` TR-2.3: 确认长按触发速度调整弹窗功能

## [x] 任务3: 在ReadView中添加仿真二动画支持
- **优先级**: P0
- **依赖**: 任务1, 任务2
- **描述**:
  - 在ReadView.kt的upPageAnim方法中添加对simulationPageAnimV2的处理
  - 创建SimulationPageDelegateV2实例
- **验收标准**: AC-1, AC-2
- **测试需求**:
  - `human-judgment` TR-3.1: 确认ReadView.kt中已添加对simulationPageAnimV2的处理
  - `human-judgment` TR-3.2: 确认可以选择并使用仿真二动画

## [x] 任务4: 添加动画速度配置项
- **优先级**: P0
- **依赖**: 任务2
- **描述**:
  - 在AppConfig.kt中添加simulationPageAnimV2Speed配置项
  - 使用prefDelegate实现持久化存储
- **验收标准**: AC-4
- **测试需求**:
  - `human-judgment` TR-4.1: 确认AppConfig.kt中已添加simulationPageAnimV2Speed配置项
  - `human-judgment` TR-4.2: 确认速度设置能够持久化保存

## [x] 任务5: 实现速度调整弹窗
- **优先级**: P0
- **依赖**: 任务2, 任务4
- **描述**:
  - 实现showSpeedAdjustDialog方法
  - 使用DetailSeekBar创建速度调整滑块
  - 确保弹窗样式符合现有项目UI
- **验收标准**: AC-3, AC-4, AC-5
- **测试需求**:
  - `human-judgment` TR-5.1: 确认速度调整弹窗能够正常显示
  - `human-judgment` TR-5.2: 确认滑块能够调整动画速度
  - `human-judgment` TR-5.3: 确认弹窗样式与现有UI一致

## [ ] 任务6: 测试与优化
- **优先级**: P1
- **依赖**: 任务1-5
- **描述**:
  - 测试仿真二翻页动画的性能和效果
  - 优化动画性能，确保流畅运行
  - 测试速度调整功能的可靠性
- **验收标准**: AC-2, AC-4
- **测试需求**:
  - `human-judgment` TR-6.1: 确认动画在不同设备上运行流畅
  - `human-judgment` TR-6.2: 确认速度调整功能在重启应用后仍然有效
  - `human-judgment` TR-6.3: 确认没有明显的性能问题