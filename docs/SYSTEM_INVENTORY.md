# Thống kê chức năng, service và cấu hình

Đối chiếu mã nguồn ngày 2026-10-31. Đây là thống kê implementation và cấu hình
mặc định, không phải xác nhận các chức năng/service đang được bật trên VPS.
Ubuntu, PostgreSQL và RustFS đã có theo thông tin chủ hệ thống; kết nối production
chưa được kiểm chứng trong lượt thống kê này.

## 1. Chức năng đã triển khai

Các chức năng Web bên dưới sử dụng Next.js và Spring Boot cùng PostgreSQL.
Service bổ sung được ghi ở cột cuối.

| Nhóm | Chức năng hiện có | Màn hình/phạm vi | Service bổ sung |
| --- | --- | --- | --- |
| Đăng nhập | Google OAuth/OIDC, đăng xuất, session HttpOnly, giới hạn một email owner đã xác minh, CSRF | `/login`, workspace riêng tư | Google OAuth |
| Thư viện ghi chú | Danh sách, đọc bài, ngày cập nhật, collection/tags, visibility, thời gian đọc, mục lục | `/`, `/knowledge/{slug}` | Không |
| Tạo/sửa ghi chú | Title, summary, Markdown, visibility, collection, tags; autosave tuần tự cho bài đã lưu | `/knowledge/new`, `/knowledge/{slug}/edit` | Không; RustFS khi upload ảnh |
| Soạn thảo | Milkdown/Crepe, toolbar, slash/block menu, kéo thả block, lists, bảng, link, CodeMirror | Editor | Thư viện chạy trong browser |
| Xóa ghi chú | Xóa bài và xử lý quan hệ/lịch sử/đưa object vào hàng đợi cleanup | Backend `DELETE /api/knowledge/{id}`; chưa có nút xóa trên Web hiện tại | RustFS cho cleanup ảnh |
| Collections | Tạo, đổi tên, xóa collection, liệt kê ghi chú theo collection; xóa collection chỉ bỏ phân loại bài | `/collections`, `/collections/{id}` | Không |
| Tags | Thêm/xóa tag trên bài, tái sử dụng tag đã lưu, hỗ trợ tìm kiếm | Editor và metadata bài | Không |
| Tìm kiếm từ khóa | PostgreSQL Full Text Search và Quick Search bằng Cmd/Ctrl+K | `/search`, overlay toàn workspace | Không; không gọi Gemini |
| Chia sẻ | Private, Public theo slug, Unlisted theo secret token; copy link và xác nhận rotate token | Share dialog, `/k/{slug}`, `/s/{shareToken}` | Không |
| Lịch sử | Checkpoint, phân trang, xem nội dung revision, restore; giữ slug và trạng thái chia sẻ | `/knowledge/{slug}/history` | RustFS nếu revision có ảnh |
| Ảnh đính kèm | Upload PNG/JPEG/WebP/GIF cho bài đã tạo; `attachment://UUID`; đọc theo quyền owner/public/unlisted/history | Editor và các trang đọc | RustFS/S3-compatible private bucket |
| Liên kết kiến thức | Wiki link `[[stable-slug]]`, backlinks, related notes theo link/tag/collection | Trang đọc owner | Không; không dùng AI |
| Knowledge Graph | Đồ thị wiki links, note cô lập, lọc collection, focus hàng xóm, pan/zoom/fit view và mở bài | `/graph`, owner-only | Không; layout và canvas trong browser |
| Hiển thị kỹ thuật | Markdown, bảng, code highlighting, inline code, Mermaid fence | Owner/Public/Unlisted/History | Thư viện browser; không cần Mermaid server |
| Semantic Search | Tìm theo ý nghĩa, explicit submit, một đoạn ngữ cảnh phù hợp trên mỗi bài | Chế độ Semantic của `/search`, owner-only | Gemini embeddings + pgvector |
| Ask My Knowledge | Hỏi một câu trên ghi chú hiện tại, trả lời có citation/evidence và link về nguồn | `/ask`, owner-only | Gemini embeddings + generation + pgvector |
| Gợi ý summary/tags | Chủ động lưu bản nháp, yêu cầu gợi ý, xem và Apply/Dismiss; chỉ cho bài đã tạo | Editor | Gemini generation; độc lập với embeddings/Ask |

Phạm vi thực tế cần phân biệt:

- Tags trong Editor đã hoạt động; chưa có trang quản lý Tags độc lập.
- Các mục sidebar `Tags`, `Private`, `Shared`, `Drafts` còn là `href="#"`; không
  được tính là các màn hình quản lý/lọc riêng đã hoàn thành.
- New note là chế độ chưa lưu; không có hệ thống quản lý draft riêng.
- Chưa có cộng tác nhiều người, multi-user, chat lưu lịch sử, graph editing,
  public search/listing, upload file tổng quát hoặc tự áp dụng metadata AI.
- Ask không lưu câu hỏi/đáp án thành lịch sử hội thoại. Gợi ý AI chỉ được lưu
  sau thao tác áp dụng của owner qua autosave thông thường.

## 2. Job nền và công cụ nội bộ

| Thành phần | Đã có | Cách chạy/service |
| --- | --- | --- |
| Embedding index/backfill | Chia chunk, tạo vector, cập nhật index khi dữ liệu/model thay đổi | Scheduler trong API; cần bật embeddings và indexing; gọi Gemini |
| Attachment cleanup | Dọn ảnh orphan sau grace period, giữ ảnh còn trong bài/revision, retry xóa object | Scheduler trong API; PostgreSQL + RustFS |
| AI quota | Giới hạn RPM/TPM/RPD; counter bền vững trong PostgreSQL | Trong API; không cần Redis |
| Quota retention | Dọn counter đã hết hạn theo lô | Scheduler trong API; không reset quota Google |
| Flyway | Quản lý/validate migration schema khi API khởi động | Trong API; PostgreSQL + quyền migration phù hợp |
| Actuator health | Endpoint health tổng quát | `/actuator/health`; không thay thế smoke test RustFS/Google/Gemini |
| Retrieval/metadata evaluation | Bộ kiểm thử offline và task live opt-in | Công cụ phát triển/test, không cần chạy để phục vụ người dùng |
| CI | Build/test riêng Web và API | GitHub Actions; không phải service runtime của VPS |

Không cần tạo PM2 worker hay cron riêng cho ba scheduler hiện có.
Backup định kỳ, log rotation, TLS renewal và cảnh báo vận hành là phần hạ tầng
cần cấu hình khi deploy; không phải tính năng backup/monitoring đã tích hợp sẵn.

## 3. Service cần để hoạt động

| Service/runtime | Mức cần thiết | Dữ liệu/trách nhiệm | Ghi chú cho VPS hiện tại |
| --- | --- | --- | --- |
| Next.js / Node.js | Bắt buộc cho Web | UI, Server Actions, Route Handlers, chuyển session/CSRF sang API | Node 22 theo CI; PM2 `knowledge-web` dự kiến |
| Spring Boot / Java | Bắt buộc cho API | Auth, nghiệp vụ, tìm kiếm, DB/storage/AI, scheduler | Java 17; PM2 `knowledge-api` dự kiến |
| PostgreSQL | Bắt buộc | Bài, collection/tag, revision, share token, attachment metadata, vector/chunks, quota | Đã có; repo kiểm thử với PostgreSQL 17, cần kiểm kê major thực tế |
| pgvector | Bắt buộc với schema hiện tại | Kiểu `vector` và phép tính tương đồng | Extension bên trong PostgreSQL, không phải daemon mới; cần cài đúng major và enable trong DB ứng dụng, kể cả lúc tắt AI |
| RustFS | Cần cho chức năng ảnh đầy đủ | Bytes ảnh trong bucket private | Đã có; API dùng S3 SDK. Cấu hình storage hiện vẫn được validate lúc startup; RustFS gián đoạn ảnh hưởng upload/read/cleanup |
| Google OAuth/OIDC | Cần cho đăng nhập owner | Xác thực tài khoản Google được allowlist | Cần OAuth client và callback HTTPS; không phải PostgreSQL login |
| Google Gemini | Tùy chọn | Embeddings, Ask generation, gợi ý metadata | Không cần key/kết nối Gemini nếu cả ba nhóm đều tắt |
| Nginx + TLS/DNS | Cần trong mô hình production đã chọn | HTTPS, routing OAuth/Web, reverse proxy | Giữ hoặc cấu hình reverse proxy hiện có; không đưa API/DB port ra internet |
| PM2 | Theo cách vận hành VPS của bạn | Giám sát/restart hai app và phục hồi sau reboot | Không thay thế Nginx, PostgreSQL hay RustFS |

Không có dependency runtime hiện tại vào Redis, Elasticsearch, Qdrant/Milvus,
Kafka/RabbitMQ, SMTP, local LLM hoặc một service render Mermaid riêng. FTS dùng
PostgreSQL; vector dùng pgvector; các job nền chạy trong Spring Boot.

## 4. Cấu hình Web cần có

| Biến | Mục đích | Giá trị production dự kiến |
| --- | --- | --- |
| `NODE_ENV` | Chế độ runtime | `production` |
| `KNOWLEDGE_API_BASE_URL` | Next server gọi Java | `http://127.0.0.1:8080` hoặc port đã chọn |
| `KNOWLEDGE_API_BROWSER_BASE_URL` | URL ngoài browser dùng bắt đầu OAuth | `https://<domain>` |

Port/host Web đặt qua launcher Next, ví dụ loopback và port 3000. Không chuyển
hai biến URL này hoặc credential thành `NEXT_PUBLIC_*`.

## 5. Cấu hình API bắt buộc/cơ bản

| Nhóm | Biến cần chuẩn bị | Ý nghĩa |
| --- | --- | --- |
| PostgreSQL | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối database ứng dụng |
| Owner | `AUTH_ALLOWED_EMAIL`, `APP_OWNER_ID` | Email Google được phép và UUID partition của dữ liệu; giữ UUID khi chuyển dữ liệu |
| Google OAuth | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | OAuth Web client |
| Web origin | `WEB_BASE_URL` | `https://<domain>` để chuyển về Web sau login/failure |
| Cookie | `SESSION_COOKIE_SECURE` | `true` khi production HTTPS |
| RustFS | `OBJECT_STORAGE_ENDPOINT`, `OBJECT_STORAGE_REGION`, `OBJECT_STORAGE_BUCKET`, `OBJECT_STORAGE_ACCESS_KEY`, `OBJECT_STORAGE_SECRET_KEY`, `OBJECT_STORAGE_PATH_STYLE` | Endpoint S3, region, bucket private và credential có quyền cần thiết; path-style `true` theo phương án RustFS |
| Server/proxy | `SERVER_ADDRESS`, `SERVER_PORT`, `SERVER_FORWARD_HEADERS_STRATEGY` | Override chuẩn Spring Boot cho deploy: loopback, port API, `framework` sau trusted proxy |
| Migration | `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` khi tách role | Migration role riêng; runtime datasource không dùng superuser |

Callback phải đăng ký trong Google Cloud:

```text
https://<domain>/login/oauth2/code/google
```

Session hiện nằm trong memory của API, timeout mặc định 30 phút; restart API làm
session mất hiệu lực. Bước triển khai đầu dùng một API instance.

Spring Boot không tự nạp `.env`; PM2 launcher phải inject các biến hoặc dùng
external configuration. Không ghi secret vào repository/PM2 ecosystem được commit.

## 6. Cấu hình AI theo chức năng

Giá trị dưới đây là default trong code/config của repo, không phải xác nhận quota
hay model được cấp cho tài khoản Google thực tế.

| Chức năng | Điều kiện bật | Default |
| --- | --- | --- |
| Semantic Search | `GEMINI_API_KEY`, `EMBEDDING_ENABLED=true`; index compatible đã có | Tắt; model `gemini-embedding-2`, 768 dimensions |
| Indexing/backfill | `EMBEDDING_ENABLED=true`, `EMBEDDING_INDEXING_ENABLED=true` | Index flag `true`, nhưng worker không chạy nếu embeddings tắt |
| Ask | Key, embeddings/index và `ASK_ENABLED=true` | Tắt; model `gemini-3.5-flash-lite` |
| Summary/tags | Key và `AI_METADATA_ENABLED=true` | Tắt; model `gemini-3.5-flash-lite`; không cần bật embeddings/Ask |

Các nhóm tuning đã có:

- Embeddings: `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS`, `EMBEDDING_BASE_URL`,
  `EMBEDDING_CONNECT_TIMEOUT`, `EMBEDDING_READ_TIMEOUT`, `EMBEDDING_BATCH_SIZE`,
  `EMBEDDING_INDEX_INTERVAL`, `EMBEDDING_INDEX_INITIAL_DELAY`,
  `EMBEDDING_KNOWLEDGE_BATCH_SIZE`, `EMBEDDING_MAX_CHUNK_CHARS`, `EMBEDDING_OVERLAP_CHARS`.
- Ask: `ASK_MODEL`, `ASK_BASE_URL`, `ASK_CONNECT_TIMEOUT`, `ASK_READ_TIMEOUT`,
  `ASK_MAX_OUTPUT_TOKENS`, `ASK_MAX_CONTEXT_CHARS`, `ASK_MAX_CHUNKS`,
  `ASK_MAX_CHUNKS_PER_KNOWLEDGE`, `ASK_MAX_SOURCES`.
- Metadata: `AI_METADATA_MODEL`, `AI_METADATA_BASE_URL`,
  `AI_METADATA_CONNECT_TIMEOUT`, `AI_METADATA_READ_TIMEOUT`,
  `AI_METADATA_MAX_OUTPUT_TOKENS`, `AI_METADATA_MAX_CONTENT_CHARS`.
- Quota cho từng nhóm: `*_QUOTA_ENABLED`, `*_RPM_LIMIT`, `*_TPM_LIMIT`,
  `*_RPD_LIMIT`, `*_TOKEN_ESTIMATE_CHARS_PER_TOKEN`, `*_RPD_RESET_ZONE` với các
  prefix `EMBEDDING`, `ASK`, `AI_METADATA`. Embeddings còn có các giới hạn
  background riêng `EMBEDDING_BG_RPM_LIMIT`, `EMBEDDING_BG_TPM_LIMIT`, `EMBEDDING_BG_RPD_LIMIT`.

AI gửi nội dung/query liên quan đến Gemini khi được bật. Bật Semantic/Ask không
làm thay đổi dependency của CRUD, FTS hay Graph. Thay model/dimension phải tính
việc reindex; dữ liệu vector incompatible không được đưa vào kết quả.

## 7. Cấu hình ảnh, revision và housekeeping

| Biến/nhóm | Default chính | Tác dụng |
| --- | --- | --- |
| `KNOWLEDGE_REVISION_INTERVAL` | `PT5M` | Khoảng thời gian checkpoint đủ điều kiện khi autosave |
| `KNOWLEDGE_IMAGE_MAX_SIZE` | `10MB` | Giới hạn ảnh và cấu hình multipart mặc định hiện tại |
| `KNOWLEDGE_IMAGE_MAX_DIMENSION` / `KNOWLEDGE_IMAGE_MAX_PIXELS` | `20000` / `100000000` | Giới hạn kích thước và số pixel ảnh |
| `KNOWLEDGE_ATTACHMENT_CLEANUP_ENABLED` | `true` | Bật dọn attachment |
| `KNOWLEDGE_ATTACHMENT_ORPHAN_GRACE_PERIOD` | `PT24H` | Grace period ảnh chưa được tham chiếu |
| `KNOWLEDGE_ATTACHMENT_CLEANUP_INTERVAL` / `INITIAL_DELAY` / `BATCH_SIZE` | `PT1H` / `PT5M` / `100` | Lịch và giới hạn mỗi batch; các tên đầy đủ cùng prefix `KNOWLEDGE_ATTACHMENT_CLEANUP_` |
| `AI_QUOTA_CLEANUP_ENABLED` | `true` | Dọn counter hết hạn |
| `AI_QUOTA_MINUTE_RETENTION` / `AI_QUOTA_DAILY_RETENTION` | `P2D` / `P30D` | Thời gian giữ counter đã hết hạn |
| `AI_QUOTA_CLEANUP_INTERVAL` / `INITIAL_DELAY` / `BATCH_SIZE` | `PT6H` / `PT10M` / `1000` | Lịch và batch quota cleanup; tên đầy đủ cùng prefix `AI_QUOTA_CLEANUP_` |

Proxy/multipart timeout và giới hạn body cần đồng bộ với giới hạn ảnh. Chi tiết
đã có trong [kế hoạch Oracle/PM2](DEPLOYMENT_ORACLE_PM2.md).

## 8. Tình trạng với VPS của bạn

| Hạng mục | Thông tin hiện có | Còn cần xác nhận |
| --- | --- | --- |
| Ubuntu / PM2 | Đã có | Version, CPU/RAM, kiến trúc, PM2 user và port |
| PostgreSQL | Đã có | Major/native hay container; DB/role và backup |
| pgvector | Chưa có | Cài extension đúng server major và enable trong DB ứng dụng |
| RustFS | Đã có | Endpoint S3/region, bucket private, credential, smoke test SDK và ảnh cũ nếu có |
| Nginx/domain/TLS | Chưa xác nhận | Routing, certificate, OAuth callback, firewall OCI/Ubuntu |
| Google OAuth | Đã có implementation | Cấu hình client production và allowlist |
| Gemini | Tùy chọn | Key/quota/flags chỉ cần nếu triển khai nhóm AI |

## 9. Nguồn đối chiếu trong repository

- [API env example](../apps/api/.env.example) và [Web env example](../apps/web/.env.example).
- [Application config](../apps/api/src/main/resources/application.yml): đọc cấu
  hình qua cơ chế bảo vệ secret, không sao chép credential ra báo cáo.
- [Metadata defaults](../apps/api/src/main/resources/metadata-suggestions.properties):
  các biến `AI_METADATA_*` nằm ở đây và chưa được liệt kê đầy đủ trong API `.env.example`.
- [Architecture](ARCHITECTURE.md), [Semantic Retrieval](SEMANTIC_RETRIEVAL.md),
  [Ask](ASK_MY_KNOWLEDGE.md), [AI Metadata](AI_METADATA_SUGGESTIONS.md),
  [Attachment Lifecycle](ATTACHMENT_LIFECYCLE.md).
- Controller, Route Handler, scheduler và cấu hình storage đã được đối chiếu trực tiếp.

Thống kê này chỉ bổ sung tài liệu; không đổi cấu hình hay service đang chạy.
