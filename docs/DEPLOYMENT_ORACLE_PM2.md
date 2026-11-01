# Kế hoạch triển khai Oracle VPS với PM2

Ngày lập: 2026-10-31. Trạng thái: kế hoạch triển khai, chưa thực thi trên VPS.

Đã bổ sung [hướng dẫn thực thi và bộ file PM2](../infra/pm2/README.md).
Theo phạm vi cập nhật, chủ hệ thống tự cấu hình Nginx/HTTPS/domain; bảng routing
trong kế hoạch là thông tin bàn giao, không phải cấu hình đã được áp dụng.

Đã có [shell scripts triển khai](../infra/pm2/SCRIPTS.md). Theo lựa chọn vận hành
mới nhất, PostgreSQL và RustFS ở service riêng: update thông thường không có
backup gate bắt buộc. Script chỉ báo thay đổi file migration; backup/snapshot
khi thay đổi schema/data đáng kể do chủ hệ thống quyết định ở các service đó.

## 1. Hạ tầng và phạm vi

Thông tin đã xác nhận:

- VPS Oracle chạy Ubuntu, đang quản lý ứng dụng bằng PM2.
- Đã có PostgreSQL, chưa có pgvector.
- Đã có RustFS thay thế MinIO.
- Ứng dụng gồm Next.js và Spring Boot; PostgreSQL và RustFS được giữ lại.

Cần kiểm kê trước khi thực thi: phiên bản Ubuntu, kiến trúc ARM64/x86_64,
CPU/RAM/disk còn trống, cách PostgreSQL đang chạy (native/container), major của
PostgreSQL, endpoint/region của RustFS, domain, vị trí TLS/reverse proxy hiện tại
và user PM2 đang sử dụng. Các giá trị này chưa được giả định là đã kiểm chứng.

Phạm vi triển khai là đóng gói, cấu hình runtime, routing, migration và
vận hành tính năng hiện có. Không thay đổi nghiệp vụ, thêm Redis hay chuyển
database/storage đang có sang một stack mới.

## 2. Mô hình chạy

```text
Internet → HTTPS/domain → Nginx
                          ├─ các URL ứng dụng + /api/* → Next.js 127.0.0.1:3000
                          └─ /oauth2/* và /login/oauth2/* → Spring Boot 127.0.0.1:8080

Next.js → Spring Boot qua loopback
Spring Boot → PostgreSQL hiện tại + pgvector
Spring Boot → bucket private trên RustFS hiện tại
```

Nếu port 3000/8080 đang được ứng dụng khác sử dụng, chọn hai port loopback trống
và cập nhật cấu hình tương ứng trước khi triển khai.

| Thành phần | Quản lý | Cấu hình ban đầu |
| --- | --- | --- |
| Next.js | PM2, `knowledge-web` | Production `next start`, một instance, bind loopback |
| Spring Boot | PM2, `knowledge-api` | Executable JAR, Java 17, fork, một instance, bind loopback |
| PostgreSQL | Giữ cơ chế hiện có | Bổ sung pgvector đúng major; DB/role riêng cho ứng dụng |
| RustFS | Giữ cơ chế hiện có | Bucket private và credential riêng cho ứng dụng |
| Nginx/TLS | Giữ hoặc cấu hình dịch vụ hệ thống hiện có | Reverse proxy, HTTPS và certificate renewal |

Node.js 22 theo CI hiện tại; Java 17 theo Gradle toolchain. Cấu hình RAM/heap chỉ
chốt sau kiểm kê các service thực tế đang dùng chung VPS. PostgreSQL và RustFS
được quản lý riêng; không giả định cùng host với ứng dụng. Java heap
không phải tổng RSS; ngưỡng PM2 memory restart không phải giới hạn RAM cứng.

## 3. Thứ tự triển khai và điều kiện hoàn tất

| Bước | Công việc | Điều kiện qua bước |
| --- | --- | --- |
| 1 | Kiểm kê VPS, port, PM2, PostgreSQL/RustFS, domain và revision phát hành | Chốt được topology, phiên bản và tài nguyên; không ảnh hưởng ứng dụng khác |
| 2 | Xác nhận endpoint và quyền truy cập PostgreSQL/RustFS | Service sẵn sàng; không có backup gate cho update thông thường |
| 3 | Cài pgvector, chuẩn bị DB/role, kiểm tra RustFS | Extension khả dụng trong DB đích; S3 operations của SDK hiện tại hoạt động |
| 4 | Chuẩn bị cấu hình production và release artifacts | CI của commit phát hành đạt; không đóng gói credential thật |
| 5 | Khởi động API rồi Web qua PM2 trên loopback | API health đạt; Web trả trang; DB/Flyway không lỗi |
| 6 | Áp dụng Nginx, TLS, DNS và callback Google OAuth | Login/logout, cookie và CSRF hoạt động qua domain HTTPS |
| 7 | Smoke test tính năng, PM2 startup và phương án rollback | Danh sách nghiệm thu bên dưới đạt; xác định tương thích schema trước rollback |

Không chạy `infra/docker/compose.yml` hiện có như cấu hình production: file đó
cung cấp PostgreSQL/MinIO phát triển, credential mặc định và port công khai.

## 4. Bổ sung pgvector vào PostgreSQL hiện tại

1. Xác định **major của PostgreSQL server**, không chỉ version của `psql` client.
   Repository đang kiểm thử với PostgreSQL 17; nếu server khác major, xác minh
   migration/query trên bản sao DB cùng major trước khi chốt triển khai.
2. Backup trước khi thay đổi package/image. Không nâng PostgreSQL major chỉ để
   cài extension trong đợt deploy này.
3. Nếu PostgreSQL chạy native Ubuntu: kiểm tra nguồn package, mô phỏng thay đổi
   package, rồi cài `postgresql-<major>-pgvector` tương ứng với server. Dùng nguồn
   hỗ trợ kiến trúc của VPS và pin phiên bản đã kiểm chứng.
4. Nếu PostgreSQL chạy container: bổ sung extension vào image cùng PostgreSQL
   major và kiến trúc, kiểm thử trên bản sao volume. Cài package trên Ubuntu host
   sẽ không làm extension xuất hiện bên trong container.
5. DBA tạo extension trong **database của ứng dụng**, không dùng database mặc định
   một cách tùy ý:

   ```sql
   SELECT version();
   SELECT name, default_version, installed_version
   FROM pg_available_extensions WHERE name = 'vector';

   -- Thực hiện bằng role quản trị trong đúng database đích:
   CREATE EXTENSION IF NOT EXISTS vector;
   SELECT extversion FROM pg_extension WHERE extname = 'vector';
   SELECT '[1,2,3]'::vector;
   ```

6. Tạo role/schema phù hợp cho ứng dụng. Runtime role không là superuser; dùng
   role migration riêng qua `SPRING_FLYWAY_USER`/`SPRING_FLYWAY_PASSWORD` khi cần,
   cùng quyền schema/default privileges cho các bảng và sequence được tạo.
7. Chạy Flyway trên bản sao DB trước. V8 hiện có câu `CREATE EXTENSION IF NOT
   EXISTS vector`, nên extension phải sẵn sàng trước lần khởi động API production.
   pgvector vẫn cần cho schema dù các tính năng Gemini đang tắt.

Hướng dẫn cài package/extension: [pgvector chính thức](https://github.com/pgvector/pgvector#installation).

## 5. Kết nối RustFS hiện có

Code đã dùng AWS SDK for Java S3, nhận endpoint, region, bucket, credential và
path-style từ cấu hình. Dự kiến tích hợp qua cấu hình trước; cần smoke test với
đúng phiên bản RustFS đang chạy để xác nhận tương thích.

- Dùng endpoint S3 của RustFS; không dùng URL của console quản trị làm endpoint.
- Tạo/chọn bucket private; credential riêng, quyền giới hạn vào bucket. Quyền
  cần bao gồm đọc/ghi/xóa object vì attachment cleanup có thao tác xóa.
- Đặt `OBJECT_STORAGE_PATH_STYLE=true`; region theo cấu hình RustFS thực tế.
- Endpoint loopback/private khi cùng VPS; nếu storage ở máy khác, dùng kết nối
  và TLS phù hợp với mạng triển khai.
- Kiểm chứng upload, read/HEAD và delete bằng SDK/version của ứng dụng; sau đó
  kiểm thử qua UI và các route owner/PUBLIC/UNLISTED/History.
- Nếu ảnh cũ còn ở MinIO, sao chép nguyên bucket/object key cùng metadata DB và
  kiểm tra ảnh trước khi đổi endpoint. Giữ nguyên `attachment://<UUID>` trong
  Markdown và `APP_OWNER_ID` của dữ liệu đã có.
- Nếu đang chuyển dữ liệu storage, tạm tắt attachment cleanup trong cửa sổ
  chuyển; bật lại sau khi DB/bucket và đường dẫn truy cập đã được xác minh.

Tham khảo [giao thức S3 của RustFS](https://github.com/rustfs/docs.rustfs.com/blob/main/content/en/administration/protocols/s3.md).

## 6. Cấu hình production

Ví dụ domain trong kế hoạch là `knowledge.example.com`, cần thay bằng domain thật.
Secret đặt ngoài release/repository, chỉ user triển khai được đọc. Spring Boot
không tự đọc file `.env`; launcher phải nạp env trước `exec java` hoặc dùng cơ
chế external configuration của Spring Boot. Không ghi secret thật vào ecosystem.

| Phạm vi | Biến/cấu hình | Giá trị/ý nghĩa |
| --- | --- | --- |
| Web | `NODE_ENV` | `production` |
| Web | `KNOWLEDGE_API_BASE_URL` | `http://127.0.0.1:8080` hoặc port API đã chọn |
| Web | `KNOWLEDGE_API_BROWSER_BASE_URL` | `https://knowledge.example.com`, để browser đi qua Nginx khi login |
| API | `SERVER_ADDRESS`, `SERVER_PORT` | `127.0.0.1`, `8080` hoặc port đã chọn |
| API | `SERVER_FORWARD_HEADERS_STRATEGY` | `framework`, nhận HTTPS/host thật từ Nginx cho OAuth |
| API | `WEB_BASE_URL` | `https://knowledge.example.com` |
| API | `SESSION_COOKIE_SECURE` | `true` |
| API | `DB_*` | Database/role của ứng dụng trên PostgreSQL hiện tại |
| API | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `AUTH_ALLOWED_EMAIL` | OAuth client và owner allowlist hiện có |
| API | `APP_OWNER_ID` | Giữ UUID gắn với dữ liệu cũ; không sinh lại theo mỗi release |
| API | `OBJECT_STORAGE_*` | Endpoint/region/bucket/credential RustFS; path-style `true` |
| API | `EMBEDDING_ENABLED`, `ASK_ENABLED`, `AI_METADATA_ENABLED` | Tắt ở lượt bring-up; bật theo cấu hình tính năng đã chốt sau smoke test |

Google OAuth callback phải đăng ký chính xác:

```text
https://knowledge.example.com/login/oauth2/code/google
```

Giữ toàn bộ backend/storage/Gemini credential ở server, không chuyển thành
`NEXT_PUBLIC_*`. Bật AI chỉ sau khi xác nhận key, quota, model và xử lý dữ liệu
riêng tư; không chạy các task live evaluation trong pipeline deploy.

## 7. Nginx và mạng Oracle

Routing quan trọng đối với code hiện tại:

| URL ngoài browser | Upstream |
| --- | --- |
| `/oauth2/…` | Spring Boot, giữ nguyên URI |
| `/login/oauth2/…` | Spring Boot, giữ nguyên URI |
| `/login`, `/api/auth/login`, `/api/auth/logout` | Next.js |
| `/api/knowledge-search`, các API Ask/metadata/attachments và các URL còn lại | Next.js |

Không đặt location tổng quát `/api/ → Spring Boot`: nó sẽ bắt nhầm các Route
Handler của Next.js. Không route `/login/` quá rộng; callback dùng prefix riêng
`/login/oauth2/`, còn trang `/login` thuộc Web.

Nginx giữ `Host`, đặt `X-Forwarded-Host`, `X-Forwarded-Proto` và port phù hợp;
overwrite header từ client tại edge đáng tin cậy. Điều này cần cho callback OAuth
và kiểm tra Host/Origin của Server Actions/Ask. Không strip prefix OAuth.

- HTTPS và tự gia hạn certificate. Kiểm tra `nginx -t` trước reload.
- Tắt proxy buffering cho luồng Next.js để giữ streaming; không thêm proxy cache
  vào private, UNLISTED, API hoặc các response `no-store`.
- Đặt giới hạn body có phần dư cho multipart (ví dụ Nginx 16 MiB với cấu hình
  Spring request 12 MiB); app vẫn giữ giới hạn file ảnh 10 MiB hiện có. Chốt các
  lớp giới hạn theo byte để tránh file hợp lệ sát ngưỡng bị từ chối do overhead.
  Cụ thể có thể override `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=12MB`, còn
  `KNOWLEDGE_IMAGE_MAX_SIZE=10MB`; không tăng giới hạn file để sửa overhead request.
- Timeout proxy phải lớn hơn ngân sách gọi embeddings + generation; khởi điểm
  120 giây, xác minh bằng request thực tế và timeout runtime Node/Java.
- Log riêng cho ứng dụng; tránh ghi query/text câu hỏi hoặc bearer token của
  `/s/<token>` và route shared attachment vào access/error log không được bảo vệ.
- Chỉ mở inbound cần thiết trên OCI NSG/security list **và** firewall Ubuntu:
  80/443 cho ứng dụng, SSH giới hạn nguồn quản trị. Không mở thêm 3000, 8080,
  PostgreSQL hay console RustFS cho internet; giữ các luồng hạ tầng hiện có cần
  thiết cho những ứng dụng khác trên VPS.

Nguồn: [Next.js self-hosting](https://nextjs.org/docs/app/guides/self-hosting),
[NGINX reverse proxy](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy),
[Oracle network security](https://docs.oracle.com/en-us/iaas/Content/Security/Reference/compute_security.htm#network-security).

## 8. PM2 và release

PM2 dùng user hiện tại đang quản lý VPS, với hai tên process riêng. Mỗi app một
instance; không bật watch hoặc cluster trong đợt đầu. API hiện lưu session trong
memory nên restart làm người dùng đăng nhập lại; rolling reload nhiều API process
chưa phải một phương án zero-downtime hợp lệ cho kiến trúc hiện tại.

Bố cục dự kiến:

```text
/home/ubuntu/knowledge-application/
  releases/release-<timestamp>-<pid>/
  current -> releases/release-<timestamp>-<pid>/
  shared/api.env
  shared/web.env
  shared/logs/
```

Các file triển khai đã có trong repository:

| File | Trách nhiệm |
| --- | --- |
| `infra/pm2/ecosystem.config.js` | Hai app, fork/single instance, log, restart delay, kill timeout; nằm ngoài package ESM của Web |
| `infra/pm2/start-api.sh` | Nạp env, resolve release hiện tại, `exec` Java bằng đường dẫn đã chốt |
| `infra/pm2/start-web.sh` | Nạp env, resolve release hiện tại, `exec` Node chạy Next production trên loopback |
| `infra/pm2/setup.sh` | Tạo thư mục deploy và mẫu cấu hình, giữ nguyên env đã có |
| `infra/pm2/deploy-first.sh` | Clone main, build cả hai app, khởi động API rồi Web và kiểm tra health |
| `infra/pm2/deploy-update.sh` | Build release mới, báo migration thay đổi, chuyển current, restart hai app và kiểm tra health |
| `infra/pm2/restart-current.sh` | Khởi động lại release đang chọn sau khi sửa cấu hình/hạ tầng, không build |
| `infra/pm2/rollback.sh` | Đổi release sau khi xác nhận schema tương thích; không restore DB/bucket |

Nginx/TLS/domain do chủ hệ thống tự cấu hình. Bộ script không cài hoặc quản lý
PostgreSQL/RustFS, không có backup gate cho update thông thường.

Launcher dùng `exec` để PM2 gửi signal đến đúng process; đường dẫn Java/Node phải
còn hợp lệ khi reboot, không phụ thuộc `.bashrc` tương tác. Chốt graceful shutdown
Spring và PM2 `kill_timeout` đồng bộ. Dùng health probe riêng, không coi trạng thái
PM2 `online` là API đã migrate và sẵn sàng.

Quy trình phát hành:

1. Chọn commit phát hành đã bao gồm các thay đổi UI mong muốn và đã qua CI.
   Không triển khai trực tiếp working tree local còn thay đổi chưa chốt.
2. Build JAR bằng JDK 17/Gradle Wrapper. Build Web bằng Node 22 với `npm ci`,
   test/lint/build; dùng Linux cùng kiến trúc VPS cho phần native của Next.js.
   Không copy `node_modules` hoặc `.next` từ macOS lên Ubuntu/ARM.
3. Đưa artifact vào thư mục release mới, inject env ngoài artifact và giữ bản cũ.
   Nếu build ngay VPS, build ở thư mục mới và tính RAM của các service cùng host.
4. Chạy script first hoặc update phù hợp. Với update, bản cũ tiếp tục chạy trong
   lúc build; sau đó dừng Web/API, đổi `current`, restart riêng `knowledge-api`,
   đợi health/Flyway, rồi restart Web. Không backup bắt buộc; nếu có thay đổi
   schema/data đáng kể, chủ hệ thống quyết định snapshot/backup ở service riêng.
5. Chạy smoke test qua HTTPS. Chỉ lưu PM2 process list sau khi cấu hình đã ổn định.
6. Thiết lập/kiểm tra `pm2 startup` đúng user và `pm2 save`, log rotation, disk
   alert và khôi phục sau reboot. Không dùng `pm2 restart all` trên VPS dùng chung.

PM2 hỗ trợ app declaration, interpreter/process mode và phục hồi danh sách
process sau reboot: [ecosystem](https://pm2.keymetrics.io/docs/usage/application-declaration/),
[startup/save](https://pm2.keymetrics.io/docs/usage/startup/).

## 9. Nghiệm thu và rollback

Nghiệm thu trước khi coi là triển khai xong:

- API `/actuator/health` đạt qua loopback; Flyway validate/migrate thành công.
- Google login đúng owner; sai owner bị từ chối; logout và cookie Secure hoạt động.
- CRUD, autosave, collections/tags, FTS, Quick Search, Graph và revision restore.
- PRIVATE không truy cập ẩn danh; PUBLIC/UNLISTED và thu hồi/rotate link đúng.
- Upload ảnh, đọc lại sau restart, ảnh trong History/PUBLIC/UNLISTED qua RustFS.
- Cleanup được bật lại sau đối chiếu dữ liệu storage và quyền xóa đã được xác nhận.
- AI disabled state; nếu chủ động bật AI, kiểm tra chức năng/quota bằng số request
  có giới hạn, không tự chạy bộ live evaluation.
- PM2 tự phục hồi khi process lỗi; kiểm tra reboot trong maintenance window và
  xác nhận PostgreSQL/RustFS/Nginx cũng phục hồi theo cơ chế đang quản lý chúng.
- Có artifact của release trước; quyết định backup/restore riêng khi thay đổi
  schema/data cần phương án phục hồi, không coi đó là gate cho update thông thường.

Rollback ứng dụng: chuyển `current` về release trước, restart riêng hai app và
kiểm tra health/login. Kiểm tra tương thích schema trước khi rollback JAR: đổi
symlink không hoàn tác Flyway. Khôi phục DB/bucket là thao tác phục hồi riêng,
chỉ làm theo kế hoạch đã kiểm thử, có tạm dừng ghi và đánh giá dữ liệu sẽ mất.
Backup DB/bucket cần mốc nhất quán, đặc biệt khi có attachment cleanup.

## 10. Việc cần chốt trước khi viết cấu hình thực thi

1. Domain/subdomain và Nginx/TLS có sẵn hay chưa.
2. Ubuntu version, CPU/RAM, ARM64/x86_64 và tài nguyên còn trống.
3. PostgreSQL server major, native hay container, DB ứng dụng mới hay di chuyển dữ liệu cũ.
4. RustFS endpoint/region và bucket đang chứa ảnh hay bucket mới; không cần gửi secret qua chat.

Hiện chưa kết nối VPS, chưa cài package, đổi firewall, chạy migration hoặc restart
bất kỳ process production nào. Các thay đổi UI đang có trong working tree được giữ nguyên.
