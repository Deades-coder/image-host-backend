-- 创建扩图任务表
CREATE TABLE IF NOT EXISTS `expend_image_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(64) NOT NULL COMMENT '阿里云任务ID',
  `picture_id` bigint NOT NULL COMMENT '原图片ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `task_status` varchar(32) NOT NULL COMMENT '任务状态',
  `output_image_url` varchar(1024) DEFAULT NULL COMMENT '结果图片URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_task_id` (`task_id`),
  KEY `idx_picture_id` (`picture_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扩图任务表'; 