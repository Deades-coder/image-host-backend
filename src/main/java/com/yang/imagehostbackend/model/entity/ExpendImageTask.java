package com.yang.imagehostbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 扩图任务表
 * @TableName expend_image_task
 */
@TableName(value ="expend_image_task")
@Data
public class ExpendImageTask implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 阿里云任务ID
     */
    private String task_id;

    /**
     * 原图片ID
     */
    private Long picture_id;

    /**
     * 用户ID
     */
    private Long user_id;

    /**
     * 任务状态
     */
    private String task_status;

    /**
     * 结果图片URL
     */
    private String output_image_url;

    /**
     * 创建时间
     */
    private Date create_time;

    /**
     * 更新时间
     */
    private Date update_time;

    /**
     * 是否删除
     */
    private Integer is_delete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}