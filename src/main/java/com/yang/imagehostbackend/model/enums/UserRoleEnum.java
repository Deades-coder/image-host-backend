package com.yang.imagehostbackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.Arrays;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 18:55
 */
@Getter
public enum UserRoleEnum {
    USER("用户","user"),
    ADMIN("管理员","admin");
    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
    public static UserRoleEnum getEnumByValue(String value) {
        if(ObjUtil.isEmpty(value)) return null;
        return Arrays.stream(values()).filter(role -> value.equals(role.getValue())).findFirst().orElse(null);
    }
}
