package com.yang.imagehostbackend.service;

import com.yang.imagehostbackend.model.dto.picture.PictureSearchRequest;
import com.yang.imagehostbackend.model.vo.PictureVO;
import org.springframework.data.domain.Page;

/**
 * 图片搜索服务
 */
public interface PictureSearchService {

    /**
     * 搜索图片
     *
     * @param searchRequest 搜索请求
     * @return 图片分页结果
     */
    Page<PictureVO> searchPictures(PictureSearchRequest searchRequest);
    
    /**
     * 初始化ES索引
     * 全量同步MySQL数据到ES
     */
    void initPictureIndex();
    
    /**
     * 增量同步图片数据到ES
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 同步数量
     */
    int syncPictureIncremental(long startTime, long endTime);
    
    /**
     * 将单个图片同步到ES
     *
     * @param pictureId 图片ID
     * @return 是否成功
     */
    boolean syncPictureById(Long pictureId);
    
    /**
     * 从ES中删除图片
     *
     * @param pictureId 图片ID
     * @return 是否成功
     */
    boolean deletePictureFromES(Long pictureId);
} 