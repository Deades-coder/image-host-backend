package com.yang.imagehostbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片搜索请求
 */
@Data
public class PictureSearchRequest implements Serializable {
    
    /**
     * 搜索关键词
     */
    private String keyword;
    
    /**
     * 分类
     */
    private String category;
    
    /**
     * 标签列表
     */
    private List<String> tags;
    
    /**
     * 图片格式
     */
    private String picFormat;
    
    /**
     * 图片颜色
     */
    private String picColor;
    
    /**
     * 最小宽度
     */
    private Integer minWidth;
    
    /**
     * 最大宽度
     */
    private Integer maxWidth;
    
    /**
     * 最小高度
     */
    private Integer minHeight;
    
    /**
     * 最大高度
     */
    private Integer maxHeight;
    
    /**
     * 空间ID
     */
    private Long spaceId;
    
    /**
     * 创建者ID
     */
    private Long userId;
    
    /**
     * 审核状态
     */
    private Integer reviewStatus;
    
    /**
     * 排序字段
     */
    private String sortField;
    
    /**
     * 排序方向（ASC/DESC）
     */
    private String sortOrder;
    
    /**
     * 页码，从1开始
     */
    private Integer pageNum = 1;
    
    /**
     * 每页大小
     */
    private Integer pageSize = 10;
    
    private static final long serialVersionUID = 1L;
} 