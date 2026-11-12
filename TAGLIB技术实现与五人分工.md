# TAGLIB 课件资料多维标签检索网站 —— 技术实现方案与五人分工

本文档依据《课件资料多维度智能标签检索网站系统需求分析》（TAGLIB-SRS-0001 v2.0）整理，将需求文档中的 24 条 SRS 条目落实为具体的类、接口、数据表和前端页面，并按五人划分开发任务。编号与 SRS 一一对应，作为开发过程中的索引。五名开发人员在本文档中以 A/B/C/D/E 代称。

---

## 一、技术栈与版本

| 层 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 前端框架 | Vue 3 + Vite | Vue 3.4+ | 使用 `<script setup>` 组合式 API |
| UI 组件 | Element Plus | 最新稳定版 | 表格、分页、上传、标签、弹窗等通用组件直接复用 |
| 前端 HTTP | Axios | 1.x | 统一封装 `request.js`，请求携带 Cookie（withCredentials）；CORS 仅开发期 Vite 5173 跨调试时需要，生产同域由 Spring Boot 托管静态文件后不生效 |
| 后端框架 | Spring Boot | 2.7.x（JDK 21） | 与 pom.xml 一致；Spring Framework 5.3.31 支持 JDK 21，避免 Spring Boot 3 的 Jakarta 包名迁移 |
| ORM | MyBatis-Plus | 3.5.x | 单表 CRUD 使用 MP，多标签交集查询手写 XML |
| 数据库 | MySQL | 8.0 | utf8mb4；数据库与服务端统一以 UTC 存储，展示时转 Asia/Shanghai（见下方“时间与时区约定”） |
| 连接池 | HikariCP | Spring Boot 自带 | 使用默认配置 |
| 密码哈希 | BCrypt（spring-security-crypto） | 5.x | 仅引入 `spring-security-crypto`，不引入完整 Spring Security |
| 文件存储 | 本地磁盘目录 | - | 存放于 `./data/upload/`，路径配置在 application.yml |
| 定时任务 | Spring `@Scheduled` | - | 清理临时文件、每日统计聚合 |
| 图表 | ECharts 5 | - | 管理员统计页使用 |

**部署形态**：单机部署，操作系统 Windows 10/11 或 Ubuntu 20.04 LTS 均可（与 SRS 2.3.2 一致），本机运行 MySQL、Spring Boot（内置 Tomcat，端口 8080）以及 Vite 构建后的静态文件（直接放入 Spring Boot 的 `resources/static`，不单独部署 Nginx）。局域网访问 `http://主机IP:8080`。

**时间与时区约定（SRS 4.1、5.1、8.3.2 I-06）**：服务端与数据库一律以 **UTC 存储**，界面展示时转换为北京时间（Asia/Shanghai）。
- **JVM**：启动参数固定 `-Duser.timezone=UTC`；不允许依赖操作系统默认时区。实体时间字段统一使用 `Instant`（UTC），Jackson 默认序列化为带 `Z` 的 ISO 8601 字符串；本期不使用 `LocalDateTime`，也不依赖 `spring.jackson.time-zone`（该配置对 `LocalDateTime` 不生效）。
- **JDBC / MySQL**：连接串使用 `serverTimezone=UTC`；Hikari 设置 `connection-init-sql: "SET time_zone = '+00:00'"`，使 `NOW()`、`CURRENT_TIMESTAMP` 也返回 UTC。
- **接口**：所有时间字段统一输出 ISO 8601 UTC 字符串（`2026-11-12T08:00:00Z`），精确到秒；前端用 `new Date(iso)` 按浏览器本地时区展示，**禁止在前端手工 ±8 小时**。实体/DTO 的时间字段**统一使用 `Instant`**（不使用 `LocalDateTime` / `OffsetDateTime`）。`resultType=Map` 的查询统一转 DTO，或在 SQL 中用 `DATE_FORMAT(..., '%Y-%m-%dT%H:%i:%sZ')`，避免 `Timestamp` 被序列化成数字或本地时间。
- **自动填充**：MyBatis-Plus 的 `created_at` / `updated_at` 由元对象填充器以 `Instant.now()`（UTC）写入；MySQL 列的 `DEFAULT CURRENT_TIMESTAMP` 在会话时区为 `+00:00` 时同样写入 UTC。
- **业务日期**：`daily_statistics.stat_date` 按**北京时间自然日**聚合：统计 SQL 用 `DATE(CONVERT_TZ(created_at, '+00:00', '+08:00'))` 分组，表内仍存 `DATE`，前端标注“北京时间”。
- Windows 与 Ubuntu 部署都必须显式设置 JVM 时区；如果更换操作系统而漏配时区，所有时间会整体偏移 8 小时。

**静态资源与 SPA 路由**：Vue 使用 history 模式时，Spring Boot 需把非 `/api`、且未命中静态文件的路径 forward 到 `index.html`（如 `WebMvcConfigurer#addViewControllers` 配置 `/**` 转发，或自定义 `ErrorController` 兜底），否则刷新 `/resource/1` 会 404；若不想改后端，前端可改用 hash 模式。

**开发期 CORS**：默认只放行本机 Vite（`localhost` / `127.0.0.1`）。若前端在另一台机器上运行 `vite dev` 联调，需把该主机来源加入 `allowedOriginPatterns`，建议做成 `taglib.cors.allowed-origins` 配置项，不要使用通配符。

---

## 二、项目目录结构

```
taglib/
├── backend/                          # SpringBoot 工程
│   ├── src/main/java/com/taglib/
│   │   ├── TaglibApplication.java
│   │   ├── common/                   # 【A】公共基础
│   │   │   ├── result/Result.java           # 统一响应体
│   │   │   ├── exception/GlobalExceptionHandler.java
│   │   │   ├── interceptor/LoginInterceptor.java
│   │   │   ├── config/WebConfig.java         # 注册拦截器、CORS
│   │   │   └── utils/PasswordUtil.java       # BCrypt
│   │   ├── user/                     # 【A】用户域
│   │   │   ├── controller/UserController.java
│   │   │   ├── service/UserService.java
│   │   │   ├── mapper/UserMapper.java
│   │   │   └── entity/User.java
│   │   ├── resource/                 # 【B】资源文件域
│   │   │   ├── controller/ResourceController.java
│   │   │   ├── controller/FileController.java     # 上传；文件读取方法供 D 的下载接口调用
│   │   │   ├── service/ResourceService.java
│   │   │   ├── service/FileStorageService.java
│   │   │   ├── mapper/ResourceMapper.java
│   │   │   ├── mapper/FileObjectMapper.java
│   │   │   └── entity/{Resource,FileObject}.java
│   │   ├── tag/                      # 【C】标签域
│   │   │   ├── controller/TagController.java
│   │   │   ├── service/TagService.java
│   │   │   ├── mapper/{TagMapper,ResourceTagMapper}.java
│   │   │   └── entity/{Tag,ResourceTag}.java
│   │   ├── search/                   # 【D】检索与互动
│   │   │   ├── controller/SearchController.java
│   │   │   ├── controller/InteractionController.java  # 点赞/收藏/下载
│   │   │   ├── service/SearchService.java
│   │   │   ├── service/InteractionService.java
│   │   │   ├── mapper/{FavoriteMapper,RatingMapper,DownloadRecordMapper}.java
│   │   │   └── mapper/xml/SearchMapper.xml          # 多标签交集 SQL
│   │   └── admin/                    # 【E】管理员后台
│   │       ├── controller/AdminController.java
│   │       ├── service/AdminService.java
│   │       ├── service/StatisticsService.java
│   │       ├── task/ScheduledTasks.java
│   │       ├── mapper/{AuditLogMapper,DailyStatisticsMapper}.java
│   │       └── entity/{AuditLog,DailyStatistics}.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── mapper/xml/*.xml
│   │   └── static/                   # 前端构建产物目录
│   └── pom.xml
├── frontend/                         # Vue 工程
│   ├── src/
│   │   ├── api/                     # axios 接口定义，按模块分文件
│   │   ├── components/              # 【E】公共组件（空状态、分页控件等）
│   │   │   └── TagPicker.vue        # 【C】标签选择器（按维度分组折叠，已选标签块）
│   │   ├── views/
│   │   │   ├── Login.vue  Register.vue              # 【A】
│   │   │   ├── Profile.vue                         # 【A】个人中心
│   │   │   ├── Upload.vue  MyResources.vue         # 【B】
│   │   │   ├── Home.vue  SearchResult.vue         # 【D】首页+检索
│   │   │   ├── ResourceDetail.vue                  # 【D】详情+互动
│   │   │   └── admin/AdminLayout.vue  Audit.vue  TagAdmin.vue  Stats.vue   # 【E】
│   │   ├── router/index.js
│   │   └── App.vue  main.js
└── db/
    └── schema.sql                   # 建表语句，全员共用
```

---

## 三、数据库表设计（10 张表，全员共用 schema.sql）

字段类型按 MySQL 8.0 编写。时间字段一律按上节约定存 **UTC**。`user`、`resource`、`tag` 等可变更表同时包含 `created_at` 与 `updated_at`；`file_object`、`favorite`、`download_record`、`audit_log` 等只追加或不可变表仅保留 `created_at`；`rating` 保留 `updated_at` 以记录评价最近变更；`daily_statistics` 按 `stat_date` 聚合。`created_at` / `updated_at` 由 MyBatis-Plus 以 `Instant.now()`（UTC）自动填充，实体对应字段类型为 `Instant`。主键使用 `BIGINT AUTO_INCREMENT`，对外接口以字符串形式传递，避免 JS 大整数精度丢失。**本期不建外键**（课程演示规模，引用完整性由 Service 层与唯一索引保证），这是相对 SRS 4.2.1 的显式裁剪，后续如需外键可在不改接口的前提下补加。

### 1. user（用户表）—— 【A】
```sql
CREATE TABLE user (
  user_id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  username       VARCHAR(32) NOT NULL UNIQUE,         -- 4~32位，字母数字下划线
  password_hash  VARCHAR(100) NOT NULL,               -- BCrypt，不存储明文密码
  nickname       VARCHAR(30) NOT NULL,
  role           VARCHAR(16) NOT NULL DEFAULT 'USER', -- USER / ADMIN
  status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',-- ACTIVE/TEMP_LOCKED/DISABLED
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until   DATETIME NULL,
  created_at     DATETIME,
  updated_at     DATETIME
);
```

### 2. resource（资源元数据表）—— 【B】
```sql
CREATE TABLE resource (
  resource_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id        BIGINT NOT NULL,                  -- 由服务端从登录态获取，不依赖前端传值
  title           VARCHAR(120) NOT NULL,
  description     VARCHAR(2000) DEFAULT '',
  material_type   VARCHAR(20) NOT NULL,             -- COURSEWARE/EXAM/EXERCISE/NOTE
  visibility      VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
                    -- UPLOADING/PROCESSING/ACTIVE/FAILED/DELETED/BLOCKED
  download_count  INT NOT NULL DEFAULT 0,
  like_count      INT NOT NULL DEFAULT 0,
  dislike_count   INT NOT NULL DEFAULT 0,
  created_at     DATETIME,
  updated_at     DATETIME,
  deleted_at      DATETIME NULL,                    -- 逻辑删除标记
  INDEX idx_owner (owner_id),
  INDEX idx_visibility_status (visibility, status, created_at)
);
```
> 资源与文件的关系**只以 `file_object.resource_id` 为权威方向**，`resource` 表不冗余保存 `file_id`。取当前文件时查询 `file_object WHERE resource_id=? AND storage_status='AVAILABLE' ORDER BY created_at DESC LIMIT 1`；历史文件对象保留，便于版本核对与补偿。

### 3. file_object（文件对象表）—— 【B】
```sql
CREATE TABLE file_object (
  file_id       VARCHAR(64) PRIMARY KEY,            -- UUID
  resource_id   BIGINT NOT NULL,                    -- 权威关联：文件属于哪个资源（resource 表不存 file_id）
  original_name VARCHAR(255) NOT NULL,
  storage_key   VARCHAR(255) NOT NULL,             -- 磁盘真实文件名，不暴露给前端
  extension     VARCHAR(16) NOT NULL,
  mime_type     VARCHAR(100),
  size_bytes    BIGINT NOT NULL,
  checksum      CHAR(64),                           -- SHA-256
  scan_status   VARCHAR(20) DEFAULT 'PASSED',      -- 本期不接入病毒扫描，默认 PASSED
  storage_status VARCHAR(20) DEFAULT 'AVAILABLE',
  created_at    DATETIME,
  INDEX idx_resource (resource_id)
);
```

### 4. tag（标签表）—— 【C】
```sql
CREATE TABLE tag (
  tag_id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag_name    VARCHAR(50) NOT NULL,
  dimension   VARCHAR(20) NOT NULL DEFAULT 'OTHER',  -- COURSE/EXAM/MATERIAL/YEAR/OTHER
  scope       VARCHAR(20) NOT NULL DEFAULT 'PRIVATE', -- PUBLIC/PRIVATE
  creator_id  BIGINT NOT NULL DEFAULT 0,            -- PUBLIC 固定 0；PRIVATE 必填创建者 user_id
  status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',-- ACTIVE/DISABLED/MERGED
  merged_to_tag_id BIGINT NULL,                     -- status=MERGED 时的目标标签编号
  created_at  DATETIME,
  updated_at  DATETIME,                             -- 改名、禁用、合并时更新
  UNIQUE KEY uk_scope_dim_name_creator (scope, dimension, tag_name, creator_id)
);
```
> 唯一键包含 `creator_id`：不同用户可以有同名私有标签，同一用户同维度下不能重名；PUBLIC 标签以 `creator_id=0` 参与唯一性判断（MySQL 唯一索引对 NULL 不去重，因此不能用 NULL 占位）。`tag_name` 入库前统一去首尾空格、全半角和连续空格（SRS 4.1.5）。

### 5. resource_tag（资源-标签关系表）—— 【C】
```sql
CREATE TABLE resource_tag (
  resource_id BIGINT NOT NULL,
  tag_id      BIGINT NOT NULL,
  created_at  DATETIME,
  PRIMARY KEY (resource_id, tag_id),
  INDEX idx_tag (tag_id)
);
-- 单个资源标签数量上限 20，由 Service 层校验
```

### 6. favorite（收藏表）—— 【D】
```sql
CREATE TABLE favorite (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  created_at  DATETIME,
  UNIQUE KEY uk_user_resource (user_id, resource_id)
);
```

### 7. rating（评价表）—— 【D】
```sql
CREATE TABLE rating (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  rating      VARCHAR(10) NOT NULL,             -- LIKE/DISLIKE/NONE
  created_at  DATETIME,
  updated_at  DATETIME,
  UNIQUE KEY uk_user_resource (user_id, resource_id)
);
```

### 8. download_record（下载记录表）—— 【D】写入，【E】统计
```sql
CREATE TABLE download_record (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  result      VARCHAR(20) NOT NULL,              -- SUCCESS/DENIED/NOT_FOUND/ERROR
  created_at  DATETIME,
  INDEX idx_resource (resource_id),
  INDEX idx_date (created_at)
);
```

### 9. daily_statistics（日聚合表）—— 【E】
```sql
CREATE TABLE daily_statistics (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date       DATE NOT NULL,
  upload_count    INT DEFAULT 0,
  download_count  INT DEFAULT 0,
  active_resources INT DEFAULT 0,
  UNIQUE KEY uk_date (stat_date)
);
```

### 10. audit_log（审计日志表）—— 【E】
```sql
CREATE TABLE audit_log (
  audit_id    VARCHAR(64) PRIMARY KEY,            -- 全局唯一字符串编号（UUID），与 SRS 4.1.9 一致
  actor_id    BIGINT NOT NULL,
  action      VARCHAR(50) NOT NULL,          -- DELETE_RESOURCE/UPDATE_TAG/DISABLE_TAG...
  target_type VARCHAR(30),
  target_id   BIGINT,
  detail      VARCHAR(500),
  result      VARCHAR(20),                   -- SUCCESS/FAILURE
  created_at  DATETIME
);
```

> 本期使用 HttpSession，不建立会话表，会话数据保存在 Tomcat 内存中。JobRecord、BackupRecord 本期不建实体表，定时任务通过日志记录运行情况。

---

## 四、后端 REST API 清单

统一前缀 `/api`，统一响应体（依据 SRS 表 4.1.11）：
```json
{ "code": "OK", "message": "操作成功", "data": { ... }, "requestId": "..." }
```
成功时 `code="OK"`；失败时 `code` 为形如 `USERNAME_DUPLICATED`、`AUTH_BAD_CREDENTIALS`、`AUTH_ACCOUNT_LOCKED`、`AUTH_ACCOUNT_DISABLED`、`PARAM_ERROR`、`RESOURCE_NOT_FOUND`、`RESOURCE_FORBIDDEN`、`RESOURCE_FILE_TOO_LARGE`、`RESOURCE_EXTENSION_NOT_ALLOWED`、`TAG_INVALID`、`ADMIN_REQUIRED`、`SYSTEM_ERROR` 的字符串错误码，前端按 code 分支提示，不依赖 message 文案。HTTP 状态码只表达 401/403/404/500 等网络层语义，业务成败一律看 body 里的 code。

### 4.1 用户域（【A】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 | 说明 |
|---|---|---|---|---|---|
| POST | /api/auth/register | 1.1.0 | username, password, nickname | user_id | 账号唯一校验；username 4~32 位字母数字下划线，password 8~64 个字符且 UTF-8 编码不超过 72 字节（BCrypt 上限，超限返回 `PARAM_ERROR`），nickname 2~30 位；密码 BCrypt |
| POST | /api/auth/login | 1.2.0 | username, password | 用户信息+写入 session | 失败分支见下 |
| POST | /api/auth/logout | - | - | - | 清除 session |
| GET | /api/auth/me | 1.4.0 | - | 当前用户信息 | 拦截器校验后可用 |
| GET | /api/profile/my-resources | 1.4.0 | page, page_size | 我的资源分页 | 联表【B】的 resource |
| GET | /api/profile/my-favorites | 1.4.0 | page, page_size | 我的收藏分页 | 联表【D】的 favorite |

**登录失败锁定逻辑（SRS-1.3.0）**：
1. 先按 username 查询用户。查不到则直接返回"账号或密码错误"，不暴露账号是否存在。
2. 若 `status=DISABLED`，直接返回"账号已被禁用，请联系管理员"。
3. 若 `status=TEMP_LOCKED`：
   - `locked_until > NOW()`：返回 `AUTH_ACCOUNT_LOCKED`，并附带 `lockedUntil`（UTC ISO 8601），前端据此显示剩余等待时间（SRS 4.1.2）；
   - `locked_until <= NOW()`：先执行 `UPDATE user SET status='ACTIVE', failed_login_count=0, locked_until=NULL WHERE user_id=? AND status='TEMP_LOCKED' AND locked_until <= NOW()` 自动解除锁定，再进入密码比对。
4. 使用 BCrypt.matches 比对密码：
   - 失败：用**一条原子 UPDATE**完成“累加 + 达阈值即锁定”，避免先查后写；失败次数上限为 5，锁定到期后的下一次失败重新锁定 15 分钟：
     ```sql
     UPDATE user SET
       status = CASE WHEN failed_login_count >= 4 THEN 'TEMP_LOCKED' ELSE status END,
       locked_until = CASE WHEN failed_login_count >= 4 THEN DATE_ADD(NOW(), INTERVAL 15 MINUTE) ELSE locked_until END,
       failed_login_count = LEAST(failed_login_count + 1, 5),
       updated_at = NOW()
     WHERE user_id = ? AND status = 'ACTIVE';
     ```
     MySQL 单表 UPDATE 的 SET 按从左到右求值，CASE 放在自增之前，判断的是本次失败前的次数（`>=4` 即本次为第 5 次）；`NOW()` 在 UTC 会话时区下返回 UTC 时间。
   - 成功：`UPDATE user SET failed_login_count=0, status='ACTIVE', locked_until=NULL, updated_at=NOW() WHERE user_id=? AND status <> 'DISABLED'`。影响行数为 0 说明账号已被管理员禁用，按禁用分支返回；成功后将 user_id 写入 HttpSession。
5. 并发失败请求由上述单条 UPDATE 原子处理；`WHERE status='ACTIVE'` 保证不会覆盖管理员的禁用操作，失败计数也不会超过 5。

### 4.2 资源文件域（【B】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 |
|---|---|---|---|---|
| POST | /api/resources | 2.1.0 | multipart: file + title, description, material_type, visibility | resource_id |
| PUT | /api/resources/{id} | 2.2.0 | title, description, material_type, visibility | 更新结果 |
| DELETE | /api/resources/{id} | 2.3.0 | - | 逻辑删除 |
| PATCH | /api/resources/{id}/visibility | 2.4.0 | visibility | 更新结果 |

> 下载接口属于 SRS-5.3.0，由 **D** 实现（见 4.4）；B 只提供 `FileStorageService` 的文件读取能力，避免 B/D 各自实现一套下载逻辑。

**文件上传要点（SRS-2.5.0）**：
- application.yml 配置 `spring.servlet.multipart.max-file-size=100MB`、`max-request-size=100MB`。
- 后端再次校验 `file.getSize()`，超过 100MB 抛出 `RESOURCE_FILE_TOO_LARGE`。
- 扩展名白名单：`pdf,doc,docx,ppt,pptx,xls,xlsx,txt,zip,jpg,png,mp4`，不在白名单内则拒绝。
- 除扩展名外，后端还要读取文件头魔数与 `file.getContentType()` 联合校验，防止改后缀上传可执行文件（SRS 4.1.4）。
- 本期不接入病毒扫描组件，按 SRS EXT-01 豁免口径，上述校验通过后 `scan_status` 直接置 `PASSED`（file_object 表默认值已是 PASSED）；后续若接入扫描服务，需改回 PENDING 门禁。
- 存储路径：`file_id = UUID.randomUUID()`，写入 `./data/upload/{yyyyMM}/{file_id}`（`yyyyMM` 按 UTC 年月），`original_name` 经 URL 编码后写入响应头，防止路径穿越。
- 状态流转：上传开始 `resource.status=UPLOADING`，文件落盘并写入 file_object 成功后 `status=ACTIVE`，中途异常则 `status=FAILED`。

### 4.3 标签域（【C】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 |
|---|---|---|---|---|
| POST | /api/tags | 3.1.0 | tag_name, dimension, scope | tag_id |
| GET | /api/tags/mine | 3.1.0 | - | 我可见的标签列表（公共+自己的私有） |
| POST | /api/resources/{id}/tags | 3.2.0 | tag_ids[] | 绑定结果 |
| PUT | /api/resources/{id}/tags | 3.3.0 | tag_ids[]（全量替换） | 更新结果 |
| DELETE | /api/resources/{id}/tags/{tagId} | 3.4.0 | - | 解除绑定 |

**校验规则**：
- 创建标签时按 `(scope, dimension, tag_name, creator_id)` 判重：`tag_name` 先做规范化（去首尾空格、统一全半角、合并连续空格），PUBLIC 标签的 `creator_id` 固定为 0；不同用户可各自创建同名私有标签。
- 仅资源 owner 可修改自己资源的标签。
- 绑定时校验：标签必须为 ACTIVE 状态；私有标签仅可绑定到自己的资源；公共标签所有人可绑定。
- 单个资源绑定标签数超过 20 时报错。
- 修改标签即全量替换 resource_tag 中该资源的记录；移除标签只删除 resource_tag 行，不删除 tag 本体。

### 4.4 检索与互动域（【D】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 |
|---|---|---|---|---|
| GET | /api/search | 4.1/4.2/4.3/4.4 | tag_ids[], keyword, sort_by, page, page_size | 资源分页列表 |
| POST | /api/resources/{id}/rating | 5.1.0 | rating(LIKE/DISLIKE/NONE) | 更新后计数 |
| POST | /api/resources/{id}/favorite | 5.2.0 | favorite(true/false) | 结果 |
| GET | /api/resources/{id} | - | - | 资源详情+我的评价+收藏状态 |
| GET | /api/resources/{id}/download | 5.3.0 | - | 文件流 |

**多标签 AND 交集查询（SRS-4.2.0，本项目核心 SQL）**：
```sql
SELECT r.resource_id, r.title, r.description, r.material_type,
       r.visibility, r.download_count, r.like_count, r.dislike_count,
       r.created_at, u.nickname AS owner_name
FROM resource r
JOIN user u ON r.owner_id = u.user_id
WHERE r.visibility = 'PUBLIC'
  AND r.status = 'ACTIVE'
  AND r.deleted_at IS NULL
  AND (:keyword IS NULL OR r.title LIKE CONCAT('%', :keyword, '%')
                        OR r.description LIKE CONCAT('%', :keyword, '%'))
  AND r.resource_id IN (
        SELECT rt.resource_id
        FROM resource_tag rt
        JOIN tag t ON rt.tag_id = t.tag_id
        WHERE t.status = 'ACTIVE' AND rt.tag_id IN (<tag_ids...>)
        GROUP BY rt.resource_id
        HAVING COUNT(DISTINCT rt.tag_id) = <tag_ids.length>
  )
ORDER BY
  CASE WHEN :sort_by = 'DOWNLOAD_COUNT' THEN r.download_count END DESC,
  r.created_at DESC,
  r.resource_id DESC
LIMIT :offset, :page_size;
```
同时执行 `SELECT COUNT(*)` 获取总数。`tag_ids` 为空数组时去掉整个子查询，仅按 keyword 过滤。

**检索入参约束（SRS 4.1.6）**：`keyword` 去掉首尾空格后不超过 100 字符，空串视为不过滤；`tag_ids` 去重后不超过 20 个，含禁用/越权/不存在的标签时直接报 `TAG_INVALID`；`page_size` 只允许 10/20/50，默认 20；`sort_by` 只允许 `UPLOAD_TIME`/`DOWNLOAD_COUNT`，默认 `UPLOAD_TIME`；`page` 超出最大页时返回空列表而非报错。

**点赞/差评（SRS-5.1.0）**：
- 使用 `INSERT INTO rating ... ON DUPLICATE KEY UPDATE rating=?` 一条 SQL 完成切换。
- 同一事务内根据新旧 rating 调整 resource 表的 like_count、dislike_count（例如旧值 LIKE 新值 DISLIKE，则 like_count-1、dislike_count+1）。
- 重复提交相同 rating 视为取消，置为 NONE。

**下载（SRS-5.3.0，【D】实现）**：
- 下载 controller/service 及 `download_record` 均由 **D** 负责；文件读取调用 B 的 `FileStorageService`，不重复实现磁盘路径解析与文件名编码。
- 权限校验：PUBLIC+ACTIVE 任何人可下载；PRIVATE 仅 owner 可下载；ADMIN 可下载；其他返回 403。
- 使用 `FileInputStream` 配合 `response.setContentType(APPLICATION_OCTET_STREAM)` 写回，`Content-Disposition` 携带文件名。
- 成功后执行 `UPDATE resource SET download_count = download_count + 1`，并插入一条 `download_record`（result=SUCCESS）；被拒绝、资源不存在或传输异常时分别写入 DENIED / NOT_FOUND / ERROR，便于 E 统计与排查。

### 4.5 管理员后台域（【E】）

| 方法 | 路径 | SRS 编号 | 说明 |
|---|---|---|---|
| GET | /api/admin/resources | 7.1.0 | 全部资源（含私有、含 BLOCKED），分页 |
| DELETE | /api/admin/resources/{id} | 7.1.0 | 置 status=BLOCKED，写 audit_log，填写原因 |
| POST | /api/admin/tags | 7.2.0 | 新增公共标签 |
| PUT | /api/admin/tags/{id} | 7.2.0 | 修改公共标签 |
| PATCH | /api/admin/tags/{id}/disable | 7.2.0 | 禁用（status=DISABLED） |
| GET | /api/admin/stats/overview | 7.3.0 | 上传数、下载数、资源总数 |
| GET | /api/admin/stats/daily | 7.3.0 | 近 30 天 daily_statistics，供 ECharts 折线图使用 |

所有 `/api/admin/` 路径下的接口，拦截器额外校验 `user.role == 'ADMIN'`，否则返回 403。

### 4.6 横切权限（SRS-6.1.0，【A】实现拦截器，全员共用）

`LoginInterceptor.preHandle()` 逻辑：
1. 未登录返回 401；每次请求校验账号状态，被禁用或已删除的账号立即失效会话。
2. **仅 GET/HEAD 请求**路径匹配 `/api/resources/{id}`、`/api/resources/{id}/download` 时，从路径取出 resource_id 并查询 resource（查询条件含 `deleted_at IS NULL`）：
   - `owner_id == 当前 user_id` 放行：owner 可查看自己处于 UPLOADING/PROCESSING/ACTIVE/FAILED/BLOCKED 的资源，BLOCKED 资源由前端标注“已下架”，避免“列表可见、详情 403”；
   - `visibility=PUBLIC && status=ACTIVE` 放行；
   - 当前用户为 ADMIN 放行；
   - 其他情况返回 403，提示"资源不存在或无权访问"，不区分不存在与无权限，防止枚举探测。
3. `PUT/DELETE/PATCH` 等写操作以及 `/api/resources/{id}/tags` 等子资源写接口，**不在拦截器做可见性放行**，必须由对应 Service 校验 `owner_id == 当前 user_id`，防止公开资源被他人修改或删除。

---

## 五、前端页面清单与负责人

| 页面/组件 | 路由 | 负责人 | 调用接口 |
|---|---|---|---|
| 登录页 | /login | A | /api/auth/login |
| 注册页 | /register | A | /api/auth/register |
| 顶部导航栏 | 全局布局 | A | /api/auth/me |
| 个人中心（我的资源/我的收藏两个 Tab） | /profile | A | /api/profile/my-resources, /api/profile/my-favorites |
| 上传页 | /upload | B | POST /api/resources（multipart） |
| 我的资源管理列表（编辑/删除/切换私有公开） | /my | B | PUT/DELETE /api/resources/{id}, PATCH visibility |
| 标签选择器组件（按维度分组折叠，已选标签块） | 复用组件 | C | GET /api/tags/mine, POST/PUT /api/resources/{id}/tags |
| 首页（公开资源浏览+检索区+结果列表+分页） | / | D | GET /api/search |
| 资源详情页（介绍+点赞/差评/收藏/下载按钮+标签列表） | /resource/:id | D | GET /api/resources/{id}, rating, favorite, download |
| 公共组件（空状态、分页控件等） | 全局复用 | E | - |
| 管理员后台布局（左侧菜单） | /admin | E | - |
| 资源审核列表（管理员下架违规资源） | /admin/resources | E | GET/DELETE /api/admin/resources |
| 公共标签管理页 | /admin/tags | E | /api/admin/tags CRUD |
| 统计页（ECharts 折线图+数字卡片） | /admin/stats | E | /api/admin/stats/* |

**路由守卫**：Vue Router 中配置 `beforeEach`，除 /login 和 /register 外，其他路由检查本地登录态（localStorage 保存的用户信息，配合 session），未登录用户跳转 /login。**localStorage 只用于界面快速跳转，不能作为权限依据**：被篡改最多让页面短暂显示，所有接口仍以服务端 Session 校验为准；接口返回 401 时统一清除本地登录态并跳转 /login。前端使用 history 模式时需按第 1 章“静态资源与 SPA 路由”配置后端 fallback。

---

## 六、关键技术点说明

| 技术点 | 对应 SRS | 正确做法 | 常见错误做法 |
|---|---|---|---|
| 密码存储 | 1.1.0 | BCrypt，盐自动生成 | MD5、明文、自行拼接盐值 |
| 登录锁定 | 1.3.0 | 数据库原子累加 failed_login_count，达到 5 次置 TEMP_LOCKED 15 分钟 | 使用 Redis 计数（本期不引入）、仅在前端锁定按钮 |
| 时间与时区 | 4.1/5.1 | JVM、MySQL 会话与 JDBC 统一 UTC 存储，接口输出 UTC ISO 8601，前端转北京时间展示 | 直接存本地时间、依赖部署机默认时区、前端手工 ±8 小时 |
| 多标签交集 | 4.2.0 | SQL `GROUP BY resource_id HAVING COUNT(DISTINCT tag_id)=N` | 在应用层多次查询后求交集（性能差且易出错） |
| 私有资源保护 | 6.1.0 | 拦截器中服务端二次查询判断 owner | 仅在前端隐藏下载按钮 |
| 文件大小限制 | 2.5.0 | yml 配置 + 后端再校验 file.getSize() | 仅在前端 input 配置 accept 和大小判断 |
| 逻辑删除 | 2.3.0 | 查询时 `deleted_at IS NULL`，物理删除由定时任务执行 | 直接 DELETE 删除整行 |
| 计数并发 | 5.1.0/5.3.0 | `UPDATE resource SET like_count = like_count + 1` | 先 SELECT 再 UPDATE（并发下丢失更新） |
| 收藏幂等 | 5.2.0 | 唯一约束 + `INSERT ... ON DUPLICATE KEY` | 每次收藏都插入新行 |
| 分页稳定排序 | 4.4.0 | 末尾追加 `resource_id DESC` 作为排序兜底 | 仅按 created_at 排序（同一秒内顺序不稳定） |
| SQL 注入 | 4.3.0 | MyBatis `#{}` 参数绑定 | 拼接 keyword 字符串构造 SQL |

---

## 七、五人分工总表

| 角色 | 定位 | 负责 SRS 编号 | 主要交付物 | 数据表 | 工作量占比 |
|---|---|---|---|---|---|
| **A** | 公共基础与用户域 | 公共骨架 + 1.1.0, 1.2.0, 1.3.0, 1.4.0, 6.1.0 | SpringBoot 工程骨架、Result 统一响应、全局异常、CORS、登录拦截器（含资源权限校验）、注册/登录/锁定/个人中心后端；登录页、注册页、导航栏、个人中心前端 | user, audit_log（配合） | ~21% |
| **B** | 资源文件 | 2.1.0, 2.2.0, 2.3.0, 2.4.0, 2.5.0 | 文件上传、大小与扩展名校验、SHA-256、本地磁盘存储（提供下载所需的文件读取能力）、资源编辑/逻辑删除/私有公开切换；上传页、我的资源管理页 | resource, file_object | ~22% |
| **C** | 标签 | 3.1.0, 3.2.0, 3.3.0, 3.4.0 | 标签 CRUD（公共/私有、五个维度）、资源-标签绑定关系维护、TagPicker 组件 | tag, resource_tag | ~18% |
| **D** | 检索与互动 | 4.1.0, 4.2.0, 4.3.0, 4.4.0, 5.1.0, 5.2.0, 5.3.0 | 多标签 AND 交集查询、关键词模糊搜索、排序分页 SQL；点赞/差评/收藏/下载接口（含 download_record 写入）；首页、结果列表、资源详情页 | favorite, rating, download_record（联表 resource/resource_tag） | ~21% |
| **E** | 后台与公共组件 | 7.1.0, 7.2.0, 7.3.0 + EXT-02/03/06 | 管理员资源审核、公共标签管理、统计聚合与 ECharts 图表；审计日志写入；定时任务（清理临时文件、每日统计）；管理员后台全部页面；前端公共组件（空状态、分页控件）；日志落盘与 30 天轮转（logback）、数据库备份脚本；联调用例、测试账号与部署脚本 | daily_statistics, audit_log | ~18% |

> 各模块负责人为核心 Service 编写单元测试（SRS 7.2），E 汇总测试账号与联调用例；日志按 SRS 5.4 轮转保留 30 天，审计日志与下载/收藏/评价明细按 SRS 5.5 至少保留 6 个月。

### 联调约定

1. 项目启动时由 A 先完成工程骨架、schema.sql、Result 类、LoginInterceptor、CORS 配置并推送到 Git，其他人再拉取各自模块开发，避免重复搭建工程。
2. 接口字段统一使用驼峰命名（`resourceId`、`createdAt`），数据库字段使用下划线命名（`resource_id`、`created_at`），MyBatis-Plus 配置 `map-underscore-to-camel-case: true`。时间字段一律输出 UTC ISO 8601（`2026-11-12T08:00:00Z`），实体字段类型统一为 `Instant`，前端统一按本地时区展示，不在各页面单独转换。
3. 跨模块数据访问直接在对应 Mapper XML 中编写 SQL，不直接调用他人模块的 Service；**文件本体读取是唯一例外**：D 的下载接口通过 B 的 `FileStorageService` 公共方法取流，不自行拼接磁盘路径。
4. Git 分支：每人一个分支 `user/a`、`user/b`、`user/c`、`user/d`、`user/e`，每日合并一次 main。
5. 枚举值前后端保持一致：`PUBLIC/PRIVATE/ACTIVE/DELETED/BLOCKED/COURSEWARE/EXAM/EXERCISE/NOTE/COURSE/EXAM/MATERIAL/YEAR/OTHER/LIKE/DISLIKE` 等字符串以后端和 SRS 表 4.1 为准。
6. 进度里程碑：
   - 第 1 周：A 完成骨架与数据库，其他人完成各自模块后端 CRUD。
   - 第 2 周：前端页面全部完成，开始联调。
   - 第 3 周：D 完成检索联调，E 完成统计联调，全员联调、修复问题，准备测试账号与演示数据。
   - 最后 2 天：A 负责部署，演示数据写入 schema.sql。
