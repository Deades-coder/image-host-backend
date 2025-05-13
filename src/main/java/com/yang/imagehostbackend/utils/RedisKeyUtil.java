package com.yang.imagehostbackend.utils;

import com.yang.imagehostbackend.constant.ThumbConstant;

/**
 * @Author 小小星仔
 * @Create 2025-05-13 17:00
 */
public class RedisKeyUtil {

    public static String getUserThumbKey(Long userId) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取 临时点赞记录 key
     */
    public static String getTempThumbKey(String time) {
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);
    }
}
