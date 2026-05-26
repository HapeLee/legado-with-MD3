# 在线朗读规则

在线朗读规则用于接入第三方 TTS（文本转语音）服务。规则格式为 URL 规则，语法同源 URL 规则。

[[toc]]

## JS 参数

在规则中可以使用以下 JS 变量：

| 参数           | 说明           |
|--------------|--------------|
| `speakText`  | 待朗读的文本内容     |
| `speakSpeed` | 朗读速度，范围 5-50 |

## 示例

在线 TTS 接口示例：

```
http://tts.example.com/text2audio,{
    "method": "POST",
    "body": "text={{java.encodeURI(speakText)}}&speed={{speakSpeed}}&lang=zh"
}
```

::: tip 说明

- 请求返回的音频会自动播放
- `speakSpeed` 值越大语速越快，需根据具体 TTS 服务的参数范围进行映射
  :::
