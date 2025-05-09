package com.yang.imagehostbackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户
 * @Author 小小星仔
 * @Create 2025-05-09 20:04
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}