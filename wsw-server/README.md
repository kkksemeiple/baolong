# 暴龙万事屋 · 后端服务（wsw-server）

校园任务大厅平台的后端实现，对应前端工程 `../vue-wsw` 的 87 个接口。

- 技术栈：Spring Boot 3.2.5 / Java 17 / MyBatis-Plus 3.5.7 / MySQL 8.4 / JWT(HS256) / BCrypt
- 规模：101 个 Java 文件，90 个 REST 接口，22 张表，27 条白盒单元测试全部通过

---

## 一、5 分钟跑起来

```bash
# 1. 建库建表（Navicat 里执行，或命令行）
mysql -uroot -p < sql/01-schema.sql

# 2. 改数据库密码：src/main/resources/application.yml
#    spring.datasource.password: 你的密码

# 3. 启动（IDEA 里直接运行 WswApplication 也行）
$env:JAVA_HOME = 'D:\jdk'
mvn spring-boot:run
```

启动日志里出现 `演示数据播种完成，默认密码：abc12345` 就成功了。

**演示账号**

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | abc12345 | 超级管理员 |
| linxiaoman | abc12345 | 普通用户（已实名） |
| zhaolei | abc12345 | 普通用户（未实名） |
| chenyu | abc12345 | 普通用户（已实名） |

---

## 二、目录结构

```
wsw-server/
├── sql/
│   └── 01-schema.sql          # 22 张表，可重复执行
├── docs/                      # 迭代四交付文档
│   ├── 01-迭代四-任务与验收标准.md      # 附表 4
│   ├── 02-附表5-测试用例设计与执行结果.md
│   ├── 03-关键模块详细设计.md
│   ├── 04-部署图与开发环境.md
│   └── 05-接口清单.md
└── src/main/java/com/baolong/wsw/
    ├── algorithm/     # 纯函数算法：打分、评分、冲突、任务类型元数据
    ├── common/        # Result / ResultCode / BizException / 分页
    ├── config/        # 属性、Jackson、MyBatis-Plus、WebMvc、演示数据播种
    ├── controller/    # 7 个控制器，90 个接口
    ├── dto/           # 入参出参 + TaskWire（内核词汇 ↔ 前端词汇）
    ├── entity/        # 22 个实体
    ├── enums/         # 14 个枚举
    ├── mapper/        # 22 个 Mapper，关键路径手写 SQL
    ├── schedule/      # 定时任务：过期、超时自动确认
    ├── security/      # JWT、鉴权拦截器、幂等拦截器、登录上下文
    └── service/       # 11 个服务
```

---

## 三、核心设计一览

| 主题 | 做法 | 详见 |
| --- | --- | --- |
| 抢单并发 | `UPDATE ... WHERE version=? AND status='WAITING'` 原子 CAS + `task_claim.uk_task_id` 兜底 | `docs/03` 第三节 |
| 钱包记账 | 流水只记「可用余额」变动，恒等式 `available − debt = SUM(amount)` | `docs/03` 第四节 |
| 信誉评分 | 先验平滑（<5 单）→ 线性过渡（5~20 单）→ 真实数据（≥20 单），满分 120 | `docs/03` 第五节 |
| 推荐算法 | 候选池 200 条 + 六因子加权 + 多样性重排，新建用户走冷启动公式 | `docs/03` 第六节 |
| 冲突检测 | 只读无副作用，BLOCK / WARN 两级 | `docs/03` 第七节 |
| 幂等 | 拦截器 + `idem_record` 唯一索引，重复提交返回 40907 | `docs/03` 第八节 |
| 定时兜底 | 10 分钟轮询：到期过期、超时自动确认（替代 Redis 延迟队列） | `docs/03` 第九节 |

---

## 四、测试

```bash
# 白盒单元测试：4 组 27 条，不需要数据库
mvn test

# 黑盒接口测试：需要本机 MySQL（被 surefire 排除，手动运行）
mvn test -Dtest=ApiFlowIT
```

白盒覆盖：状态机迁移、推荐六因子、信誉评分三段式平滑、冲突检测两级判定。

---

## 五、与前端联调

| 约定 | 说明 |
| --- | --- |
| 响应体 | `{ "code": 0, "message": "ok", "data": ... }`，`code !== 0` 前端抛 `BizError` |
| 未登录 | 返回**真实的 HTTP 401**，前端据此清凭证并跳 `/login?redirect=` |
| 鉴权头 | `Authorization: Bearer <token>` |
| 幂等头 | 写请求带 `Idempotency-Key`，重复提交返回 40907 |
| 分页 | 列表用游标 `{ list, nextCursor, hasMore }`，管理端用页码 `{ list, total, page, size }` |
| 状态词汇 | 后端内核 `WAITING/IN_PROGRESS/TO_CONFIRM`，出参经 `TaskWire` 转成前端的 `OPEN/TAKEN/SUBMITTED` |

前端默认请求 `http://localhost:8080`（见 `vue-wsw/.env.development`），把 `VITE_USE_MOCK` 设为 `false` 即走真实后端。

---

## 六、已知限制

1. **忘记密码没有短信验证码**：课程项目未接入短信服务，改为「登录名 + 手机号匹配」校验，接入短信后替换 `AuthService.forgotPassword` 即可。
2. **图片存本地磁盘**（`./uploads`），未接对象存储；生产部署需改 `FileService`。
3. **没有上 Redis**：缓存、延迟队列都用数据库与定时任务替代，量级足够但并发上不去。
4. **黑盒集成测试未在无数据库环境执行**：需要本机 MySQL，见 `docs/02` 第三节。

---

## 七、成员分工（迭代四）

| 成员 | 负责模块 |
| --- | --- |
| 张艺博 | 数据库脚本、管理后台、部署与文档 |
| 张皓宁 | 工程骨架、鉴权、账号、推荐算法、定时任务、单元测试 |
| 李浩铭 | 任务模块、消息通知、议价举报 |
| 陈长洲 | 论坛模块、个人中心、冲突检测 |
| 赵柏善 | 钱包服务、信誉评分 |
