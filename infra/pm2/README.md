# Triển khai Ubuntu + PM2 với PostgreSQL/RustFS hiện có

**Dùng scripts:** xem [SCRIPTS.md](SCRIPTS.md) cho lệnh setup, deploy lần đầu,
update main, restart/recovery và rollback. Đây là cách chạy được tự động hóa.
Phần bên dưới giải thích chi tiết từng thao tác thủ công.

Bộ file này chạy hai app trên loopback. Bạn tự cấu hình Nginx, HTTPS và domain;
hướng dẫn chỉ cung cấp thông tin upstream/callback cần nối ở bước cuối.
Chưa có thao tác nào được thực hiện trên VPS bởi việc bổ sung các file này.
Chạy các bước tuần tự và dừng xử lý nếu một lệnh báo lỗi trước khi sang bước tiếp.

## 1. Kiểm tra runtime và port

Chạy bằng user đang quản lý PM2, không tạo thêm PM2 daemon bằng root:

```bash
node --version
java -version
pm2 --version
uname -m
free -h
df -h
ss -ltn
```

Dùng Node 22 theo CI và JDK 17 cho Gradle build. Nếu thiếu JDK 17 trên Ubuntu,
cài `openjdk-17-jdk`; chọn đường dẫn Java phù hợp trong `api.env` nếu máy có nhiều
JDK. Dùng cơ chế quản lý Node hiện có để chọn Node 22. Kiểm tra lại PM2 startup
khi thay đường dẫn Node. Chọn port khác trong env nếu 3000/8080 đã được sử dụng.

PostgreSQL/RustFS được quản lý riêng. Không có bước backup bắt buộc trong update
thông thường; snapshot/backup khi thay đổi schema/data đáng kể theo quyết định
của chủ hệ thống. Không chạy Compose phát triển trong `infra/docker` để thay
PostgreSQL/RustFS đang có.

## 2. Cài pgvector vào đúng PostgreSQL server

Xác định version server bằng kết nối đúng host/port:

```sql
SELECT version();
SHOW server_version;
```

Với PostgreSQL native, ví dụ **server major 17**, kiểm tra package trước khi cài:

```bash
apt-cache policy postgresql-17-pgvector
sudo apt-get -s install postgresql-17-pgvector
sudo apt-get install postgresql-17-pgvector
```

Thay `17` bằng major thực tế. Nếu package không có, kiểm tra repository Ubuntu/PGDG
phù hợp với bản Ubuntu và kiến trúc hiện tại, hoặc build extension theo hướng dẫn
chính thức; không tự nâng PostgreSQL major. Repo kiểm thử với PostgreSQL 17,
nên major khác cần kiểm thử migration/query trên DB sao trước.

Nếu PostgreSQL chạy container, extension phải nằm **trong image container** cùng
major PostgreSQL. Cài package trên host không có tác dụng cho PostgreSQL container;
chuẩn bị image và kiểm thử với bản sao volume trước khi đổi image production.

Nguồn: [pgvector installation](https://github.com/pgvector/pgvector#installation).

## 3. Chuẩn bị database và RustFS

Nếu tạo DB mới, quản trị viên PostgreSQL có thể dùng hai role riêng. Ví dụ dưới
đây chạy trong `psql`, đặt mật khẩu bằng prompt thay vì đưa mật khẩu vào history:

```sql
CREATE ROLE knowledge_migrator LOGIN;
\password knowledge_migrator
CREATE ROLE knowledge_app LOGIN;
\password knowledge_app
CREATE DATABASE knowledge_app OWNER knowledge_migrator;
\connect knowledge_app

CREATE EXTENSION IF NOT EXISTS vector;
SELECT extversion FROM pg_extension WHERE extname = 'vector';
SELECT '[1,2,3]'::vector;

GRANT CONNECT ON DATABASE knowledge_app TO knowledge_app;
GRANT USAGE ON SCHEMA public TO knowledge_app;
ALTER DEFAULT PRIVILEGES FOR ROLE knowledge_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO knowledge_app;
ALTER DEFAULT PRIVILEGES FOR ROLE knowledge_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO knowledge_app;
```

Nếu dùng DB/schema đã có, bỏ các câu tạo trùng; cấp quyền trên các bảng/sequence
hiện có sau restore, bên cạnh default privileges cho bảng mới. Runtime role không
cần superuser. Giữ `APP_OWNER_ID` của dữ liệu cũ.

RustFS:

- Chuẩn bị bucket private, ví dụ `knowledge-images`.
- Chuẩn bị credential của ứng dụng với quyền đọc/ghi/xóa object trong bucket.
- Ghi lại **S3 endpoint**, region, access key và secret key; không dùng console URL.
- Nếu chuyển ảnh từ MinIO, sao chép nguyên object key và giữ metadata DB trước
  khi bật cleanup. Ứng dụng không tự tạo bucket hoặc tự di chuyển ảnh.

## 4. Chuẩn bị một release mới trên VPS

Các lệnh dưới đây giả định thư mục `/home/ubuntu/knowledge-application`. Nếu dùng đường dẫn khác,
đặt lại `KNOWLEDGE_DEPLOY_ROOT` trong mỗi phiên thao tác deploy.

Đây là thư mục gốc triển khai, chứa `releases/`, symlink `current` và `shared/`.
Source được checkout vào từng thư mục release bên dưới, không clone trực tiếp
vào root này. Nếu root đang có source/dữ liệu, giữ nguyên và kiểm tra bố cục trước
khi chạy các bước tạo release; không di chuyển hoặc ghi đè tự động.

```bash
export KNOWLEDGE_DEPLOY_ROOT=/home/ubuntu/knowledge-application
sudo install -d -o "$(id -un)" -g "$(id -gn)" -m 750 "$KNOWLEDGE_DEPLOY_ROOT"
mkdir -p "$KNOWLEDGE_DEPLOY_ROOT/releases" "$KNOWLEDGE_DEPLOY_ROOT/shared/logs"
chmod 700 "$KNOWLEDGE_DEPLOY_ROOT/shared"

release_dir="$KNOWLEDGE_DEPLOY_ROOT/releases/release-$(date +%Y%m%d-%H%M%S)"
git clone --branch main --single-branch 'https://github.com/phanducquang/knowledge-application.git' "$release_dir"
cd "$release_dir"
git checkout --detach HEAD
git rev-parse HEAD
```

Lệnh lấy `main` mới nhất tại thời điểm clone, rồi cố định HEAD vào commit đó.
Nếu repo private, cấu hình quyền Git cho user deploy hoặc dùng SSH URL tương ứng.
Commit này phải chứa bộ file PM2 và các thay đổi ứng dụng mong muốn, đã qua CI.
Các sửa đổi local chưa commit/push sẽ không được lấy lên VPS. Release ID mới được
tạo mỗi lần; không build đè lên bản đang phục vụ.

Tạo file cấu hình chỉ ở lần đầu; các lệnh dưới giữ nguyên file đã tồn tại:

```bash
test -e "$KNOWLEDGE_DEPLOY_ROOT/shared/api.env" || (
  umask 077
  cp infra/pm2/api.env.example "$KNOWLEDGE_DEPLOY_ROOT/shared/api.env"
)
test -e "$KNOWLEDGE_DEPLOY_ROOT/shared/web.env" || (
  umask 077
  cp infra/pm2/web.env.example "$KNOWLEDGE_DEPLOY_ROOT/shared/web.env"
)
chmod 600 "$KNOWLEDGE_DEPLOY_ROOT/shared/api.env" "$KNOWLEDGE_DEPLOY_ROOT/shared/web.env"
```

Sửa hai file bằng editor trên VPS, thay toàn bộ `CHANGE_ME_*`, email và domain.
Đây là file assignment tương thích Bash, được launcher `source` như cấu hình
tin cậy. Quote/escape giá trị theo Bash; không paste một file shell không tin cậy.
Không source secret vào shell dùng để gọi PM2; launcher sẽ tự nạp cho process con.

`api.env` cần DB/runtime role và Flyway role, Google OAuth, owner UUID, RustFS;
`web.env` cần URL API nội bộ và domain ngoài browser. Heap Java/Node chỉ đặt sau
khi tính RAM còn trống của máy cùng PostgreSQL/RustFS.

## 5. Build production trên Linux

Từ thư mục release:

```bash
cd "$release_dir/apps/api"
./gradlew --no-daemon bootJar
mkdir -p "$release_dir/runtime"
cp build/libs/knowledge-api-0.0.1-SNAPSHOT.jar "$release_dir/runtime/knowledge-api.jar"

cd "$release_dir/apps/web"
npm ci
npm test
npm run lint
npm run build
```

Tên JAR nguồn ở trên khớp version hiện tại; kiểm tra `build/libs` nếu version
ứng dụng thay đổi. Launcher dùng tên đích ổn định `runtime/knowledge-api.jar`.
`bootJar` đóng gói ứng dụng; full API tests phải qua CI trước đó, hoặc chạy
`./gradlew test` trong môi trường có Docker/Testcontainers phù hợp.

Không copy `node_modules`/`.next` từ macOS lên Ubuntu. Native dependencies phải
khớp OS/kiến trúc VPS. Build Web hiện dùng `next/font/google`, nên máy build cần
kết nối HTTPS tải font. Nếu máy đang thiếu RAM do các service cùng host, build artifact
trên Linux cùng kiến trúc rồi chuyển release đã build lên VPS.

## 6. Trỏ current và khởi động API trước

Sau build thành công, bảo đảm `current` chưa tồn tại hoặc là symlink:

```bash
test ! -e "$KNOWLEDGE_DEPLOY_ROOT/current" || test -L "$KNOWLEDGE_DEPLOY_ROOT/current"
ln -s "$release_dir" "$KNOWLEDGE_DEPLOY_ROOT/current.next" &&
  mv -Tf "$KNOWLEDGE_DEPLOY_ROOT/current.next" "$KNOWLEDGE_DEPLOY_ROOT/current"

pm2 start "$KNOWLEDGE_DEPLOY_ROOT/current/infra/pm2/ecosystem.config.js" --only knowledge-api
pm2 logs knowledge-api --lines 100 --nostream
curl -fsS http://127.0.0.1:8080/actuator/health
```

Không tiếp tục nếu lệnh kiểm tra `current` thất bại. Chỉ chuyển symlink trong cửa
sổ triển khai đã chọn. Các lệnh từng bước không phải script tự động có rollback.
Đợi API trả `{"status":"UP"}`, kiểm tra log Flyway không có lỗi. Nếu chưa sẵn
sàng, đợi API startup rồi kiểm tra lại; xử lý DB/permission/config nếu vẫn lỗi.
Đổi port health check nếu cấu hình API khác 8080.

Sau khi API khỏe:

```bash
pm2 start "$KNOWLEDGE_DEPLOY_ROOT/current/infra/pm2/ecosystem.config.js" --only knowledge-web
pm2 logs knowledge-web --lines 100 --nostream
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3000/login
pm2 status
```

Web `/login` dự kiến 200 khi chưa đăng nhập. PM2 `online` chỉ là process sống,
không thay thế health và smoke test. Health API hiện không chứng minh RustFS/OAuth
hoạt động; kiểm tra hai phần đó qua UI ở bước kế tiếp.

## 7. Thông tin để bạn nối Nginx/HTTPS/domain

Không có cấu hình Nginx hoặc certificate được áp dụng bởi bộ file này.

| URL | Upstream |
| --- | --- |
| `/oauth2/…`, `/login/oauth2/…` | `127.0.0.1:8080`, giữ nguyên URI |
| `/login`, `/api/*`, các URL ứng dụng còn lại | `127.0.0.1:3000` |

Đặt domain thật trong `WEB_BASE_URL` và `KNOWLEDGE_API_BROWSER_BASE_URL`, rồi đăng ký
Google OAuth redirect URI `https://<domain>/login/oauth2/code/google`.
Giữ Host/forwarded HTTPS đúng, body limit có overhead multipart và timeout đủ cho
AI nếu bật. Chi tiết tham chiếu ở [kế hoạch triển khai](../../docs/DEPLOYMENT_ORACLE_PM2.md).
Cookie Secure trong mẫu đòi hỏi HTTPS cho đăng nhập thực tế; kiểm tra local health
qua HTTP không thay thế kiểm thử login qua domain.

Không mở port 3000/8080 ra internet. Xác nhận loopback ở `ss -ltn`.

## 8. Nghiệm thu và lưu PM2

Sau khi bạn hoàn tất phần domain/HTTPS:

1. Google login đúng owner, logout, sai owner bị từ chối.
2. Tạo/sửa/autosave, collections/tags, tìm kiếm và Graph.
3. Upload ảnh vào RustFS, đọc lại sau refresh/restart; kiểm tra History và quyền
   Public/Unlisted/Private bằng cửa sổ ẩn danh.
4. Nếu nhập dữ liệu cũ, đối chiếu ảnh và revision trước khi bật cleanup.
5. Đổi `KNOWLEDGE_ATTACHMENT_CLEANUP_ENABLED=true` trong `shared/api.env`, rồi
   `pm2 restart knowledge-api`. Restart API làm mất session, cần đăng nhập lại.
6. AI đang tắt trong mẫu; chỉ cấu hình key/flags/model/quota khi muốn bật tính
   năng hiện có. Không tự chạy các task live evaluation khi deploy.

Thiết lập tự phục hồi bằng đúng user PM2:

```bash
pm2 save
pm2 startup
```

Chạy lệnh sudo **do PM2 in ra** để cài startup theo đúng user và Node path, nếu
chưa có startup service phù hợp. Với VPS đã có PM2 startup, kiểm tra service hiện
tại trước khi tạo lại. Cấu hình log rotation cho bốn file log riêng, backup định
kỳ và kiểm tra reboot trong maintenance window.

Nguồn PM2: [ecosystem](https://pm2.keymetrics.io/docs/usage/application-declaration/),
[startup/save](https://pm2.keymetrics.io/docs/usage/startup/).

## 9. Cập nhật phiên bản và rollback

### 9.1. Lấy main mới và build khi bản cũ vẫn chạy

```bash
export KNOWLEDGE_DEPLOY_ROOT=/home/ubuntu/knowledge-application
previous_release="$(readlink -f "$KNOWLEDGE_DEPLOY_ROOT/current")"
printf 'Current release: %s\n' "$previous_release"

release_dir="$KNOWLEDGE_DEPLOY_ROOT/releases/release-$(date +%Y%m%d-%H%M%S)"
git clone --branch main --single-branch 'https://github.com/phanducquang/knowledge-application.git' "$release_dir"
cd "$release_dir"
git checkout --detach HEAD
git rev-parse HEAD

cd "$release_dir/apps/api"
./gradlew --no-daemon bootJar
mkdir -p "$release_dir/runtime"
cp build/libs/knowledge-api-0.0.1-SNAPSHOT.jar "$release_dir/runtime/knowledge-api.jar"

cd "$release_dir/apps/web"
npm ci
npm test
npm run lint
npm run build
```

Dừng nếu clone/test/build lỗi; `current` chưa đổi nên bản cũ vẫn chạy. Cần build
cả Web và API vì hai launcher dùng chung `current`. Giữ nguyên `shared/*.env`,
chỉ sửa khi cấu hình release mới thực sự thay đổi; không copy đè bằng mẫu.

### 9.2. Dừng ghi trước migration; backup theo chính sách service

Chọn maintenance window; bước này có downtime. Sau build thành công và kiểm tra
tương thích migration, dừng hai process riêng của ứng dụng:

```bash
pm2 stop knowledge-web
pm2 stop knowledge-api

printf '%s\n' "$previous_release" > "$KNOWLEDGE_DEPLOY_ROOT/shared/previous-release"
mkdir -p "$KNOWLEDGE_DEPLOY_ROOT/shared/backups"
chmod 700 "$KNOWLEDGE_DEPLOY_ROOT/shared/backups"
```

Với PostgreSQL/RustFS ở service khác, script update không tự backup hoặc bắt buộc
backup mỗi lần cập nhật thông thường. Nếu release có migration đáng kể, chủ động
chọn backup/snapshot ở các service đó trước cutover. Các lệnh dưới chỉ là ví dụ
backup tùy chọn nếu PostgreSQL native, DB/port/role
đúng như mẫu và `pg_dump` tương thích server:

```bash
(
  umask 077
  pg_dump -h 127.0.0.1 -p 5432 -U knowledge_migrator -W \
    -d knowledge_app -Fc \
    -f "$KNOWLEDGE_DEPLOY_ROOT/shared/backups/knowledge-$(date +%Y%m%d-%H%M%S).dump"
)
```

Đổi thông số theo DB thật. PostgreSQL container dùng lệnh backup bên trong
container hoặc client tương thích. Nếu thực hiện backup DB/bucket, dùng công cụ
đã chọn và giữ bản sao ngoài VPS; đợi backup xong trước cutover. Hai app đang dừng
để DB/bucket không bị app và cleanup thay đổi trong cửa sổ này.
Nếu backup thất bại trước khi đổi `current`, khởi động lại API cũ, đợi health UP,
rồi Web cũ; chưa có migration release mới ở giai đoạn này.

### 9.3. Chuyển release và khởi động API

```bash
test -L "$KNOWLEDGE_DEPLOY_ROOT/current"
ln -s "$release_dir" "$KNOWLEDGE_DEPLOY_ROOT/current.next" &&
  mv -Tf "$KNOWLEDGE_DEPLOY_ROOT/current.next" "$KNOWLEDGE_DEPLOY_ROOT/current"

config="$KNOWLEDGE_DEPLOY_ROOT/current/infra/pm2/ecosystem.config.js"
pm2 restart "$config" --only knowledge-api --update-env
pm2 logs knowledge-api --lines 100 --nostream
curl -fsS http://127.0.0.1:8080/actuator/health
```

Dừng nếu kiểm tra symlink thất bại; không ghi đè thư mục `current` thật. Đợi API
trả `{"status":"UP"}` và log Flyway không lỗi; chạy lại health check nếu API còn
đang startup. Nếu API lỗi, giữ Web dừng và xử lý trước khi sang bước kế tiếp.

### 9.4. Khởi động Web và nghiệm thu

Chỉ sau khi API đã UP:

```bash
pm2 restart "$config" --only knowledge-web --update-env
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3000/login
pm2 status
```

Kiểm tra qua domain: login/logout, tạo/sửa/autosave, tìm kiếm và ảnh. Khi đạt:

```bash
pm2 save
```

Env được launcher đọc mỗi lần start/restart. Bạn không chạy trực tiếp
`start-api.sh`/`start-web.sh` để tải code hoặc cập nhật process; PM2 gọi chúng khi
restart. Không dùng `pm2 restart all` trên máy có ứng dụng khác.

### 9.5. Rollback

Rollback app: trỏ `current` về release trước, restart riêng API/Web và kiểm tra.
Rollback JAR không hoàn tác Flyway; chỉ dùng bản cũ khi schema còn tương thích.
Nếu phải restore DB/bucket, dừng ghi và làm theo backup/restore đã kiểm thử,
không tự động restore đè dữ liệu mới. Không có cam kết zero downtime: API session
đang nằm trong memory và mỗi app hiện chạy một instance.
