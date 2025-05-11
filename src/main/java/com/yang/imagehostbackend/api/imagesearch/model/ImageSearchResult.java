package com.yang.imagehostbackend.api.imagesearch.model;

import lombok.Data;

/**
 * @Author 小小星仔
 * @Create 2025-05-11 10:56
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;
}
