# 全部翻页动画速度调节 - 实现计划

## [ ] 任务 1: 扩展AppConfig以支持所有动画类型和6个方位的速度设置
- **优先级**: P0
- **依赖**: 无
- **描述**:
  - 在AppConfig.kt中添加所有翻页动画类型和6个方位的速度设置
  - 为每个动画类型和方位创建独立的SharedPreferences键
  - 设置默认速度值为200ms
- **验收标准**: AC-2, AC-4
- **测试要求**:
  - `programmatic` TR-1.1: 验证所有动画类型和方位的速度设置都能正确保存和读取
  - `programmatic` TR-1.2: 验证应用重启后速度设置保持不变
- **备注**: 参考现有的simulationPageAnimV2Speed实现方式

## [ ] 任务 2: 扩展PageAnimSpeedDialog以支持6个方位的速度调节
- **优先级**: P0
- **依赖**: 任务1
- **描述**:
  - 修改PageAnimSpeedDialog.kt以接收和处理方位参数
  - 更新dialog_page_anim_speed.xml布局，添加方位显示
  - 确保弹窗样式与现有项目一致
- **验收标准**: AC-3, AC-5
- **测试要求**:
  - `human-judgment` TR-2.1: 验证弹窗样式与现有UI一致
  - `human-judgment` TR-2.2: 验证速度调节范围为10-500ms
- **备注**: 保持与现有弹窗的设计风格一致

## [ ] 任务 3: 修改ReadView.kt以支持所有动画类型的长按速度调节
- **优先级**: P0
- **依赖**: 任务2
- **描述**:
  - 在ReadView.kt中修改长按事件处理逻辑
  - 为所有动画类型添加长按显示速度调节弹窗的功能
  - 传递当前方位信息给弹窗
- **验收标准**: AC-1
- **测试要求**:
  - `human-judgment` TR-3.1: 验证所有动画类型长按都能显示速度调节弹窗
  - `human-judgment` TR-3.2: 验证弹窗显示对应方位的速度设置
- **备注**: 参考现有的simulationPageAnimV2长按处理逻辑

## [ ] 任务 4: 修改各个动画Delegate以使用对应的速度设置
- **优先级**: P1
- **依赖**: 任务1
- **描述**:
  - 修改CoverPageDelegate、SlidePageDelegate、SimulationPageDelegate、ScrollPageDelegate、GradientPageDelegate等
  - 使每个Delegate使用对应的速度设置
  - 确保速度设置正确应用到动画中
- **验收标准**: AC-2
- **测试要求**:
  - `programmatic` TR-4.1: 验证每个动画类型都使用对应的速度设置
  - `human-judgment` TR-4.2: 验证动画速度符合设置值
- **备注**: 注意不同动画类型的速度参数名称可能不同

## [ ] 任务 5: 测试和验证
- **优先级**: P1
- **依赖**: 任务3, 任务4
- **描述**:
  - 测试所有动画类型的速度调节功能
  - 测试6个方位的独立速度设置
  - 验证速度设置持久化
  - 验证弹窗样式一致性
- **验收标准**: AC-1, AC-2, AC-3, AC-4, AC-5
- **测试要求**:
  - `human-judgment` TR-5.1: 全面测试所有功能点
  - `programmatic` TR-5.2: 验证设置持久化功能
- **备注**: 确保所有功能正常工作，无崩溃或异常
