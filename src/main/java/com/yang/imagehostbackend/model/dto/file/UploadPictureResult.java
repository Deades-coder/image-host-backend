package com.yang.imagehostbackend.model.dto.file;

import lombok.Data;

/**
 * 上传图片的结果
 * @Author 小小星仔
 * @Create 2025-05-09 22:27
 */
@Data
public class UploadPictureResult {

    /**
     * 图片地址
     */
    private String url;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private int picWidth;

    /**
     * 图片高度
     */
    private int picHeight;

    /**
     * 图片宽高比
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;


    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

}