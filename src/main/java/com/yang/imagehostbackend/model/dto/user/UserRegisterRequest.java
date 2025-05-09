package com.yang.imagehostbackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册
 * @Author 小小星仔
 * @Create 2025-05-09 18:59
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;
}
