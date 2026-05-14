# PDF AI Reader

基于 Android 的智能 PDF 阅读笔记系统。以 PDF 为基底的笔记管理，内置多模型 AI 对话，支持 Markdown / 数学公式渲染。

## Key Features

- **笔记式架构** — PDF 作为笔记背景嵌入，非独立只读文件（Samsung Notes 模式）
- **左侧文件树** — 折叠式笔记列表，新建/切换/删除笔记
- **AI 永久侧边栏** — 分屏布局，支持 DeepSeek / OpenAI / Ollama 三模型切换
- **联网搜索** — DuckDuckGo 实时搜索，AI 综合文档+搜索+知识回答
- **圈画搜索** — 在 PDF 上拖动选择区域，以标签形式添加上下文，自由组合后发送
- **上下文标签** — Web-Chat 风格：圈选 → Chip 标签 → 增删 → 输入问题 → 组装发送
- **高亮与批注** — 高亮/下划线/批注持久化到 Room 数据库，不修改原 PDF
- **多模态图片** — 支持粘贴/上传图片，AI 理解图片内容
- **Markdown 渲染** — AI 回复支持标题、加粗、列表、代码块、表格 (Markwon + Glide)
- **数学公式** — 行内 `$E=mc^2$` 和块级 `$$\int f(x)dx$$` 渲染为图片
- **笔记保存** — AI 回复一键保存，按笔记关联查询

## Screenshots

<details>
<summary>点击展开</summary>

| 主页 | 阅读界面 |
|------|---------|
| 卡片式入口，新建/导入/最近笔记 | 左侧文件树 + PDF + AI 侧边栏分屏 |

</details>

## Download

最新版本: **[v2.0](https://github.com/terefal/PDF-ai-reader/releases/tag/v2.0)**

- Android 7.0+ (API 24)
- 直接下载 APK 安装

## Tech Stack

| 层 | 技术 |
|----|------|
| 语言 | Kotlin |
| UI | XML Layouts (AppCompat) |
| 架构 | ViewModel + LiveData + Coroutines |
| 数据库 | Room (SQLite) v3 |
| PDF 渲染 | AndroidPdfViewer (mhiew fork) |
| PDF 文本提取 | PDFBox Android |
| Markdown | Markwon + Glide Images |
| AI 后端 | DeepSeek / OpenAI / Ollama |
| 网络搜索 | DuckDuckGo API |

## Project Structure

```
app/src/main/java/com/terefal/pdfaireader/
├── ai/                    # AI 集成
│   ├── AiProvider.kt      # 抽象接口 (suspend, 多模态)
│   ├── OpenAiProvider.kt  # OpenAI 兼容基类
│   ├── DeepSeekProvider.kt
│   ├── OllamaProvider.kt
│   ├── SearchAgent.kt     # 联网搜索协调
│   └── WebSearchService.kt
├── chat/                  # 聊天 UI
│   ├── ContextTag.kt      # 上下文标签模型
│   ├── ContextTagManager.kt
│   └── MarkdownRenderer.kt
├── config/
│   └── SettingsManager.kt # SharedPreferences
├── data/                  # Room 数据层
│   ├── NoteBook.kt        # 笔记实体 (顶层)
│   ├── Note.kt            # AI 对话笔记
│   ├── Annotation.kt      # PDF 标注
│   └── *Dao.kt / AppDatabase.kt
├── pdf/                   # PDF 处理
│   ├── PdfTextExtractor.kt
│   ├── PdfCoordinateMapper.kt
│   ├── SelectionOverlay.kt
│   └── AnnotationOverlay.kt
├── view/
│   └── FileTreeAdapter.kt
├── viewmodel/
│   └── PdfReaderViewModel.kt
├── MainActivity.kt
├── PdfReaderActivity.kt
└── SettingsActivity.kt
```

## Setup

### 快速安装
从 [Releases](https://github.com/terefal/PDF-ai-reader/releases) 下载最新 APK 安装到 Android 设备。

### 从源码构建

```bash
git clone https://github.com/terefal/PDF-ai-reader.git
cd PDF-ai-reader
./gradlew assembleDebug
```

### AI 配置

在 App 内 **设置 → AI 模型设置**：
- **DeepSeek**（推荐）— 国内直连，需 [API Key](https://platform.deepseek.com/api_keys)
- **OpenAI** — 需代理 + [API Key](https://platform.openai.com/api-keys)
- **Ollama** — 本地部署，完全离线

### Gradle 镜像 (中国大陆)

项目已配置阿里云 Maven 镜像 + 腾讯云 Gradle 镜像，无需额外配置。

## Roadmap

- [x] 多模型 AI 对话 + 联网搜索
- [x] 笔记式架构 (NoteBook)
- [x] 左侧文件树
- [x] Web-Chat 上下文标签
- [x] PDF 高亮/下划线/批注
- [x] Markdown + 数学公式渲染
- [x] 多模态图片支持
- [ ] 手写批注
- [ ] 云同步
- [ ] 协作标注

## License

MIT License
