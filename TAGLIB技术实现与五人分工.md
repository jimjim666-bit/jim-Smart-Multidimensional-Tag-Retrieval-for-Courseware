# TAGLIB 课件资料多维标签检索网站 —— 技术实现方案与五人分工

> 本文档根据《课件资料多维度智能标签检索网站系统需求分析》(TAGLIB-SRS-0001 v2.0) 整理，目的是把需求文档里的 24 条 SRS 翻译成"实际要写哪些类、哪些接口、哪些表、哪些页面"，并按五人切分工作量。所有编号与原 SRS 一一对应，编码时直接拿编号当索引。五名开发人员在本文档中以 A/B/C/D/E 代称。

---

## 一、技术栈与版本（先定死，不许各写各的）

| 层 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 前端框架 | Vue 3 + Vite | Vue 3.4+ | 用 `<script setup>` 组合式 API，不用 Vue2 |
| UI 组件 | Element Plus | 最新稳定版 | 表格、分页、上传、标签、弹窗全都用现成的，不要手写 CSS 控件 |
| 前端 HTTP | Axios | 1.x | 统一封装 `request.js`，请求带 cookie（withCredentials） |
| 后端框架 | Spring Boot | 2.7.x（JDK 11） | 不用 Spring Boot 3，避免 Jakarta 包名变动 |
| ORM | MyBatis-Plus | 3.5.x | 单表 CRUD 用 MP，多标签交集查询自己写 XML |
| 数据库 | MySQL | 8.0 | utf8mb4，时区 UTC |
| 连接池 | HikariCP | Spring Boot 自带 | 默认即可 |
| 密码哈希 | BCrypt（Spring Security-crypto） | 5.x | 只引入 `spring-security-crypto` 一个包，不引整套 Security |
| 文件存储 | 本地磁盘目录 | - | 存到 `./data/upload/`，路径写在 application.yml |
| 定时任务 | Spring `@Scheduled` | - | 清理临时文件、每日统计聚合 |
| 图表 | ECharts 5 | - | 管理员统计页用，其他人不引 |

**部署形态**：一台 Windows 机器跑 MySQL + SpringBoot（内置 Tomcat，端口 8080）+ Vite build 后的静态文件（可以直接放进 SpringBoot 的 `resources/static`，不用单独起 Nginx）。局域网访问 `http://主机IP:8080`。

---

## 二、项目目录结构（统一按这个建）

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
│   │   │   ├── controller/FileController.java     # 上传下载
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
│   │   │   └── mapper/xml/SearchMapper.xml          # 多标签交集 SQL 写这
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
│   │   └── static/                   # 前端 build 产物扔这
│   └── pom.xml
├── frontend/                         # Vue 工程
│   ├── src/
│   │   ├── api/                     # axios 接口定义，按模块分文件
│   │   ├── views/
│   │   │   ├── Login.vue  Register.vue              # 【A】
│   │   │   ├── Profile.vue                         # 【A】个人中心
│   │   │   ├── Upload.vue  MyResources.vue         # 【B】
│   │   │   ├── components/TagPicker.vue            # 【C】标签选择器
│   │   │   ├── Home.vue  SearchResult.vue         # 【D】首页+检索
│   │   │   ├── ResourceDetail.vue                  # 【D】详情+互动
│   │   │   └── admin/AdminLayout.vue  Audit.vue  TagAdmin.vue  Stats.vue   # 【E】
│   │   ├── router/index.js
│   │   └── App.vue  main.js
└── db/
    └── schema.sql                   # 建表语句，全员共用
```

---

## 三、数据库表设计（13 张表，全员共用 schema.sql）

字段类型按 MySQL 8.0 写。所有表都要有 `created_at DATETIME`、`updated_at DATETIME`（由 MP 自动填充）。主键 `BIGINT AUTO_INCREMENT`，对外接口用 String 传避免 JS 精度丢失。

### 1. user（用户表）—— 【A】
```sql
CREATE TABLE user (
  user_id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  username       VARCHAR(32) NOT NULL UNIQUE,         -- 4~32位 字母数字下划线
  password_hash  VARCHAR(100) NOT NULL,               -- BCrypt，绝不存明文
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
  owner_id        BIGINT NOT NULL,                  -- 服务端从 session 取，不信前端
  title           VARCHAR(120) NOT NULL,
  description     VARCHAR(2000) DEFAULT '',
  material_type   VARCHAR(20) NOT NULL,             -- COURSEWARE/EXAM/EXERCISE/NOTE
  visibility      VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
                    -- UPLOADING/PROCESSING/ACTIVE/FAILED/DELETED/BLOCKED
  download_count  INT NOT NULL DEFAULT 0,
  like_count      INT NOT NULL DEFAULT 0,
  dislike_count   INT NOT NULL DEFAULT 0,
  file_id         VARCHAR(64),                      -- 关联 file_object.file_id
  created_at     DATETIME,
  updated_at     DATETIME,
  deleted_at      DATETIME NULL,                    -- 逻辑删除标记
  INDEX idx_owner (owner_id),
  INDEX idx_visibility_status (visibility, status, created_at)
);
```

### 3. file_object（文件对象表）—— 【B】
```sql
CREATE TABLE file_object (
  file_id       VARCHAR(64) PRIMARY KEY,            -- UUID
  resource_id   BIGINT,
  original_name VARCHAR(255) NOT NULL,
  storage_key   VARCHAR(255) NOT NULL,             -- 磁盘上的真实路径文件名，不暴露给前端
  extension     VARCHAR(16) NOT NULL,
  mime_type     VARCHAR(100),
  size_bytes    BIGINT NOT NULL,
  checksum      CHAR(64),                           -- SHA-256
  scan_status   VARCHAR(20) DEFAULT 'PASSED',      -- 本期不接杀毒，直接 PASSED
  storage_status VARCHAR(20) DEFAULT 'AVAILABLE',
  created_at    DATETIME
);
```

### 4. tag（标签表）—— 【C】
```sql
CREATE TABLE tag (
  tag_id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag_name    VARCHAR(50) NOT NULL,
  dimension   VARCHAR(20) NOT NULL DEFAULT 'OTHER',  -- COURSE/EXAM/MATERIAL/YEAR/OTHER
  scope       VARCHAR(20) NOT NULL DEFAULT 'PRIVATE', -- PUBLIC/PRIVATE
  creator_id  BIGINT,                               -- 私有标签必填
  status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',-- ACTIVE/DISABLED/MERGED
  created_at  DATETIME,
  UNIQUE KEY uk_scope_dim_name (scope, dimension, tag_name)
);
```

### 5. resource_tag（资源-标签关系表）—— 【C】
```sql
CREATE TABLE resource_tag (
  resource_id BIGINT NOT NULL,
  tag_id      BIGINT NOT NULL,
  created_at  DATETIME,
  PRIMARY KEY (resource_id, tag_id),
  INDEX idx_tag (tag_id)
);
-- 一个资源最多 20 个标签由 service 层校验，数据库不做限制
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

### 8. download_record（下载记录表）—— 【B】写下载，【E】做统计
```sql
CREATE TABLE download_record (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  result      VARCHAR(20) NOT NULL,              -- SUCCESS/DENIED/NOT_FOUND
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
  audit_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id    BIGINT NOT NULL,
  action      VARCHAR(50) NOT NULL,          -- DELETE_RESOURCE/UPDATE_TAG/DISABLE_TAG...
  target_type VARCHAR(30),
  target_id   BIGINT,
  detail      VARCHAR(500),
  result      VARCHAR(20),
  created_at  DATETIME
);
```

> Session 表**不用建**，直接用 HttpSession 存在 Tomcat 内存里（单机演示够用）。JobRecord、BackupRecord 本期不做实体表，定时任务打日志就行。

---

## 四、后端 REST API 清单（前后端按这个对齐）

统一前缀 `/api`，统一响应体：
```json
{ "code": 0, "message": "ok", "data": { ... } }
```
`code=0` 成功，非 0 失败；错误码见原 SRS 表 4.1.11。

### 4.1 用户域（【A】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 | 说明 |
|---|---|---|---|---|---|
| POST | /api/auth/register | 1.1.0 | username, password, nickname | user_id | 账号唯一校验，密码 BCrypt |
| POST | /api/auth/login | 1.2.0 | username, password | 用户信息+写入 session | 失败分支见下 |
| POST | /api/auth/logout | - | - | - | 清 session |
| GET | /api/auth/me | 1.4.0 | - | 当前用户信息 | 拦截器校验后可用 |
| GET | /api/profile/my-resources | 1.4.0 | page, page_size | 我的资源分页 | 联表【B】的 resource |
| GET | /api/profile/my-favorites | 1.4.0 | page, page_size | 我的收藏分页 | 联表【D】的 favorite |

**登录失败锁定逻辑（SRS-1.3.0，核心难点）**：
1. 先查 user by username。查不到 → 直接返回"账号或密码错误"（不暴露账号是否存在）。
2. 检查 `status=TEMP_LOCKED AND locked_until > now()` → 返回"账号已临时锁定，请 xx 后再试"。
3. BCrypt.matches 比对密码：
   - 失败：`UPDATE user SET failed_login_count = failed_login_count + 1 WHERE user_id=?`。如果累计到 5，同时 `status='TEMP_LOCKED', locked_until=DATE_ADD(NOW(), INTERVAL 15 MINUTE)`。
   - 成功：`failed_login_count=0, status=ACTIVE, locked_until=NULL`，把 user_id 塞进 HttpSession。
4. 并发问题用数据库行锁或 `UPDATE ... WHERE failed_login_count < 5` 保证原子。

### 4.2 资源文件域（【B】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 |
|---|---|---|---|---|
| POST | /api/resources | 2.1.0 | multipart: file + title, description, material_type, visibility | resource_id |
| PUT | /api/resources/{id} | 2.2.0 | title, description, material_type, visibility | 更新结果 |
| DELETE | /api/resources/{id} | 2.3.0 | - | 逻辑删除 |
| PATCH | /api/resources/{id}/visibility | 2.4.0 | visibility | 更新结果 |
| GET | /api/resources/{id}/download | 5.3.0 | - | 文件流 |

**文件上传关键实现（SRS-2.5.0）**：
- `application.yml` 配 `spring.servlet.multipart.max-file-size=100MB`、`max-request-size=100MB`。
- 后端再查一次 `file.getSize()`，超 100MB 直接抛 `RESOURCE_FILE_TOO_LARGE`。
- 扩展名白名单：`pdf,doc,docx,ppt,pptx,xls,xlsx,txt,zip,jpg,png,mp4`。不在白名单 → 拒绝。
- 存储：`file_id = UUID.randomUUID()`，写到 `./data/upload/{yyyyMM}/{file_id}`，`original_name` URL 编码后再进响应头，防路径穿越。
- 状态流：上传开始 `resource.status=UPLOADING` → 文件落盘+写 file_object 成功 → `status=ACTIVE`。中途异常 → `status=FAILED`。

### 4.3 标签域（【C】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 |
|---|---|---|---|---|
| POST | /api/tags | 3.1.0 | tag_name, dimension, scope | tag_id |
| GET | /api/tags/mine | 3.1.0 | - | 我可见的标签列表（公共+自己的私有） |
| POST | /api/resources/{id}/tags | 3.2.0 | tag_ids[] | 绑定结果 |
| PUT | /api/resources/{id}/tags | 3.3.0 | tag_ids[]（全量替换） | 更新结果 |
| DELETE | /api/resources/{id}/tags/{tagId} | 3.4.0 | - | 解除绑定 |

**校验规则**：
- 只有资源 owner 能改自己资源的标签。
- 绑定时校验：标签必须是 ACTIVE 状态；私有标签只能绑给自己的资源；公共标签所有人可绑。
- 一个资源绑超过 20 个 → 报错。
- "修改标签"按 SRS 描述其实就是全量替换 resource_tag 里该 resource 的记录；"移除标签"只删 resource_tag 行，不删 tag 本体。

### 4.4 检索与互动域（【D】）

| 方法 | 路径 | SRS 编号 | 入参 | 出参 |
|---|---|---|---|---|
| GET | /api/search | 4.1/4.2/4.3/4.4 | tag_ids[], keyword, sort_by, page, page_size | 资源分页列表 |
| POST | /api/resources/{id}/rating | 5.1.0 | rating(LIKE/DISLIKE/NONE) | 更新后计数 |
| POST | /api/resources/{id}/favorite | 5.2.0 | favorite(true/false) | 结果 |
| GET | /api/resources/{id} | - | - | 资源详情+我的评价+收藏状态 |

**多标签 AND 交集查询（SRS-4.2.0，项目最核心 SQL）**：
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
同时跑一个 `SELECT COUNT(*)` 拿 total。`tag_ids` 空数组时整个子查询去掉，只按 keyword 过滤。

**点赞/差评（SRS-5.1.0）**：
- `INSERT INTO rating ... ON DUPLICATE KEY UPDATE rating=?` 一条 SQL 搞定切换。
- 同事务里根据新旧 rating 调整 resource 表 like_count/dislike_count（旧是 LIKE 新是 DISLIKE → like_count-1, dislike_count+1）。
- 重复点同一个 rating 视为取消（变 NONE）。

**下载（SRS-5.3.0）**：
- 先过权限：PUBLIC+ACTIVE 任何人可下；PRIVATE 只有 owner 可下；否则返回 403。
- 用 `FileInputStream` + `response.setContentType(APPLICATION_OCTET_STREAM)` 写回，`Content-Disposition` 带文件名。
- 成功后 `UPDATE resource SET download_count = download_count + 1`，插一条 download_record。

### 4.5 管理员后台域（【E】）

| 方法 | 路径 | SRS 编号 | 说明 |
|---|---|---|---|
| GET | /api/admin/resources | 7.1.0 | 全部资源（含私有、含 BLOCKED），分页 |
| DELETE | /api/admin/resources/{id} | 7.1.0 | 置 status=BLOCKED，写 audit_log，填原因 |
| POST | /api/admin/tags | 7.2.0 | 新增公共标签 |
| PUT | /api/admin/tags/{id} | 7.2.0 | 改公共标签 |
| PATCH | /api/admin/tags/{id}/disable | 7.2.0 | 禁用（status=DISABLED） |
| GET | /api/admin/stats/overview | 7.3.0 | 上传数、下载数、资源总数 |
| GET | /api/admin/stats/daily | 7.3.0 | 近 30 天 daily_statistics，给 ECharts 画折线图 |

**所有 /api/admin/** 开头的接口，拦截器里额外校验 `user.role == 'ADMIN'`，否则 403。

### 4.6 横切权限（SRS-6.1.0，【A】写拦截器，全员共用）

`LoginInterceptor.preHandle()` 里做：
1. 没 session → 401。
2. 请求路径匹配 `/api/resources/{id}`、`/api/resources/{id}/download` 时，从 path 取出 resource_id，查 resource：
   - `visibility=PUBLIC && status=ACTIVE` → 放行。
   - `visibility=PRIVATE` 且 `owner_id == 当前 user_id` → 放行。
   - 当前 user 是 ADMIN → 放行。
   - 其他 → 403，返回"资源不存在或无权访问"（不区分是不存在还是没权限，防枚举）。

---

## 五、前端页面清单与负责人

| 页面/组件 | 路由 | 负责人 | 用到的后端接口 |
|---|---|---|---|
| 登录页 | /login | A | /api/auth/login |
| 注册页 | /register | A | /api/auth/register |
| 顶部导航栏 | 全局布局 | A | /api/auth/me |
| 个人中心（我的资源/我的收藏两个 Tab） | /profile | A | /api/profile/my-resources, /api/profile/my-favorites |
| 上传页 | /upload | B | POST /api/resources（multipart） |
| 我的资源管理列表（编辑/删除/切私有公开） | /my | B | PUT/DELETE /api/resources/{id}, PATCH visibility |
| 标签选择器组件（按维度分组折叠，已选标签块） | 复用组件 | C | GET /api/tags/mine, POST/PUT /api/resources/{id}/tags |
| 首页（=公开资源浏览+检索区+结果列表+分页） | / | D | GET /api/search |
| 资源详情页（介绍+点赞/差评/收藏/下载按钮+标签列表） | /resource/:id | D | GET /api/resources/{id}, rating, favorite, download |
| 管理员后台布局（左侧菜单） | /admin | E | - |
| 资源审核列表（管理员删违规） | /admin/resources | E | GET/DELETE /api/admin/resources |
| 公共标签管理页 | /admin/tags | E | /api/admin/tags CRUD |
| 统计页（ECharts 折线图+数字卡片） | /admin/stats | E | /api/admin/stats/* |

**路由守卫**：Vue Router 里加 `beforeEach`，除了 /login 和 /register，其他路由都检查本地有没有登录态（localStorage 里存的用户信息，配合 session）。游客直接跳 /login。

---

## 六、关键技术难点对照（容易踩坑的点）

| 难点 | 对应 SRS | 正确做法 | 错误做法 |
|---|---|---|---|
| 密码存储 | 1.1.0 | BCrypt.hashpw，盐自动带 | MD5、明文、自己拼盐 |
| 登录锁定 | 1.3.0 | 数据库原子累加 failed_login_count，到 5 置 TEMP_LOCKED 15 分钟 | 用 Redis 计数（本期不用）、前端只锁 UI |
| 多标签交集 | 4.2.0 | SQL `GROUP BY resource_id HAVING COUNT(DISTINCT tag_id)=N` | 在 Java 里多次查 list 再取交集（慢且易错） |
| 私有资源保护 | 6.1.0 | 拦截器里服务端二次查库判断 owner | 只靠前端不渲染"下载"按钮 |
| 文件大小限制 | 2.5.0 | yml 配置 + 后端再查 file.getSize() | 只在前端 input 写 accept 和 size 判断 |
| 逻辑删除 | 2.3.0 | 查的时候 `deleted_at IS NULL`，真删靠定时任务 | DELETE FROM 直接删行 |
| 计数并发 | 5.1.0/5.3.0 | `UPDATE resource SET like_count = like_count + 1` | 先 SELECT 再 UPDATE（并发会丢） |
| 收藏幂等 | 5.2.0 | 唯一约束 + `INSERT ... ON DUPLICATE KEY` | 每次收藏都 insert 一行 |
| 分页稳定排序 | 4.4.0 | 最后加 `resource_id DESC` 兜底 | 只按 created_at 排（同一秒会乱） |
| SQL 注入 | 4.3.0 | MyBatis `#{}` 参数绑定 | 字符串拼 keyword 进 SQL |

---

## 七、五人分工总表（直接按这个派活）

| 角色 | 定位 | 负责 SRS 编号 | 主要交付物 | 数据表 | 工作量占比 |
|---|---|---|---|---|---|
| **A** | 组长/架构 | 公共骨架 + 1.1.0, 1.2.0, 1.3.0, 1.4.0, 6.1.0 | SpringBoot 工程骨架、Result 统一响应、全局异常、CORS、登录拦截器（含资源权限校验）、注册/登录/锁定/个人中心后端；登录页、注册页、导航栏、个人中心前端 | user, audit_log(配合) | ~25% |
| **B** | 资源文件 | 2.1.0, 2.2.0, 2.3.0, 2.4.0, 2.5.0 | 文件上传下载、大小与扩展名校验、SHA-256、本地磁盘存储、资源编辑/逻辑删除/私有公开切换；上传页、我的资源管理页 | resource, file_object, download_record | ~22% |
| **C** | 标签 | 3.1.0, 3.2.0, 3.3.0, 3.4.0 | 标签 CRUD（公共/私有、五个维度）、资源-标签绑定关系维护、TagPicker 组件 | tag, resource_tag | ~18% |
| **D** | 检索与互动 | 4.1.0, 4.2.0, 4.3.0, 4.4.0, 5.1.0, 5.2.0, 5.3.0 | 多标签 AND 交集查询、关键词模糊搜索、排序分页 SQL；点赞/差评/收藏/下载接口；首页、结果列表、资源详情页 | favorite, rating（join resource/resource_tag） | ~22% |
| **E** | 后台运维 | 7.1.0, 7.2.0, 7.3.0 + EXT-02/03/06 | 管理员资源审核、公共标签管理、统计聚合与 ECharts 图表；审计日志写入；定时任务（清理临时文件、每日统计）；管理员后台全部页面 | daily_statistics, audit_log | ~13% |

> E 那栏占比偏低，因为他的功能大多是简单 CRUD + 一个图表。如果后期联调紧张，他可以同时帮忙写前端公共组件（空状态、分页控件）和测试用例。

### 联调约定（开干前必须全员对齐）

1. **第一天上午 A 先把工程骨架、schema.sql、Result 类、LoginInterceptor、CORS 配好推到 Git**，其他人再拉下来开始写自己的模块，不然各建各的工程最后合不上。
2. **接口字段名统一用驼峰**（`resourceId`、`createdAt`），数据库字段用下划线（`resource_id`、`created_at`），MyBatis-Plus 配 `map-underscore-to-camel-case: true`。
3. **跨模块调用只走 HTTP 接口，不直接调别人的 Service**。比如 D 写检索时要 join resource 表，直接在 SearchMapper.xml 里写 SQL，不要去 import B 的 ResourceService。
4. **Git 分支**：每人一个分支 `user/a`、`user/b`、`user/c`、`user/d`、`user/e`，每天晚上合一次 main。
5. **枚举值两边对齐**：`PUBLIC/PRIVATE/ACTIVE/DELETED/BLOCKED/COURSEWARE/EXAM/EXERCISE/NOTE/COURSE/EXAM/MATERIAL/YEAR/OTHER/LIKE/DISLIKE` 这些字符串前后端必须一模一样，以本文档和原 SRS 表 4.1 为准。
6. **进度里程碑建议**：
   - 第 1 周：A 出骨架 + 数据库；其他人写各自模块的后端 CRUD。
   - 第 2 周：前端页面全部写完，开始联调。
   - 第 3 周：D 把检索跑通，E 把统计跑通，全员联调 + 修 bug + 写测试账号和演示数据。
   - 最后 2 天：A 负责部署到一台机器上，演示数据塞进 schema.sql。
