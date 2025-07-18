package com.yang.imagehostbackend.service;

import com.yang.imagehostbackend.model.dto.thumb.DoThumbRequest;
import com.yang.imagehostbackend.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;

/**
* @author Decades
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-05-13 16:10:01
*/

public interface ThumbService extends IService<Thumb> {
    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);


    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 判断用户是否已点赞图片
     * @param pictureId 图片ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    Boolean hasThumb(Long pictureId, Long userId);
}
