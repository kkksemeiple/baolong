# 暴龙万事屋 · 校园任务大厅平台

> 软件工程课程项目。校园内的委托 / 接单平台：发任务时冻结「屋币」作为报酬，接单方完成后由发布方确认结算，全程记账可对账，并带论坛、私信、管理与仲裁后台。

- **前端**：Vue 3 + TypeScript + Vite + Element Plus + Pinia
- **后端**：Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + MySQL 8 + JWT
- **规模**：后端 90 个接口、22 张表；前端 22 个页面

---

## 一、目录结构

```
baolong/
├─ vue-wsw/                 前端工程
│  ├─ src/api/              按模块封装的接口层
│  ├─ src/views/            页面（任务 / 论坛 / 消息 / 个人中心 / 管理后台）
│  ├─ src/components/       公共组件（任务卡片、信誉分明细弹窗…）
│  └─ docs/前端说明文档.md
│
└─ wsw-server/              后端工程
   ├─ src/main/java/com/baolong/wsw/
   │  ├─ algorithm/         推荐打分、信誉计算、冲突检测、敏感词
   │  ├─ controller/        7 个控制器，90 个端点
   │  ├─ service/           业务层
   │  ├─ mapper/            MyBatis-Plus + 手写原子 SQL
   │  ├─ security/          JWT、登录态、幂等拦截
   │  └─ schedule/          任务过期 / 超时自动确认
   ├─ sql/                  建表脚本 + 增量脚本
   ├─ docs/                 迭代四全部文档（含 docx 提交版）
   └─ src/test/             白盒单测 + 集成测试
```

---

## 二、环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 低于 17 编译不过（用了 record / 文本块） |
| Maven | 3.9+ | 或用 IDEA 自带的 Maven |
| MySQL | 8.0+ | 默认库名 `wsw`，端口 3306 |
| Node.js | 18+（推荐 20） | 前端构建 |
| IDE | IntelliJ IDEA / VS Code | 后端 / 前端 |

---

## 三、5 分钟跑起来

### 1. 建库建表

用 Navicat 或命令行执行：

```bash
mysql -uroot -p < wsw-server/sql/01-schema.sql
```

> `01-schema.sql` 会 `DROP` 后重建 `wsw` 库，可重复执行。已有数据时先备份。

### 2. 配置数据库密码（不写进仓库）

`wsw-server/src/main/resources/application.yml` 里数据库密码是环境变量：

```yaml
username: ${DB_USERNAME:root}
password: ${DB_PASSWORD:}
```

启动前设置环境变量即可：

- **IDEA**：Run → Edit Configurations → Environment variables 填 `DB_PASSWORD=你的密码`
- **命令行**：
  ```bash
  set DB_PASSWORD=你的密码        # Windows CMD
  $env:DB_PASSWORD="你的密码"     # PowerShell
  ```

JWT 签名密钥同理，用环境变量 `WSW_JWT_SECRET`（32 位以上随机串）：

```bash
$env:WSW_JWT_SECRET="换成你自己的随机串"
```

不设置也能跑：首次启动会自动生成一份随机密钥存到 `wsw-server/.wsw/jwt-secret`
（该目录已在 `.gitignore` 里，不会进仓库；也刻意不放在 `uploads/`，因为那是静态直出目录）。
但**部署到服务器时必须显式配置**，否则删掉 `.wsw` 目录会让所有用户掉线重新登录。

### 3. 启动后端

```bash
cd wsw-server
mvn spring-boot:run
```

看到 `Tomcat started on port 8080` 与 `演示数据播种完成，默认密码：abc12345` 即成功。

### 4. 启动前端

```bash
cd vue-wsw
npm install
npm run dev
```

打开 <http://localhost:5173>。

> 前端默认连 `http://localhost:8080`（见 `.env.development` 的 `VITE_API_BASE_URL`）。
> **`VITE_USE_MOCK` 必须为 `false`**，否则走的是本地假数据、不会连后端。

### 5. 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `abc12345` | 超级管理员（审核 / 裁决 / 发币 / 配置） |
| `linxiaoman` | `abc12345` | 普通用户（已实名） |
| `zhaolei` | `abc12345` | 普通用户 |
| `chenyu` | `abc12345` | 普通用户（已实名） |

---

## 四、跑测试

```bash
cd wsw-server

# 白盒单测（27 条，不需要数据库）
mvn test

# 集成测试（需要本机 MySQL，在 IDEA 里右键运行 ApiFlowIT / CreditDetailIT）
mvn test -Dtest=ApiFlowIT
```

---

## 五、核心设计一览

| 主题 | 做法 |
| --- | --- |
| 抢单并发 | 带 `status='WAITING'` 与 `version` 守卫的原子 UPDATE，200 并发只成功一条 |
| 状态机 | 8 个任务状态 + 静态迁移表，非法迁移在 Java 层拒绝 |
| 钱包记账 | 原子条件更新；恒等式 `可用 − 欠款 = Σ流水.amount`；冻结额由任务状态镜像 |
| 信誉评分 | 90 天窗口 + 先验平滑（<5 单用先验、≥20 单用真实、中间线性混合），满分 120 |
| 推荐 | 六因子（类型偏好 / 报酬 / 信用 / 新鲜度 / 紧迫度 / 完成率）加权 + 多样性重排 |
| 冲突检测 | 只读判定：时间重叠且独占 → BLOCK；地点相邻 → WARN |
| 幂等 | 请求头 `Idempotency-Key` + `idem_record` 唯一索引 |
| 定时兜底 | 每分钟扫描：到期任务退回报酬、超时未确认自动结算 |
| 前后端词汇 | 内核 8 态 ↔ 前端 6 态，统一在 `dto/TaskWire` 翻译 |

---

## 六、文档索引

都在 `wsw-server/docs/`：

| 文件 | 内容 |
| --- | --- |
| `01-迭代四-任务与验收标准.md`（对应**附表4**） | 迭代三验收总结、17 项任务到人、6 条完成标准 |
| `02-附表5-测试用例设计与执行结果.md`（对应**附表5**） | 27 条白盒 + 12 条黑盒 + 24 个缺陷的成因与修正 |
| `06-迭代三回顾会议记录.md` | 增量演示记录、验收结论、遗留问题 |
| `03-关键模块详细设计.md` | 状态机、抢单时序、记账口径、推荐公式 |
| `04-部署图与开发环境.md` | 部署图、环境清单、9 条常见问题 |
| `05-接口清单.md` | 90 个接口一览 |
| `需求分析/` | 迭代一~三的需求文档、UML 图、任务书 |
| `docx/` | 附表 4 / 附表 5 / 会议记录的可提交 Word 版 |

---

## 七、常见问题

| 现象 | 原因 / 解决 |
| --- | --- |
| 启动报 `Access denied for user 'root'` | 没设 `DB_PASSWORD`，或密码错 |
| 启动报 `Unknown database 'wsw'` | 没导入 `sql/01-schema.sql` |
| 登录成功但页面数据是假的 | `VITE_USE_MOCK` 还是 `true`，改成 `false` 并重启 dev server |
| 前端 404 `页面不存在` | 后端没启动，或 `VITE_API_BASE_URL` 指错 |
| 上传图片报「服务器开小差了」 | 后端没重启（上传限制改过），或文件超过 8MB |
| 端口被占用 | 后端改 `server.port`，前端 Vite 会自动换端口 |

---

## 八、已知限制

1. 屋币是平台内虚拟货币，**不可充值、提现、转让**，也不对接真实支付。
2. 文件访问目前是静态直出（`/files/upload/**`），上线前需改为带鉴权的下载接口。
3. 忘记密码走「登录名 + 手机号」校验，没有接短信服务。
4. 评价字段已落库，但「好评率」展示本轮下线，留待下一迭代。
