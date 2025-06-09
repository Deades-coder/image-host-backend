package com.yang.imagehostbackend.controller;

import com.yang.imagehostbackend.common.BaseResponse;
import com.yang.imagehostbackend.common.ResultUtils;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.model.dto.picture.PictureSearchRequest;
import com.yang.imagehostbackend.model.vo.PictureVO;
import com.yang.imagehostbackend.service.PictureSearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 图片搜索接口
 * @Author 小小星仔
 * @Create 2025-06-09 22:17
 */
@RestController
@RequestMapping("/picture/search")
@Api(tags = "图片搜索接口")
@Slf4j
public class PictureSearchController {

    @Resource
    private PictureSearchService pictureSearchService;

    /**
     * 高级搜索图片
     *
     * @param searchRequest 搜索请求
     * @return 搜索结果
     */
    @PostMapping("/advanced")
    @ApiOperation(value = "高级搜索图片", notes = "支持多条件组合搜索")
    public BaseResponse<Page<PictureVO>> searchPictures(@RequestBody PictureSearchRequest searchRequest) {
        if (searchRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Page<PictureVO> pictureVOPage = pictureSearchService.searchPictures(searchRequest);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 简单搜索图片
     *
     * @param keyword 关键词
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 搜索结果
     */
    @GetMapping("/simple")
    @ApiOperation(value = "简单搜索图片", notes = "根据关键词搜索图片")
    public BaseResponse<Page<PictureVO>> searchPicturesByKeyword(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PictureSearchRequest searchRequest = new PictureSearchRequest();
        searchRequest.setKeyword(keyword);
        searchRequest.setPageNum(pageNum);
        searchRequest.setPageSize(pageSize);
        // 默认只搜索已审核通过的图片
        searchRequest.setReviewStatus(1);
        Page<PictureVO> pictureVOPage = pictureSearchService.searchPictures(searchRequest);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 手动触发全量同步
     * 
     * @return 同步结果
     */
    @PostMapping("/sync/full")
    @ApiOperation(value = "手动触发全量同步", notes = "将MySQL中的所有图片数据同步到Elasticsearch")
    public BaseResponse<Boolean> syncFull() {
        // 这里可以添加权限控制，仅允许管理员操作
        pictureSearchService.initPictureIndex();
        return ResultUtils.success(true);
    }

    /**
     * 同步单个图片到ES
     *
     * @param pictureId 图片ID
     * @return 同步结果
     */
    @PostMapping("/sync/single/{pictureId}")
    @ApiOperation(value = "同步单个图片", notes = "将指定ID的图片同步到Elasticsearch")
    public BaseResponse<Boolean> syncSingle(@PathVariable Long pictureId) {
        if (pictureId == null || pictureId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = pictureSearchService.syncPictureById(pictureId);
        return ResultUtils.success(result);
    }
} 