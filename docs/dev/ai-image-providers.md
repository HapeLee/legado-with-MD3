# AI 图片供应商

截至 2026-10-27 按官方文档核对。图片输入、文字输出、生成图片、编辑图片是不同能力；
不能仅因为一个模型能看图，就把它放进漫画翻译的图片编辑选择器。

## 接口调研与当前范围

| 供应商 | 已核实的原生接口与差异 | 本次接入 |
| --- | --- | --- |
| OpenAI | Images API：`/images/generations`、`/images/edits`；编辑支持 multipart。Responses 也有图像生成工具，是另一种调用契约。[官方文档](https://developers.openai.com/api/docs/guides/image-generation) | Images API；保留兼容服务的地址与路径覆盖。Responses 图像工具未实现 |
| Google Gemini | 当前推荐 Interactions：`/v1beta/interactions`；`input` 图片块、`response_format`、`steps[].content`。旧 `models/{model}:generateContent` 继续支持，使用 `inlineData`、`responseModalities`。[Interactions](https://ai.google.dev/gemini-api/docs/interactions-overview)、[图片指南](https://ai.google.dev/gemini-api/docs/image-generation)、[generateContent](https://ai.google.dev/api/generate-content) | 两种协议分别配置，不自动切换；Interactions 使用 `store=false` |
| 火山方舟 Seedream | `POST /api/v3/images/generations` 同时接收参考图；JSON `image` 可为 data URL；输出 `data[]`，与 OpenAI multipart 编辑不同。[官方 API](https://docs.volcengine.com/docs/ark/image-generation-api?lang=zh) | 单图同步请求、2K 输出、关闭组图；不启用图层拆分等扩展功能 |
| 阿里云百炼 Qwen Image | `/api/v1/services/aigc/multimodal-generation/generation`；`input.messages[].content` 输入图片和文字，`output.choices[].message.content` 返回图片；地域密钥独立。[官方 API](https://help.aliyun.com/zh/model-studio/qwen-image-edit-api) | 同步 Qwen Image 协议；预设北京地址，可改新加坡或业务空间域名。不声称覆盖 Wan 的其他异步协议 |
| xAI | `/v1/images/edits` 使用 JSON `image.url`；官方明确不能直接使用 OpenAI SDK 的 multipart 编辑请求。[官方文档](https://docs.x.ai/developers/model-capabilities/images/editing) | 原生 JSON 生成／编辑 |
| 硅基流动 | `/v1/images/generations` 的 JSON `image` 支持图生图，返回 `images[]`；Qwen Image Edit 不接受 `image_size`，平台有默认水印规则。[API](https://docs.siliconflow.cn/docs/api/images-generations-post)、[变更公告](https://docs.siliconflow.cn/docs/release-notes/overview) | 原生 JSON；编辑时不发送尺寸；保留平台默认水印 |
| Black Forest Labs / FLUX | 模型端点如 `/v1/flux-2-pro`，使用 `x-key`；提交后按返回的 `polling_url` 查询任务，下载临时成品。[编辑 API](https://docs.bfl.ai/flux_2/flux2_image_editing)、[集成契约](https://docs.bfl.ai/api_integration/integration_guidelines) | 已调研，未适配异步任务状态与轮询，不添加可用预设 |
| Stability AI | 例如 `/v2beta/stable-image/edit/inpaint`：multipart 图片／掩膜，按 `Accept` 返回图片字节或 JSON。[官方 API](https://docs.stability.ai/api-reference/edit/inpaint) | 已调研，掩膜契约尚未接入，不添加可用预设 |
| Anthropic Claude | 官方区分看图与生成照片／插图，不能把视觉输入能力当作原生图片输出。[官方说明](https://support.claude.com/en/articles/9002504-can-claude-produce-images) | 保留识图／文本翻译，不加入图片输出预设 |
| DeepSeek | 本轮核实了 Flash 的图片理解与文字输出格式；未确认可据以实现的原生图片输出契约。[官方视觉接口](https://api-docs.deepseek.com/guides/vision/) | 仍走文字／识图路径，不因其支持图片输入而增加出图标记 |

型号、权限与地域以账号控制台为准。预填模型 ID 是可编辑的配置建议，不保证每个账号已开通。
既有 DashScope 北京域名仍被官方支持，同时官方建议迁移到业务空间专属域名。

## 设置与职责

在 AI 设置的供应商编辑页选择「服务类型 → 图片生成与编辑」，选择供应商预设，填写密钥，
再「手动添加模型」。原生图片预设会预填可修改的型号。模型编辑中分别设置图片输入、生成图片、编辑图片能力。

本次沿用原有供应商档案：文字与图片协议可以分别建档，即使来自同一品牌，也可用不同地址、密钥或账号。
没有新增数据库列或搬迁历史配置；协议保存在已有 `protocol` 字符串，能力保存在已有 `capabilities` 字段。
旧 OpenAI 文字协议下的已识别图片型号仍可使用 Images API；未知兼容型号可在模型编辑中显式启用图片输出。

图片模型会从默认文字模型、文字任务与角色分析的选择器中排除，仓储写入默认模型时也做校验。
已有错误推断为普通 GPT／Gemini 的图片型号在配置读取时修正能力。自动推断是辅助，账号实际能力可以手动修改。
漫画翻译只接受有编辑输出能力、且已有协议适配器的图片模型。

`AiImageGateway` 负责图片生成／编辑契约，`AiImageRepository` 负责原生请求、鉴权和返回值解析。
漫画数据层只负责方形输入画布、输出尺寸验证、去除留白和恢复原图几何。没有新增独立绘图页面。
图片服务使用正常 TLS 校验、有界超时与响应体限制。HTTP 失败不会自动重试出图，也不自动降级到其他供应商。
返回临时图片 URL 时立即下载，不携带供应商密钥或自定义请求头；只接受 HTTPS 成品地址。

OpenAI Images 的模型列表仍通过现有兼容接口获取，并仅导入识别为图片的型号。
其他图片协议当前采用手动添加模型；「测试连接／获取模型」会说明没有配置元数据探测，
不伪报图片调用成功，也不会发送收费的生成／编辑请求。

旧漫画配置里的 `/images/edits` 默认路径在换到原生供应商时不会覆盖其原生端点。
显式填写其他路径仍作为高级覆盖；留空使用原生默认路径。

## 验证范围

定向测试覆盖各协议的生成／编辑请求形状、图片输出能力守卫、最终图片解析、忽略 Gemini 思考图、
密钥不随成品 URL 下载传播、收费失败只尝试一次、模型能力持久化和文字默认模型保护。
Compose 测试覆盖紧凑／展开窗口的图片模型表单内容；漫画测试验证原生输出去留白后仍保持原图尺寸。
完整弹窗宿主在 Windows Robolectric 中未能稳定等待空闲，未将其算作已通过的窗口验证；仍需真机验收。

2026-10-27 验证结果：73 项定向单元测试（含已有 AI 协议、漫画加载与翻译回归）全部通过，
`:app:assembleAppDebug`、`verifyConfigArchitecture` 和 `git diff --check` 通过。
本轮未重跑全量 Lint；上轮全量 Lint 被未改动的 `ExploreMainRoute.kt` 中 6 处既有错误阻塞，未改 Lint 基线。

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests '*AiImage*Test' --tests '*MangaImageEditorTest'
.\gradlew.bat :app:assembleAppDebug verifyConfigArchitecture
git diff --check
```

未使用真实供应商密钥发起收费调用。协议测试不等于真实模型的翻译质量、文字保真度、耗时、费用或地区可用性验收。
Release/R8 和真实设备上这些新设置的视觉、IME 与旋转表现尚未验收。
