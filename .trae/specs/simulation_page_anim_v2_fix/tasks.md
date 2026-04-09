# 仿真二翻页动画完善 - 实现计划

## [x] 任务1: 添加仿真二翻页动画的字符串资源
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - 在 strings.xml 中添加"仿真二"翻页动画的字符串资源
  - 确保中英文资源都有对应的值
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `human-judgement` TR-1.1: 确认字符串资源已正确添加
- **Notes**: 使用 page_anim_simulation_v2 作为资源名称

## [x] 任务2: 在布局文件中添加仿真二翻页动画的Chip
- **Priority**: P0
- **Depends On**: 任务1
- **Description**:
  - 在 dialog_read_book_style.xml 中添加仿真二翻页动画的 Chip
  - Chip 的 id 为 rb_simulation_anim_v2
  - 确保 Chip 与其他翻页动画选项样式一致
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `human-judgement` TR-2.1: 确认 Chip 在布局中正确显示
- **Notes**: Chip 应该放在 rb_simulation_anim 之后

## [x] 任务3: 在ReadStyleDialog.kt中添加仿真二翻页动画的处理逻辑
- **Priority**: P0
- **Depends On**: 任务2
- **Description**:
  - 在 ReadStyleDialog.kt 的 setOnCheckedStateChangeListener 中添加对 rb_simulation_anim_v2 的处理
  - 对应的值为 6 (PageAnim.simulationPageAnimV2)
  - 在 upView() 方法中添加对 simulationPageAnimV2 的支持
- **Acceptance Criteria Addressed**: AC-1, AC-2
- **Test Requirements**:
  - `human-judgement` TR-3.1: 确认可以选择仿真二翻页动画
  - `programmatic` TR-3.2: 确认选择后正确切换到仿真二翻页动画
- **Notes**: 确保在 when 语句中添加对应的 case

## [x] 任务4: 创建翻页动画速度调整弹窗
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - 创建速度调整弹窗的布局文件（参考项目中现有的弹窗布局）
  - 创建 DialogFragment 或 BottomSheetDialogFragment 来实现速度调整弹窗
  - 弹窗包含一个滑块来调节速度（范围 100-500ms）
  - 显示当前速度值
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-5
- **Test Requirements**:
  - `human-judgement` TR-4.1: 确认弹窗样式与项目一致
  - `human-judgement` TR-4.2: 确认滑块可以正常调节
- **Notes**: 参考 AutoReadDialog.kt 或其他现有弹窗的实现

## [x] 任务5: 实现长按翻页区域触发速度调整弹窗
- **Priority**: P0
- **Depends On**: 任务4
- **Description**:
  - 在 ReadView.kt 中实现长按检测逻辑
  - 当检测到长按时，显示速度调整弹窗
  - 确保长按不会影响正常的翻页操作
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `human-judgement` TR-5.1: 确认长按可以触发弹窗
  - `human-judgement` TR-5.2: 确认长按不影响正常翻页
- **Notes**: 查看 ReadView.kt 中已有的长按逻辑

## [x] 任务6: 保存和应用动画速度设置
- **Priority**: P0
- **Depends On**: 任务4, 任务5
- **Description**:
  - 确保 AppConfig.kt 中的 simulationPageAnimV2Speed 配置正常工作
  - 在速度调整弹窗中保存用户设置的速度
  - 在翻页动画中应用用户设置的速度
- **Acceptance Criteria Addressed**: AC-4, AC-6
- **Test Requirements**:
  - `programmatic` TR-6.1: 确认速度设置被正确保存
  - `programmatic` TR-6.2: 确认速度设置在重启应用后保持
  - `human-judgement` TR-6.3: 确认动画速度按设置变化
- **Notes**: 查看 SimulationPageDelegateV2.kt 中如何使用速度设置

## [ ] 任务7: 测试和验证
- **Priority**: P0
- **Depends On**: 任务3, 任务6
- **Description**:
  - 进行全面的功能测试
  - 验证仿真二翻页动画可以正常选择和使用
  - 验证速度调整功能正常工作
  - 验证所有验收标准都满足
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5, AC-6
- **Test Requirements**:
  - `human-judgement` TR-7.1: 全面功能测试
  - `programmatic` TR-7.2: 验证设置持久化
  - `human-judgement` TR-7.3: 用户体验评估
- **Notes**: 测试不同设备和不同翻页速度的情况
