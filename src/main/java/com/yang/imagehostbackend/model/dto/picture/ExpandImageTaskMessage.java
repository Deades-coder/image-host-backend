package com.yang.imagehostbackend.model.dto.picture;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author 小小星仔
 * @Create 2025-05-11 22:42
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpandImageTaskMessage implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 任务状态
     */
    private String taskStatus;

    /**
     * 输出图像URL
     */
    private String outputImageUrl;

    private static final long serialVersionUID = 1L;
}
