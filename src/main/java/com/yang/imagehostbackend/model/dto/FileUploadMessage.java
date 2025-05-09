package com.yang.imagehostbackend.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传消息实体，用于 RabbitMQ 消息传递
 * @Author 小小星仔
 * @Create 2025-05-09 21:29
 */
@Data
public class FileUploadMessage implements Serializable {
    private String taskId; // 任务 ID
    private String filePath; // 临时文件路径
    private String cosKey; // COS 中的文件 key
    private String originalFilename; // 原始文件名
}
