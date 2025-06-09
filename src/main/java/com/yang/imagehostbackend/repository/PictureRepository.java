package com.yang.imagehostbackend.repository;

import com.yang.imagehostbackend.model.es.PictureDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 图片ES存储库
 */
@Repository
public interface PictureRepository extends ElasticsearchRepository<PictureDocument, Long> {
    
    /**
     * 根据更新时间范围和删除标记查询图片
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param isDelete 是否删除
     * @return 图片文档列表
     */
    List<PictureDocument> findByUpdateTimeBetweenAndIsDelete(Date startTime, Date endTime, Integer isDelete);
    
    /**
     * 根据空间ID查询图片
     * 
     * @param spaceId 空间ID
     * @return 图片文档列表
     */
    List<PictureDocument> findBySpaceIdAndIsDeleteOrderByCreateTimeDesc(Long spaceId, Integer isDelete);
    
    /**
     * 根据审核状态查询图片
     * 
     * @param reviewStatus 审核状态
     * @param isDelete 是否删除
     * @return 图片文档列表
     */
    List<PictureDocument> findByReviewStatusAndIsDelete(Integer reviewStatus, Integer isDelete);
    
    /**
     * 删除已标记为删除的图片
     * 
     * @param isDelete 删除标记
     */
    void deleteByIsDelete(Integer isDelete);
} 