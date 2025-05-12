package com.yang.imagehostbackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间用户查询请求
 * @Author 小小星仔
 * @Create 2025-05-12 21:51
 */
@Data
public class SpaceUserQueryRequest implements Serializable {
    /**
     * ID
     */
    private Long id;

    /**
     * 空间 ID
     */
    private Long spaceId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;

}
