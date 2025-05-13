package com.yang.imagehostbackend.service.impl;

import com.yang.imagehostbackend.mapper.PictureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 图片服务扩展，用于包装PictureMapper操作并添加日志
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PictureServiceExtension {
    
    private final PictureMapper pictureMapper;
    
    /**
     * 添加thumbCount列（如果不存在）
     */
    public void addThumbCountColumnIfNotExists() {
        log.info("开始执行SQL: 添加thumbCount列");
        try {
            pictureMapper.addThumbCountColumnIfNotExists();
            log.info("SQL执行成功: 添加thumbCount列");
        } catch (Exception e) {
            log.error("SQL执行失败: 添加thumbCount列, 错误: {}, 堆栈: {}", 
                    e.getMessage(), e.getStackTrace());
            throw e;
        }
    }
    
    /**
     * 增加图片点赞数
     * @param pictureId 图片ID
     * @return 影响行数
     */
    public int incrementThumbCount(Long pictureId) {
        log.info("开始执行SQL: 增加图片点赞数, pictureId={}", pictureId);
        try {
            int rows = pictureMapper.incrementThumbCount(pictureId);
            log.info("SQL执行成功: 增加图片点赞数, pictureId={}, 影响行数={}", pictureId, rows);
            return rows;
        } catch (Exception e) {
            log.error("SQL执行失败: 增加图片点赞数, pictureId={}, 错误: {}, 堆栈: {}", 
                    pictureId, e.getMessage(), e.getStackTrace());
            throw e;
        }
    }
    
    /**
     * 减少图片点赞数
     * @param pictureId 图片ID
     * @return 影响行数
     */
    public int decrementThumbCount(Long pictureId) {
        log.info("开始执行SQL: 减少图片点赞数, pictureId={}", pictureId);
        try {
            int rows = pictureMapper.decrementThumbCount(pictureId);
            log.info("SQL执行成功: 减少图片点赞数, pictureId={}, 影响行数={}", pictureId, rows);
            return rows;
        } catch (Exception e) {
            log.error("SQL执行失败: 减少图片点赞数, pictureId={}, 错误: {}, 堆栈: {}", 
                    pictureId, e.getMessage(), e.getStackTrace());
            throw e;
        }
    }
} 