# 云端素材库平台

本项目是一个全面的云端素材库平台，旨在实现高效的图片管理、用户协作和高级图像处理。它为个人和团队提供了一套强大的功能，用于存储、组织和交互其视觉资产。该平台以性能和可扩展性为重点进行构建。

## 功能特性

### 用户模块
- 用户登录
- 用户注册
- 用户注销
- 用户权限控制 (基于 Sa-Token)
- 用户管理

### 图片模块
- 图片上传与创建
- 图片管理
- 图片信息编辑
- 图片查看与搜索
- 图片详情
- 图片下载

### 用户传图模块
- 用户上传与创建图片
- 图片审核
- 图片导入功能
- URL 导入
- 批量抓取图片

### 图片优化
- **性能优化!**
- 图片查询优化 (使用 Redis 和 Caffeine 实现多级缓存，并采用延迟双删策略保证数据一致性)
- 图片上传优化 (压缩)
- 图片加载优化 (缩略图)
- AI 图像处理异步化：使用 Kafka 作为消息队列，处理 AI 图像扩展（如扩图）等耗时任务：
  - 任务状态轮询：Kafka 消费者异步轮询 AI 任务处理状态。
  - 失败重试：内置指数退避策略进行任务重试。
  - 具体流程（以扩图为例）：
    1. 用户发起扩图请求后，后端调用阿里云 AI 接口创建扩图任务。
    2. 将任务信息保存到数据库，并发送消息到 Kafka 队列。
    3. Kafka 消费者定期检查任务状态，直到任务完成或失败。
    4. 前端可以随时查询任务状态，如果任务已完成，直接返回结果。

### 空间模块
- 空间管理
- 用户开通私有空间
- 私有空间权限控制
- 空间级别与存储限额控制
- **消息队列:** RabbitMQ, Apache Kafka (用于 AI 图像处理任务的异步化、状态轮询和重试)
- **认证与授权:** Sa-Token
- **API 文档:** Knife4j

### 图片功能扩展
- 高级图片搜索
  - 基本属性搜索
  - 以图搜图
  - 颜色搜索
- 图片分享
- 图片批量编辑
- 图片点赞与计数 (Redis 缓存 + RabbitMQ 异步处理 + 定时任务对账保证最终一致性)

### 图片编辑能力
- 基础图片编辑
- AI 图片编辑 (集成阿里云 AI 等服务)

### 团队共享空间
- 创建团队共享空间
- 成员邀请和空间成员管理
- 空间权限控制 (使用 Sa-Token)
- 空间数据管理 (使用 ShardingSphere 进行分库分表)

### 图片协同编辑
- 协同编辑方案 (使用 WebSocket)
- 实时协同编辑实战

## 技术栈

- **后端:** Java, Spring Boot 2.7.6
- **数据库:** MySQL, MyBatis-Plus
- **缓存:** Redis, Caffeine (多级缓存)
- **消息队列:** RabbitMQ (用于处理点赞等业务的异步消息及数据对账、确保最终一致性), Apache Kafka (用于 AI 图像处理任务的异步化、状态轮询和重试)
- **认证与授权:** Sa-Token
- **API 文档:** Knife4j
- **对象存储:** 腾讯云 COS (可配置)
- **AI 服务:** 阿里云 AI (可配置用于 AI 图片编辑)
- **数据分片:** Apache ShardingSphere
- **实时通信:** WebSocket
- **并发处理:** LMAX Disruptor (高性能队列)
- **工具库:** Hutool, Lombok
- **构建工具:** Maven

## 快速开始

### 环境要求

- Java 17
- Maven
- MySQL
- Redis
- RabbitMQ
- Kafka 


### 安装步骤

1.  **克隆仓库:**
    ```bash
    git clone <your-repository-url>
    cd image-host-backend
    ```
2.  **配置 `application.yaml`:**
    - 更新数据库连接信息 (`spring.datasource`)。
    - 如果 Redis, RabbitMQ, Kafka 运行在不同的主机/端口或需要认证，请配置其连接详情。
    - 设置腾讯云 COS 配置 (`cos.client`)。
    - 提供阿里云 AI API 密钥 (`aliYunAi.apiKey`)。
    - 根据需要调整 ShardingSphere 数据源配置。
3.  **构建项目:**
    ```bash
    mvn clean package
    ```

### 运行应用

```bash
java -jar target/image-host-backend-0.0.1-SNAPSHOT.jar
```
应用将运行在 `http://localhost:8123/api` (或 `application.yaml` 中配置的地址)。

## 项目结构

```
image-host-backend/
├── .git/
├── .idea/
├── sql/                            # SQL 脚本 (例如，初始化表结构)
├── src/
│   ├── main/
│   │   ├── java/com/yang/imagehostbackend/
│   │   │   ├── ImageHostBackendApplication.java  # Spring Boot 主应用类
│   │   │   ├── annotation/             # 自定义注解
│   │   │   ├── aop/                    # 面向切面编程组件
│   │   │   ├── api/                    # 外部 API 集成
│   │   │   ├── common/                 # 通用工具类、枚举、常量
│   │   │   ├── config/                 # 应用配置 (Spring, MyBatis 等)
│   │   │   ├── consumer/               # 消息队列消费者 (Kafka, RabbitMQ)
│   │   │   ├── constant/               # 应用常量
│   │   │   ├── controller/             # Spring MVC 控制器 (REST API)
│   │   │   ├── exception/              # 自定义异常处理器
│   │   │   ├── job/                    # 定时任务
│   │   │   ├── manager/                # 业务逻辑管理器 (通常用于编排服务)
│   │   │   ├── mapper/                 # MyBatis Mapper (数据访问对象)
│   │   │   ├── model/                  # 数据模型 (实体, DTO, 枚举, VO)
│   │   │   ├── producer/               # 消息队列生产者
│   │   │   ├── service/                # 服务层 (业务逻辑)
│   │   │   ├── utils/                  # 工具类
│   │   │   └── websocket/              # WebSocket 处理器
│   │   └── resources/
│   │       ├── application.yaml        # 主应用配置文件
│   │       ├── static/                 # 静态资源 (如有，通常用于前端)
│   │       └── templates/              # 服务端模板 (如有)
│   └── test/                           # 单元测试和集成测试
├── target/                           # 编译后的字节码和打包的 JAR 文件
├── .gitignore
├── pom.xml                           # Maven 项目配置文件
└── README.md                         # 本文件
```

## 性能优化

本项目高度重视性能。关键的优化点包括：

-   **多级缓存:** 同时利用本地缓存 (Caffeine) 和分布式缓存 (Redis)，显著加快数据检索速度，特别是针对频繁访问的图片数据和用户会话。
-   **高可用点赞系统:** 点赞功能采用 Redis 缓存快速响应前端，通过 RabbitMQ 将点赞/取消点赞操作异步化处理并持久化到数据库。结合定时任务对 Redis 与数据库进行数据对账，确保点赞数据的高可用性和最终一致性。
-   **图片上传优化:** 实现图片压缩技术，在存储前减小文件大小，节省带宽和存储成本，并提高上传/下载速度。
-   **图片加载优化:** 生成并提供缩略图，以加快图片预览和相册加载速度，减少初始视图传输的数据量。
-   **异步处理:**
    -   利用消息队列 (RabbitMQ 用于点赞等业务，Kafka 用于 AI 图像处理任务) 和 Spring 的 `@Async` 注解进行后台任务处理（如图片处理、通知、分析、AI 图像生成等），避免阻塞用户请求。
    -   Kafka 被用于 AI 图像扩展（如扩图）任务的异步提交、状态轮询 (polling) 和基于指数退避的自动重试机制。
-   **缓存一致性:** 采用延迟双删策略来保证数据库与缓存 (Redis, Caffeine) 之间的数据一致性，特别是在图片信息更新或删除时。
-   **数据库分片:** 针对 `picture` 表使用 ShardingSphere，根据 `spaceId` 将数据分布到多个数据库实例/表中。