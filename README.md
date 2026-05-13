# PDF AI Reader

This is a powerful Android app designed for reading and annotating PDF documents. It integrates AI capabilities to assist users with real-time content understanding, note-taking, and context-based explanations.

## Key Features

- **PDF Viewing & Annotation**: Smooth PDF reading experience with support for annotations (highlight, underline, sticky notes).
- **AI Sidebar**: Context-aware assistant powered by AI models (OpenAI or local LLMs like Ollama).
- **Real-Time Screenshot Queries**: Select and screenshot specific PDF content to instantly query AI for understanding or further details.
- **Offline Mode**: Integrates local AI model for usage without internet connectivity.
- **Note Management**: Save, organize, and search your PDF notes.

---

## Project Structure

```
pdf-ai-reader/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/
│   │   │   │   └── com/terefal/pdfaireader/
│   │   │   │       ├── ui/                 # UI Screens
│   │   │   │       ├── viewmodel/          # ViewModels
│   │   │   │       ├── repository/         # Repositories
│   │   │   │       ├── ai/                 # AI Integration
│   │   │   │       ├── pdf/                # PDF Management
│   │   │   │       └── MainActivity.kt     # Main Activity
│   ├── build.gradle.kts
│   └── res/                                # Resource files
├── build.gradle.kts                        # Main Gradle file
├── README.md                               # Documentation
├── settings.gradle.kts                     # Gradle settings
└── LICENSE                                 # License file
```

---

## Technologies Used

- **Programming Language**: Kotlin
- **Frameworks**: Jetpack Compose, Android ViewModel, Room
- **PDF Library**: AndroidPdfViewer or MuPDF for rendering and interaction
- **AI Integration**:
  - [OpenAI API](https://openai.com/api/) for cloud-based AI features
  - [Local LLMs](https://ollamalabs.com/) for offline modes
- **Database**: SQLite with Room ORM
- **UI Design**: Jetpack Compose for modern UI interactions

---

## Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or newer
- Java 11 or higher
- Minimum SDK: 24
- Target SDK: 34

### Steps to Build Locally

1. Clone Repository:
   ```bash
   git clone https://github.com/terefal/PDF-ai-reader.git
   cd PDF-ai-reader
   ```

2. Build and Run:
   ```bash
   ./gradlew assembleDebug
   ```

3. Open in Android Studio and install on a connected emulator/device.

---

### Configuring AI

#### OpenAI API
- Add your API key as a build variable or in `local.properties`:
  ```properties
  openai.api.key=your_openai_api_key_here
  ```

#### Local LLM Integration
- Install Ollama and load required models:
  ```bash
  # Install Ollama
  brew install ollama

  # Load LLM models
  ollama pull llama2
  ```

---

## Current Roadmap

### Phase 1 (Current Target)
- [x] Set up project structure
- [ ] Implement basic PDF rendering and navigation
- [ ] Develop AI-powered contextual sidebar (OpenAI API support)
- [ ] Enable text/image-based screenshot querying

### Phase 2
- [ ] Build note-taking and organization features
- [ ] Extend support for offline AI functionality
- [ ] User settings for customizable AI prompts

### Phase 3
- [ ] Allow cloud-based note synchronization
- [ ] Support shared annotations/collaboration
- [ ] Advanced AI features (e.g., summarization, sentiment analysis)

---

## Contribution

We welcome contributions! Feel free to fork the repository, submit issues, or propose new features. Open a pull request anytime 🚀

---

## License

This project is licensed under the [MIT License](LICENSE).