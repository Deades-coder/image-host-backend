package com.yang.imagehostbackend.mapper;

import com.yang.imagehostbackend.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
* @author Decades
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2025-05-09 21:08:59
* @Entity com.yang.imagehostbackend.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {
    
    /**
     * 添加thumbCount列（如果不存在）
     */
    @Update("ALTER TABLE picture ADD COLUMN IF NOT EXISTS thumbCount BIGINT DEFAULT 0 NOT NULL COMMENT '点赞数量'")
    void addThumbCountColumnIfNotExists();
    
    /**
     * 增加图片点赞数
     * @param pictureId 图片ID
     * @return 影响行数
     */
    @Update("UPDATE picture SET thumbCount = thumbCount + 1 WHERE id = #{pictureId}")
    int incrementThumbCount(@Param("pictureId") Long pictureId);
    
    /**
     * 减少图片点赞数
     * @param pictureId 图片ID
     * @return 影响行数
     */
    @Update("UPDATE picture SET thumbCount = GREATEST(thumbCount - 1, 0) WHERE id = #{pictureId}")
    int decrementThumbCount(@Param("pictureId") Long pictureId);
}




