package com.yang.imagehostbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量导入图片请求
 * @Author 小小星仔
 * @Create 2025-05-10 13:43
 */

@Data
public class PictureUploadByBatchRequest implements Serializable {

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 10;

    /**
     * 图片名称前缀
     */
    private String namePrefix;

    private static final long serialVersionUID = 1L;
}
