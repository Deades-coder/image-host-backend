package com.yang.imagehostbackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 19:25
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;
}
