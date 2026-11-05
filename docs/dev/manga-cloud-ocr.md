# 漫画云端 OCR 与补充端侧模型

2026-10-28。沿用当前“识别原文／坐标 → 翻译纯文字 → 保存锚点 → 本地合成”流程。
云端 OCR 与文字翻译供应商各自独立；不迁移 AI 供应商表或数据库，不依赖 Yakuyomi／NCNN。

## 配置入口

漫画翻译 → 定位来源选择“云端 OCR” → 选择供应商、填写配置并保存。
OCR 供应商配置全局共用；每本漫画保存自己的供应商选择、原文语言和翻译模型。
阅读器和全局设置同时编辑时会同步已保存配置，保留本地尚未保存的字段；保存仅提交用户修改的字段，
并在 Repository 锁内合并最新配置，避免旧草稿覆盖另一处刚更新的凭据。同一字段均有修改时，后保存者生效。

| 供应商 | 凭据／配置 | 接口 |
| --- | --- | --- |
| 百度智能云 | API Key、Secret Key，高精度开关 | OAuth client_credentials；`accurate` 或 `general` 含位置版 |
| 腾讯云 | SecretId、Secret Key、地域，高精度开关 | TC3-HMAC-SHA256，`GeneralAccurateOCR` 或 `GeneralBasicOCR` |
| PaddleOCR 官方在线 API | AI Studio Access Token，PP-OCRv6／v5／v5-latin | 上传图片到官方 jobs API、查询状态、下载 JSONL 结果 |
| PaddleOCR 兼容服务 | 完整 `/ocr` 地址、可选令牌和 Bearer／token 认证 | 官方同步 JSON 协议，可连接自建服务；模型由服务端选择 |

这四种服务会上传图片，消耗相应供应商的配额／费用；界面明确说明。
没有凭据时在网络请求前报错；没有自动跨供应商回退或自动重复提交付费任务。
百度缓存有效 access_token，凭据变更时重新获取；不自动用旧 token 重试 OCR。
取消会停止本机 HTTP／轮询，已提交的远端任务不保证取消，不自动重新提交。
自建服务使用局域网地址时，请求前检查系统本地网络权限，由既有前台宿主发起授权；
未获授权不发送 HTTP 请求，并暂停当前自动翻译队列。授权后需显式重试，成功后继续处理剩余页面。

## 坐标与图像合同

输入按需等比缩小至最长边 4096、最多 4 Mi 像素；JPEG 编码不超过 3 MiB，避免超过含 Base64 的上传限制。
坐标按实际上送的图像尺寸归一化到 0..1000，继续使用现有整页／长图分段映射。
百度请求四边形并关闭文档方向旋转；腾讯保留文字行 Polygon；Paddle 关闭文档旋转与去畸变。
Paddle 响应必须提供 `rec_texts` 和一一对应的 `rec_polys`，只接受单张图片的一页结果。
非空文字缺少坐标、数量不一致、非法坐标和缺失结果字段作为错误；只有有效的空结果才表示无字。
保留文字行四边形供字号与方向判断，沿用物理气泡归属／散行分组，不把 OCR 框当作气泡框。

官方 Paddle 使用固定 jobs 地址，multipart 文件字段和 `model`／`optionalPayload` 与官方 SDK 对齐。
查询 pending／running 采用有上限的退避，总等待 180 秒；只提交一次。
完成后下载 `resultUrl.jsonUrl`，不向结果文件所在主机发送 API 令牌，不下载识别预览图。
带凭据的请求禁用重定向；客户端不附加其他 AI 供应商的拦截器／请求头。

## 存储与缓存

`MangaCloudOcrGateway` 提供配置读取、变更观察和 `update { copy(...) }`，由 Repository 原子写入，
写入成功后通知观察者重新读取配置。
凭据以独立 Keystore alias 的 AES-GCM 加密，配置位于不参与备份的应用私有目录。
界面用密码输入显示凭据；配置对象 `toString()` 脱敏。供应商错误只展示错误码，不回显原始响应或请求 URL。
旧书设置兼容，默认仍为原来的 AI 定位；切换不会撤下已发布译图。
供应商、公开接口／型号／精度配置参与分析身份，密钥不进入分析缓存身份或按书 JSON。
修改凭据可继续复用已有有效识别结果；需要重新 OCR 时显式重新翻译。
仅重新排版仍使用已保存的原文、译文和锚点，不调用云端 OCR 或翻译模型。

## 端侧补充

三套均来自 PaddlePaddle 官方 ONNX 仓库，固定提交、大小、SHA-256，按需下载，不内置权重。

| 套件 | 整套字节 | 开放语言 | 检测器 |
| --- | ---: | --- | --- |
| V5 mobile 韩文 | 18,341,344 | 韩、英 | V5 mobile |
| V5 mobile 东斯拉夫语 | 12,718,683 | 俄、乌克兰、白俄罗斯、保加利亚、英 | V5 mobile |
| V6 Tiny | 6,298,800 | 中、英 | V6 Tiny |

V6 Tiny 检测框均值阈值为其配置的 0.4（Small／Medium 为 0.45），其余 DB 参数 0.2／unclip 1.4。
三个识别字典分别为 11,945／517／6,904 项，输出类别数分别为字典长度＋blank＋space。
因此去掉此前“字典必须大于 1000 项”的通用版假设，仍强制字典非空并逐输出检查类别数一致。
端侧来源语言下拉按所选模型开放；保存的原文语言不受自动改写，不兼容时显示提示让用户选择。

## 验证范围

83 项定向单元／Compose 测试通过（8 个类，无失败、错误或跳过）：供应商协议与固定签名向量、
令牌缓存、异步任务提交／轮询／取消／超时、结果主机不携带令牌、坐标与空结果合同、
双供应商配置重建／清除、独立翻译与缓存身份，以及紧凑／展开窗口的配置操作。
实际运行 `:app:testAppDebugUnitTest`（上述定向集）、`:app:assembleAppDebug`、
`:app:assembleAppDebugAndroidTest`、`verifyConfigArchitecture` 与 `git diff --check`，全部通过。

PLK110／Android 17 原生验证五项通过：三套新增 OCR 权重均按生产清单的大小和 SHA-256 校验，
实际运行 APK 内的 ORT Android 1.28.0 CPU；另两项验证 Android Keystore 的双供应商加密保存／重建和腾讯 TC3 固定签名样例。
OCR 共七个输入：Tiny 中／英、韩文版韩／英、东斯拉夫语版俄／乌克兰／英，要求识别文本完全一致并保留源四边形。
测试使用独立私有夹具，结束后清理，未改动用户已下载的模型或设备电源豁免。

云端协议用模拟 HTTP 响应验证，未调用真实付费账户；不能据此承诺云端鉴权开通状态、配额、费用或真实漫画准确率。
设备模型通过夹具安装，桌面已下载和校验官方文件，未验证手机直接访问下载源的网络情况。
2026-10-30 PR 复核：新增跨入口配置同步、并发保存字段合并、显式清空凭据、
局域网权限前置检查、自动队列暂停／手动重试，以及全章失败不拦截未执行页面的回归覆盖。
94 项定向测试（8 个类）通过，`:app:compileAppDebugAndroidTestKotlin`、
`:app:assembleAppDebug`、`:app:lintAppDebug`、`verifyConfigArchitecture` 和空白检查通过，未修改检查基线。
本轮未进行真机授权弹窗或真实云端服务验证；未重跑 Release R8，未做低内存、持续运行发热或真实漫画质量对比。

## 原始依据

- [百度高精度含位置版](https://cloud.baidu.com/doc/OCR/s/tk3h7y2aq)
- [腾讯云通用识别](https://cloud.tencent.com/document/api/866/33526)、[TC3 签名](https://cloud.tencent.com/document/api/213/30654)
- [PaddleOCR 官方 SDK HTTP 合同](https://github.com/PaddlePaddle/PaddleOCR/blob/main/api_sdk/typescript/src/internal/http.ts)、[轮询合同](https://github.com/PaddlePaddle/PaddleOCR/blob/main/api_sdk/typescript/src/internal/poller.ts)
- [PaddleOCR 同步服务协议](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/pipeline_usage/OCR.md)
- [V5 韩文](https://huggingface.co/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx)、[V5 东斯拉夫语](https://huggingface.co/PaddlePaddle/eslav_PP-OCRv5_mobile_rec_onnx)
- [V6 Tiny 检测](https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx)、[V6 Tiny 识别](https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx)
