# Deploy bằng shell scripts

Root mặc định: `/home/ubuntu/knowledge-application`. Nhánh deploy: `main` mới nhất
tại thời điểm clone, sau đó cố định commit của release. Nginx/HTTPS/domain do bạn
tự quản lý. PostgreSQL và RustFS là service bên ngoài, không được các script cài,
restart, backup hoặc restore.

## Các file sử dụng

| Script | Chức năng |
| --- | --- |
| `setup.sh` | Tạo `releases/`, `shared/logs/`, mẫu env; giữ nguyên env đã có |
| `deploy-first.sh` | Clone main, build API/Web, tạo current, PM2 start API → health → Web → HTTP check → save |
| `deploy-update.sh` | Build release mới khi bản cũ chạy, báo migration thay đổi, dừng hai app, chuyển current, restart API → health → Web → save |
| `restart-current.sh` | Không tải code/build; restart hoặc hoàn tất lần start lỗi với release đang được chọn, sau khi bạn sửa env/lỗi hạ tầng |
| `rollback.sh` | Đổi về release đã build trước đó, chỉ khi bạn xác nhận schema hiện tại còn tương thích |
| `deploy-common.sh` | Thư viện nội bộ của các script trên |
| `start-api.sh`, `start-web.sh` | Launcher do PM2 gọi, không phải script cập nhật source |

## 1. Chuẩn bị một lần

Commit/push bộ file này lên `main` trước khi dùng. Trên VPS, SSH bằng user `ubuntu`
đang quản lý PM2. Cần Node 22, npm, JDK 17, git, curl và PM2; PostgreSQL đã có
pgvector/database/role; RustFS đã có bucket private và credential.

Tải một bản bootstrap **ngoài deployment root** để có script chạy lần đầu:

```bash
git clone --branch main --single-branch \
  https://github.com/phanducquang/knowledge-application.git \
  /home/ubuntu/knowledge-deploy-tools

cd /home/ubuntu/knowledge-deploy-tools
bash infra/pm2/setup.sh
```

Nếu đã có checkout chứa bộ script mới nhất, chạy `setup.sh` từ checkout đó,
không cần clone lại bootstrap. Repo private cần quyền Git cho user deploy.

Điền cấu hình thật:

```bash
nano /home/ubuntu/knowledge-application/shared/api.env
nano /home/ubuntu/knowledge-application/shared/web.env
chmod 600 /home/ubuntu/knowledge-application/shared/{api,web}.env
```

Trong `api.env`, dùng **host/port PostgreSQL và endpoint S3 RustFS thực tế**;
không giữ `127.0.0.1` nếu hai service nằm ở máy khác. Điền Google OAuth, owner,
domain và credentials; giữ `APP_OWNER_ID` khi nhập dữ liệu cũ. Trong `web.env`,
API URL là loopback tới Java trên VPS, browser URL là domain HTTPS của bạn.

Script từ chối mẫu `CHANGE_ME_*`, domain/email mẫu, env bị mở quyền đọc cho
group/others, port app không hợp lệ, internal API URL sai port và public Web
origins không khớp. Các biến từ shared env được nạp trong subprocess/launcher,
không source chung vào shell build hoặc chép vào ecosystem.
File env là Bash tin cậy; quote/escape theo Bash, không dùng file của nguồn lạ.

## 2. Triển khai lần đầu

Từ checkout bootstrap ở trên:

```bash
bash infra/pm2/deploy-first.sh
```

Script tự clone main vào một release mới, chạy `bootJar`, chọn đúng một executable
JAR (không phụ thuộc version filename), `npm ci --include=dev`, Web test/lint/build.
Nó kiểm tra artifact cả API/Web trước khi đổi current. Full API tests vẫn cần qua
CI của commit phát hành; script không chạy Testcontainers hoặc live AI evaluation.

Nếu app đã có current/process PM2, script first sẽ từ chối; dùng update hoặc
restart-current phù hợp. Các file PM2 cần có trên main, không chỉ trong bootstrap.

Sau health thành công, kiểm tra login/logout, ghi chú, tìm kiếm và upload/read
ảnh qua domain HTTPS. Sau khi ảnh/dữ liệu nhập được xác nhận, bật
`KNOWLEDGE_ATTACHMENT_CLEANUP_ENABLED=true` trong api.env nếu đang tắt ở mẫu:

```bash
bash /home/ubuntu/knowledge-application/current/infra/pm2/restart-current.sh
```

PM2 process list được script save sau API health và Web HTTP check. Nếu PM2 chưa
có startup phù hợp, chạy `pm2 startup` bằng user hiện tại và làm theo lệnh sudo
nó in ra. Restart API làm mất session đăng nhập hiện đang lưu trong memory.

## 3. Mỗi lần main cập nhật

```bash
bash /home/ubuntu/knowledge-application/current/infra/pm2/deploy-update.sh
```

Mọi lần update đều build cả API/Web vì dùng chung current. `shared/*.env` được
giữ nguyên. Có downtime ngắn sau build, khi hai app được dừng để chuyển release.
Script chỉ thao tác `knowledge-api` và `knowledge-web` thuộc đúng root; không
restart các ứng dụng PM2 khác hoặc các service PostgreSQL/RustFS.

Theo lựa chọn vận hành của bạn, **không có bước backup bắt buộc/confirmation
trong update**. Script báo tên file Flyway migration khác so với commit cũ.
Database dù ở service khác vẫn có thể được Flyway thay đổi khi API mới khởi động;
rollback app không hoàn tác migration. Với thay đổi schema/data đáng kể, bạn tự
chọn snapshot/backup ở service PostgreSQL/RustFS trước maintenance window.

Không chạy cùng lúc nhiều deploy. Lock được giữ trong `shared/.deploy-lock` và
được dọn khi script thoát bình thường/lỗi/signal. Nếu bị kill -9 hoặc máy mất điện,
kiểm tra PID/tiến trình trước khi tự dọn stale lock; script không tự đoán và xóa.

## 4. Khi gặp lỗi

- Clone/build/test/lint/preflight lỗi: current và app cũ chưa bị thay đổi.
- Nếu lỗi sau khi đã dừng app nhưng trước cutover, script cố gắng chạy lại bản cũ.
- Sau cutover, nếu API/Web health lỗi: script dừng hai app, giữ release và log;
  không tự quay về JAR cũ vì migration có thể đã chạy.
- `shared/deploy-state` ghi stage/release/previous; `shared/previous-release`
  ghi đường dẫn trước update. Đọc `pm2 logs knowledge-api`/`knowledge-web` để xử lý.

Sau khi sửa env, DB/storage kết nối hoặc lỗi hạ tầng mà vẫn muốn chạy release
đang chọn:

```bash
bash /home/ubuntu/knowledge-application/current/infra/pm2/restart-current.sh
```

Lệnh này không tải main, không build và không chọn release khác. Nó hỗ trợ cả
trường hợp first deploy đã tạo API process nhưng Web chưa được tạo do API lỗi.

## 5. Rollback app

Chỉ chạy sau khi kiểm tra bản cũ tương thích **schema hiện tại**:

```bash
bash /home/ubuntu/knowledge-application/current/infra/pm2/rollback.sh --schema-compatible
```

Mặc định dùng `shared/previous-release`. Có thể chỉ định release cụ thể bên dưới
`releases/` bằng tham số thứ hai. Script không restore DB/bucket hay chạy Flyway
repair; cần xử lý riêng nếu schema/migration đang lỗi.

## Tùy chỉnh

```bash
KNOWLEDGE_DEPLOY_ROOT=/another/root \
KNOWLEDGE_REPOSITORY_URL=git@github.com:phanducquang/knowledge-application.git \
bash infra/pm2/deploy-first.sh
```

Root được truyền vào PM2. Repository mặc định là HTTPS project origin, luôn lấy
main. Node binary PM2 dùng theo Node hiện tại; kiểm tra startup khi đổi Node path.
Health đọc port/loopback từ env; mặc định thử tối đa 60 lần, nghỉ 2 giây giữa lần,
mỗi curl có timeout 5 giây. Có thể điều chỉnh `KNOWLEDGE_HEALTH_ATTEMPTS` và
`KNOWLEDGE_HEALTH_INTERVAL`. Health không xác minh Google OAuth hoặc RustFS ảnh;
bạn vẫn cần smoke test chức năng thực tế.

Không xóa release cũ tự động. Có thể lưu nhiều release để rollback; dọn có chọn
lọc sau khi xác nhận không phải current hoặc previous-release và đã nghiệm thu.

## Kiểm tra bộ script

```bash
python3 infra/pm2/tests/deploy_test.py
```

Bộ kiểm tra offline dùng Bash/Git/Node thật và giả lập PM2/build/HTTP để kiểm tra
deploy, update, lỗi build/health, restart, rollback và bảo vệ cấu hình/process.
Không kết nối VPS, PostgreSQL, RustFS, Google OAuth hoặc Gemini. Cần Node 22 để
khớp preflight của script; kiểm tra này không thay thế smoke test trên VPS.
