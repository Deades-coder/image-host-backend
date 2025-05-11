# AI 扩图任务 Kafka 异步处理实现

## 概述

本项目使用 Kafka 消息队列来管理阿里云 AI 扩图任务的异步处理，替代原有的前端轮询方案。这种方式可以减轻前端负担，提高系统的可靠性和可扩展性。

## 实现方案

1. 用户发起扩图请求后，后端调用阿里云 AI 接口创建扩图任务
2. 将任务信息保存到数据库，并发送消息到 Kafka 队列
3. Kafka 消费者定期检查任务状态，直到任务完成或失败
4. 前端可以随时查询任务状态，如果任务已完成，直接返回结果

## 主要组件

### 数据库表

- `out_painting_task`: 存储扩图任务信息，包括任务ID、图片ID、用户ID、任务状态和结果URL等

### 消息模型

- `OutPaintingTaskMessage`: 包含任务ID、图片ID、用户ID、创建时间、重试次数等信息

### 服务组件

- `OutPaintingTaskService`: 处理扩图任务的服务接口
- `OutPaintingTaskServiceImpl`: 实现扩图任务处理逻辑
- `KafkaProducerService`: 发送消息到Kafka队列
- `OutPaintingTaskConsumer`: 消费Kafka消息并处理扩图任务 (位于consumer包下)

## 代码结构

为了更好地遵循规范，我们采用了以下包结构：

```
src/main/java/com/yang/imagehostbackend/
├── api/                 # 外部API接口
├── config/              # 配置类
├── consumer/            # 消息消费者
│   └── OutPaintingTaskConsumer.java   # 扩图任务消费者
├── controller/          # 控制器
├── mapper/              # MyBatis映射器
├── model/               # 数据模型
│   ├── dto/             # 数据传输对象
│   │   └── picture/     # 图片相关DTO
│   │       └── OutPaintingTaskMessage.java  # 扩图任务消息
│   └── entity/          # 实体类
│       └── OutPaintingTask.java  # 扩图任务实体
├── service/             # 服务接口
│   ├── OutPaintingTaskService.java  # 扩图任务服务接口
│   ├── KafkaProducerService.java    # Kafka生产者服务接口
│   └── impl/            # 服务实现
│       ├── OutPaintingTaskServiceImpl.java  # 扩图任务服务实现
│       └── KafkaProducerServiceImpl.java    # Kafka生产者服务实现
```

## 流程说明

1. **创建任务**:
   - 用户通过 `/picture/out_painting/create_task` 接口发起扩图请求
   - 后端验证权限后调用阿里云AI接口创建任务
   - 将任务信息保存到数据库
   - 发送消息到Kafka队列

2. **任务轮询**:
   - Kafka消费者接收到消息后，查询阿里云任务状态
   - 如果任务完成或失败，更新数据库状态
   - 如果任务仍在进行中，使用指数退避策略重新发送消息到队列

3. **查询任务**:
   - 用户通过 `/picture/out_painting/get_task` 接口查询任务状态
   - 后端首先查询数据库，如果任务已完成，直接返回结果
   - 如果任务仍在进行中，查询阿里云最新状态并更新数据库

## 配置说明

### Kafka 配置

在 `application.yaml` 中配置:

```yaml
spring:
  kafka:
    bootstrap-servers: 192.168.21.132:9092
    producer:
      retries: 3
      batch-size: 16384
      buffer-memory: 33554432
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: 1
    consumer:
      auto-commit-interval: 1S
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring:
          json:
            trusted:
              packages: com.yang.imagehostbackend.model.dto.picture
    listener:
      concurrency: 5
      ack-mode: manual_immediate
      missing-topics-fatal: false

kafka:
  topics:
    outpainting-task: outpainting-task
```

## 部署步骤

1. 安装并启动 Kafka 服务
2. 执行 `src/main/resources/db/out_painting_task.sql` 创建数据库表
3. 修改 `application.yaml` 中的 Kafka 配置
4. 重启应用服务

## 优势

1. **可靠性**: 消息持久化，即使系统崩溃也能恢复任务
2. **可扩展性**: 可以轻松扩展消费者数量处理更多任务
3. **解耦**: 任务创建和状态检查解耦，提高系统稳定性
4. **减轻前端负担**: 前端不再需要频繁轮询，减少网络请求
5. **可监控**: 可以方便地监控任务处理状态和性能
6. **代码规范**: 按功能职责划分包结构，消费者位于独立的consumer包中 