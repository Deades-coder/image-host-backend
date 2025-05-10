package com.yang.imagehostbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author 小小星仔
 * @Create 2025-05-10 12:00
 */
@Data
public class PictureReviewRequest implements Serializable {
    private Long id;
    private Integer reviewStatus;
    private String reviewMessage;
    private static final long serialVersionUID = 1L;
}
