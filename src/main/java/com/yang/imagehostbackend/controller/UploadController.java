package com.yang.imagehostbackend.controller;

import com.yang.imagehostbackend.model.dto.FileUploadMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.yang.imagehostbackend.config.RabbitMQConfig.FILE_UPLOAD_EXCHANGE;
import static com.yang.imagehostbackend.config.RabbitMQConfig.FILE_UPLOAD_ROUTING_KEY;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 21:05
 */
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${file.upload.temp-dir}")
    private String tempDir;

    /**
     * 上传文件，异步处理
     *
     * @param file 前端上传的文件
     * @return 任务 ID
     */
    @PostMapping("/file")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("上传文件不能为空");
        }

        try {
            // 确保临时目录存在
            File tempDirFile = new File(tempDir);
            if (!tempDirFile.exists()) {
                tempDirFile.mkdirs();
            }

            // 保存文件到临时目录
            String originalFilename = file.getOriginalFilename();
            String tempFileName = UUID.randomUUID() + "-" + originalFilename;
            File tempFile = new File(tempDir, tempFileName);
            file.transferTo(tempFile);

            // 构造 COS key
            String cosKey = "uploads/" + originalFilename;

            // 创建消息
            FileUploadMessage message = new FileUploadMessage();
            message.setTaskId(UUID.randomUUID().toString());
            message.setFilePath(tempFile.getAbsolutePath());
            message.setCosKey(cosKey);
            message.setOriginalFilename(originalFilename);

            // 发送消息到 RabbitMQ
            rabbitTemplate.convertAndSend(FILE_UPLOAD_EXCHANGE, FILE_UPLOAD_ROUTING_KEY, message);

            // 返回任务 ID
            return ResponseEntity.ok("文件上传任务已提交，任务 ID: " + message.getTaskId());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件处理失败: " + e.getMessage());
        }
    }

    /**
     * 测试端点
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("文件上传控制器正常运行");
    }
}