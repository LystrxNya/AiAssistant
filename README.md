# AI Assistant

一个功能丰富的 Android AI 助手应用，基于 Jetpack Compose 和 Kotlin 构建。

## 主要功能

- **智能对话** - 与 AI 进行自然语言对话
- **图文识别** - OCR 文字识别和文档扫描
- **录音转文字** - 语音转录和音频处理
- **智能待办** - 任务管理和提醒功能
- **密码本** - 生物识别保护的安全存储
- **日志面板** - 应用运行日志查看

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **架构**: MVVM
- **本地数据库**: Room
- **依赖注入**: Hilt
- **网络请求**: Retrofit
- **异步处理**: Kotlin Coroutines & Flow

## 系统要求

- Android 8.0 (API 26) 及以上版本
- 支持的功能（可选）：
  - 相机（用于 OCR 扫描）
  - 生物识别（用于密码本）
  - 音频录制（用于语音转录）

## 构建说明

1. 克隆项目：
```bash
git clone https://github.com/LystrxNya/AiAssistant.git
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 依赖

4. 构建并运行

## 权限说明

- `INTERNET` - 网络通信
- `CAMERA` - 拍照和文档扫描
- `READ_MEDIA_AUDIO` - 读取音频文件
- `READ_MEDIA_IMAGES` - 读取图片
- `POST_NOTIFICATIONS` - 发送通知
- `USE_BIOMETRIC` - 生物识别认证

## License

MIT License
