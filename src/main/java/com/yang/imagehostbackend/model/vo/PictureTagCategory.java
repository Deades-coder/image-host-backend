package com.yang.imagehostbackend.model.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author 小小星仔
 * @Create 2025-05-10 11:07
 */
@Data
public class PictureTagCategory {

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 分类列表
     */
    private List<String> categoryList;
}