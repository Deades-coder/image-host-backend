package com.yang.imagehostbackend.model.es;

import com.yang.imagehostbackend.model.entity.Picture;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 图片搜索文档对象
 */
@Data
@Document(indexName = "#{@environment.getProperty('elasticsearch.index.picture-index')}")
public class PictureDocument implements Serializable {
    
    @Id
    private Long id;

    /**
     * 图片 url
     */
    @Field(type = FieldType.Keyword)
    private String url;

    /**
     * 图片名称
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    /**
     * 简介
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String introduction;

    /**
     * 分类
     */
    @Field(type = FieldType.Keyword)
    private String category;

    /**
     * 标签（JSON 数组）- 存储为解析后的列表
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private List<String> tags;

    /**
     * 图片体积
     */
    @Field(type = FieldType.Long)
    private Long picSize;

    /**
     * 图片宽度
     */
    @Field(type = FieldType.Integer)
    private Integer picWidth;

    /**
     * 图片高度
     */
    @Field(type = FieldType.Integer)
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    @Field(type = FieldType.Double)
    private Double picScale;

    /**
     * 图片格式
     */
    @Field(type = FieldType.Keyword)
    private String picFormat;

    /**
     * 创建用户 id
     */
    @Field(type = FieldType.Long)
    private Long userId;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Date createTime;

    /**
     * 编辑时间
     */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Date editTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Date updateTime;

    /**
     * 审核状态：0-待审核; 1-通过; 2-拒绝
     */
    @Field(type = FieldType.Integer)
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String reviewMessage;

    /**
     * 空间 id
     */
    @Field(type = FieldType.Long)
    private Long spaceId;

    /**
     * 图片主色调
     */
    @Field(type = FieldType.Keyword)
    private String picColor;

    /**
     * 点赞数
     */
    @Field(type = FieldType.Long)
    private Long thumbCount;

    /**
     * 缩略图URL
     */
    @Field(type = FieldType.Keyword)
    private String thumbnailUrl;
    
    /**
     * 是否删除
     */
    @Field(type = FieldType.Integer)
    private Integer isDelete;

    /**
     * 将数据库实体转换为ES文档
     */
    public static PictureDocument fromEntity(Picture picture) {
        if (picture == null) {
            return null;
        }
        
        PictureDocument document = new PictureDocument();
        document.setId(picture.getId());
        document.setUrl(picture.getUrl());
        document.setName(picture.getName());
        document.setIntroduction(picture.getIntroduction());
        document.setCategory(picture.getCategory());
        
        // 处理标签字段 - 假设tags字段在数据库中是JSON字符串
        if (picture.getTags() != null && !picture.getTags().isEmpty()) {
            try {
                List<String> tagList = cn.hutool.json.JSONUtil.toList(picture.getTags(), String.class);
                document.setTags(tagList);
            } catch (Exception e) {
                // 解析失败时保持为null
            }
        }
        
        document.setPicSize(picture.getPicSize());
        document.setPicWidth(picture.getPicWidth());
        document.setPicHeight(picture.getPicHeight());
        document.setPicScale(picture.getPicScale());
        document.setPicFormat(picture.getPicFormat());
        document.setUserId(picture.getUserId());
        document.setCreateTime(picture.getCreateTime());
        document.setEditTime(picture.getEditTime());
        document.setUpdateTime(picture.getUpdateTime());
        document.setReviewStatus(picture.getReviewStatus());
        document.setReviewMessage(picture.getReviewMessage());
        document.setSpaceId(picture.getSpaceId());
        document.setPicColor(picture.getPicColor());
        document.setThumbCount(picture.getThumbCount());
        document.setThumbnailUrl(picture.getThumbnailUrl());
        document.setIsDelete(picture.getIsDelete());
        
        return document;
    }

    private static final long serialVersionUID = 1L;
} 