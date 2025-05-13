package com.yang.imagehostbackend.model.enums;

import lombok.Getter;

/**
 * Lua脚本状态枚举
 * @Author 小小星仔
 * @Create 2025-05-13 21:12
 */
@Getter
public enum LuaStatusEnum {
    // 成功
    SUCCESS(1L),
    // 失败
    FAIL(-1L),
    ;

    private final long value;

    LuaStatusEnum(long value) {
        this.value = value;
    }

}

