package com.yang.imagehostbackend.model.dto.picture;

import lombok.Data;

/**
 * 颜色搜索图片请求
 * @Author 小小星仔
 * @Create 2025-05-11 12:44
 */
@Data
public class SearchPictureByColorRequest {
    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
