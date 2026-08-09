# 馃帉 Anime AI 路 鍔ㄦ极鏅鸿兘鍔╂墜骞冲彴

> **鎴浘璇嗙暘 路 瑙掕壊璇嗗埆 路 鐣墽鎼滅储 路 AI 瀵硅瘽 路 RAG 鐭ヨ瘑搴?路 TTS/ASR**
> Screenshot-to-anime recognition 路 Character ID 路 Anime search 路 AI chat 路 RAG 路 TTS/ASR

[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-purple)](https://spring.io/projects/spring-ai)
[![RAG](https://img.shields.io/badge/RAG-鍚戦噺鐭ヨ瘑搴?8A2BE2)](#rag-鐭ヨ瘑搴?
[![ONNX](https://img.shields.io/badge/ONNX-瑙嗚妯″瀷-005B96)](https://onnxruntime.ai/)
[![TTS/ASR](https://img.shields.io/badge/TTS%2FASR-璁%20%7C%20DashScope-blue)](#澶氭ā鎬?
[![Redis](https://img.shields.io/badge/Redis-鑱婂ぉ璁板繂-DC382D)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

A full-featured **AI assistant platform built around anime**: upload a screenshot, let AI tell you which anime it's from, which episode, and which character is in it. Also includes anime search & recommendation, airing reminders, pet/plant care with AI, weather, maps, image generation and voice (TTS/ASR).

涓€涓互**鍔ㄦ极涓烘牳蹇?*鐨?AI 鏅鸿兘鍔╂墜骞冲彴锛氭埅鍥捐瘑鐣€佽鑹茶瘑鍒€佺暘鍓ф悳绱㈡帹鑽愩€佽拷鐣彁閱掞紝骞堕泦鎴愬ぉ姘斻€佸湴鍥俱€佸疇鐗╂姢鐞嗐€佸浘鐗囩敓鎴愩€佽闊崇瓑澶氭ā鎬?AI 鑳藉姏銆?

<p align="center">
  <img src="screenshots/demo.webp" alt="Demo" width="720"/>
</p>
---

## 鉁?Features / 鏍稿績鍔熻兘

### 馃幀 Anime AI (Core) / 鍔ㄦ极 AI锛堝钩鍙版牳蹇冿級

| Feature | Description |
|---------|-------------|
| **鎴浘璇嗙暘** Screenshot recognition | `trace.moe` real-time matching (similarity 鈮?82%) + AniList GraphQL lookup + multi-level fallback: visible title 鈫?local character index 鈫?recent anime catalog 鈫?current season 鈫?cross-era candidate comparison |
| **瑙掕壊璇嗗埆** Character ID | Compares hairstyle, eye color, outfit features 鈫?character name, anime, confidence |
| **鐣墽鏈嶅姟** Anime services | Search, by-ID query, seasonal/quarterly, upcoming, TOP list, character search, anime news |
| **杩界暘鎻愰啋** Airing alerts | Scheduled tasks + DB persistence for new anime/movie releases |

### 馃惥 AI 鐪嬫姢 Pet / Plant Care
- PetProfile & PlantProfile management, CareRecord, CareReminder
- Medical triage, pet food safety check, plant safety check, nearby pet hospitals (Amap)
- AI care workflow (`SpringAiCareWorkflowService`)

### 馃 閫氱敤 AI General AI
- Streaming chat via Spring AI with tool calling (`/api/ai/chat-with-tools`)
- Agent toolchain: image analysis / image editing / AI drawing / file parsing / web search / TTS
- Weather tool, Amap nearby search
- **Chat memory**: Redis + DB dual-layer, RAG Q&A via SQLite VectorStore
- Multi-turn sessions with `UserSession` + async message events

### 馃帣锔?澶氭ā鎬?Multimodal
- **TTS**: Xunfei speech synthesis (`XfTtsService`)
- **ASR**: Alibaba DashScope (`DashScopeAsrService`)
- **Vision**: ONNX Runtime (CUDA) (`VisionService`)
- **Image generation**: `ImageGenerationService`

### 馃摝 鍏朵粬 Extras
E-commerce module (products/categories), WeChat ilink SDK messaging, sensitive-word filtering.

## 馃洜锔?Tech Stack / 鎶€鏈爤

```text
Backend     Java 21 路 Spring Boot 3.5 路 Spring AI (spring-ai-bom) 路 WebFlux
Frontend    Thymeleaf
Data        H2 路 SQLite (VectorStore RAG) 路 Redis (chat memory) 路 MySQL (reserved)
AI Vision   ONNX Runtime (CUDA) 路 trace.moe API 路 AniList GraphQL
AI Voice    Xunfei TTS 路 DashScope ASR
External    Amap 路 weather API 路 AniList GraphQL 路 trace.moe
Others      Fastjson2 路 OkHttp3 路 HttpClient5 路 Lombok
```

## 馃搻 Project Structure / 椤圭洰缁撴瀯

```text
anime-ai
鈹溾攢鈹€ pom.xml
鈹溾攢鈹€ src/main/java/com/example/demo
鈹?  鈹溾攢鈹€ anime/          # 鎴浘璇嗙暘銆佽鑹茶瘑鍒€佺暘鍓ф湇鍔★紙鏍稿績锛?鈹?  鈹溾攢鈹€ ai/             # AI 瀵硅瘽銆佸伐鍏疯皟鐢ㄣ€佸姩婕簨浠舵湇鍔?鈹?  鈹溾攢鈹€ agent/          # Agent 宸ュ叿閾撅紙鍥剧墖/鏂囦欢/鎼滅储/TTS锛?鈹?  鈹溾攢鈹€ aicare/         # 瀹犵墿/妞嶇墿鎶ょ悊鍏ュ彛
鈹?  鈹溾攢鈹€ care/           # 鎶ょ悊鏈嶅姟锛堝垎璇娿€佸畨鍏ㄣ€佹彁閱掋€侀檮杩戝尰闄級
鈹?  鈹溾攢鈹€ chat/           # 鑱婂ぉ璁板繂銆佷細璇濄€丷AG 鍚戦噺搴?鈹?  鈹溾攢鈹€ vision/         # ONNX 瑙嗚鎺ㄧ悊
鈹?  鈹溾攢鈹€ asr/ 路 tts/     # 璇煶璇嗗埆 / 璇煶鍚堟垚
鈹?  鈹溾攢鈹€ imagegen/       # AI 鐢诲浘
鈹?  鈹溾攢鈹€ movie/          # 杩界暘/鐢靛奖鎻愰啋
鈹?  鈹溾攢鈹€ weather/        # 澶╂皵鏌ヨ
鈹?  鈹溾攢鈹€ service/        # 楂樺痉鍦板浘
鈹?  鈹斺攢鈹€ web/            # 椤甸潰鎺у埗鍣?鈹溾攢鈹€ tools/                  # 鏈湴宸ュ叿鑴氭湰
鈹斺攢鈹€ rag_knowledge.sqlite    # RAG 鐭ヨ瘑搴?```

## 鈻讹笍 Quick Start / 蹇€熷紑濮?
### 1. 閰嶇疆鐜鍙橀噺 Environment variables

Create the following env vars (or edit `src/main/resources/application.properties`):

| Variable | Required | Description |
|----------|----------|-------------|
| `DEEPSEEK_API_KEY` | 鉁?| DeepSeek (main chat / tool calling) |
| `DASHSCOPE_API_KEY` | 鈿狅笍 | DashScope embedding / vision / ASR |
| `MYSQL_PASSWORD` | 鈿狅笍 | MySQL password |
| `SENIVERSE_API_KEY` | 鉂?| Weather API (蹇冪煡澶╂皵) |
| `AMAP_API_KEY` | 鉂?| Amap web services (nearby search) |
| `XUNFEI_APP_ID` / `XUNFEI_API_KEY` / `XUNFEI_API_SECRET` | 鉂?| Xunfei TTS |

### 2. 杩愯 Run

```powershell
mvn spring-boot:run
# 鈫?http://localhost:8094
```

### 3. 鍙€夛細鏈湴瑙嗚妯″瀷 Optional: local vision model

Copy `vision_model.onnx` into `data/` for offline ONNX inference (model file not included due to size; use any ONNX-compatible vision model).

## 馃摑 Notes / 璇存槑

- All external API keys are read from **environment variables** 鈥?never hardcode secrets.
- `data/`: DB files & vision models (large files not committed).
- Demo screenshots and the `tools/` folder contain helper scripts for local development.

## 馃搫 License

[MIT](LICENSE) 漏 2026 [sekai-lyr](https://github.com/sekai-lyr)

---

**猸?If this project helped you, star it! 濡傛灉杩欎釜椤圭洰瀵逛綘鏈夊府鍔╋紝娆㈣繋 Star锛?*

