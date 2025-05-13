package com.yang.imagehostbackend.controller;

import com.yang.imagehostbackend.common.BaseResponse;
import com.yang.imagehostbackend.common.ResultUtils;
import com.yang.imagehostbackend.constant.SpaceUserPermissionConstant;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.yang.imagehostbackend.model.dto.thumb.DoThumbRequest;
import com.yang.imagehostbackend.model.entity.User;
import com.yang.imagehostbackend.service.ThumbService;
import com.yang.imagehostbackend.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @Author 小小星仔
 * @Create 2025-05-13 21:41
 */

@RestController
@RequestMapping("/thumb")
@Slf4j
public class ThumbController {

    @Resource
    private  ThumbService thumbService;

    @Resource
    private  UserService userService;
    
    /**
     * 点赞图片
     * 
     * @param doThumbRequest 点赞请求
     * @param request HTTP请求
     * @return 点赞结果
     */
    @PostMapping("/")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Boolean result = thumbService.doThumb(doThumbRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 取消点赞
     * 
     * @param doThumbRequest 取消点赞请求
     * @param request HTTP请求
     * @return 取消点赞结果
     */
    @DeleteMapping("/")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
           throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Boolean result = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 获取用户是否已点赞
     * 
     * @param pictureId 图片ID
     * @param request HTTP请求
     * @return 是否已点赞
     */
    @GetMapping("/status")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> hasThumb(@RequestParam("pictureId") Long pictureId, HttpServletRequest request) {
        if (pictureId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        try {
            // 获取当前登录用户
            User loginUser = userService.getLoginUser(request);
            Boolean hasThumb = thumbService.hasThumb(pictureId, loginUser.getId());
            return ResultUtils.success(hasThumb);
        } catch (Exception e) {
            //log.error("获取点赞状态失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"点赞失败");           }
    }
} 