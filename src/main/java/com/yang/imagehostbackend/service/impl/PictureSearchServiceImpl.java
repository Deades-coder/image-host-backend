package com.yang.imagehostbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yang.imagehostbackend.mapper.PictureMapper;
import com.yang.imagehostbackend.model.dto.picture.PictureSearchRequest;
import com.yang.imagehostbackend.model.entity.Picture;
import com.yang.imagehostbackend.model.es.PictureDocument;
import com.yang.imagehostbackend.model.vo.PictureVO;
import com.yang.imagehostbackend.repository.PictureRepository;
import com.yang.imagehostbackend.service.PictureSearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图片搜索服务实现
 * @Author 小小星仔
 * @Create 2025-06-09 22:14
 */
@Service
@Slf4j
public class PictureSearchServiceImpl implements PictureSearchService {

    @Resource
    private PictureRepository pictureRepository;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Value("${elasticsearch.index.picture-index}")
    private String pictureIndexName;

    @Value("${elasticsearch.sync.batch-size:100}")
    private int batchSize;

    @Override
    public org.springframework.data.domain.Page<PictureVO> searchPictures(PictureSearchRequest searchRequest) {
        if (searchRequest == null) {
            throw new IllegalArgumentException("搜索参数不能为空");
        }

        // 构建查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // 关键词搜索（多字段匹配）
        if (StrUtil.isNotBlank(searchRequest.getKeyword())) {
            boolQueryBuilder.must(
                    QueryBuilders.multiMatchQuery(searchRequest.getKeyword(), 
                            "name", "introduction", "tags")
            );
        }

        // 分类筛选
        if (StrUtil.isNotBlank(searchRequest.getCategory())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category", searchRequest.getCategory()));
        }

        // 标签筛选
        if (CollUtil.isNotEmpty(searchRequest.getTags())) {
            for (String tag : searchRequest.getTags()) {
                boolQueryBuilder.filter(QueryBuilders.matchQuery("tags", tag));
            }
        }

        // 图片格式筛选
        if (StrUtil.isNotBlank(searchRequest.getPicFormat())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("picFormat", searchRequest.getPicFormat()));
        }

        // 图片颜色筛选
        if (StrUtil.isNotBlank(searchRequest.getPicColor())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("picColor", searchRequest.getPicColor()));
        }

        // 宽度范围筛选
        if (searchRequest.getMinWidth() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("picWidth").gte(searchRequest.getMinWidth()));
        }
        if (searchRequest.getMaxWidth() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("picWidth").lte(searchRequest.getMaxWidth()));
        }

        // 高度范围筛选
        if (searchRequest.getMinHeight() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("picHeight").gte(searchRequest.getMinHeight()));
        }
        if (searchRequest.getMaxHeight() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("picHeight").lte(searchRequest.getMaxHeight()));
        }

        // 空间ID筛选
        if (searchRequest.getSpaceId() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("spaceId", searchRequest.getSpaceId()));
        }

        // 用户ID筛选
        if (searchRequest.getUserId() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", searchRequest.getUserId()));
        }

        // 审核状态筛选
        if (searchRequest.getReviewStatus() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("reviewStatus", searchRequest.getReviewStatus()));
        }

        // 只搜索未删除的图片
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));

        // 构建查询
        NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(PageRequest.of(searchRequest.getPageNum() - 1, searchRequest.getPageSize()));

        // 添加排序
        if (StrUtil.isNotBlank(searchRequest.getSortField())) {
            SortOrder sortOrder = "DESC".equalsIgnoreCase(searchRequest.getSortOrder()) 
                    ? SortOrder.DESC : SortOrder.ASC;
            searchQueryBuilder.withSort(SortBuilders.fieldSort(searchRequest.getSortField()).order(sortOrder));
        } else {
            // 默认按创建时间降序
            searchQueryBuilder.withSort(SortBuilders.fieldSort("createTime").order(SortOrder.DESC));
        }

        // 执行搜索
        SearchHits<PictureDocument> searchHits = elasticsearchRestTemplate.search(
                searchQueryBuilder.build(),
                PictureDocument.class,
                IndexCoordinates.of(pictureIndexName)
        );

        // 转换结果
        List<PictureVO> pictureVOList = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 构建分页结果
        return new org.springframework.data.domain.PageImpl<>(
                pictureVOList,
                PageRequest.of(searchRequest.getPageNum() - 1, searchRequest.getPageSize()),
                searchHits.getTotalHits()
        );
    }

    @Override
    public void initPictureIndex() {
        // 清空索引
        try {
            if (elasticsearchRestTemplate.indexOps(PictureDocument.class).exists()) {
                elasticsearchRestTemplate.indexOps(PictureDocument.class).delete();
            }
            elasticsearchRestTemplate.indexOps(PictureDocument.class).create();
            log.info("图片索引初始化成功");
        } catch (Exception e) {
            log.error("图片索引初始化失败", e);
            throw new RuntimeException("初始化ES索引失败", e);
        }

        // 全量同步数据
        long total = 0;
        long processedCount = 0;
        long pageNum = 1;
        long pageSize = batchSize;

        try {
            // 获取总记录数
            total = pictureMapper.selectCount(new LambdaQueryWrapper<>());
            log.info("开始全量同步图片数据，总数: {}", total);

            while (processedCount < total) {
                Page<Picture> page = new Page<>(pageNum, pageSize);
                Page<Picture> picturePage = pictureMapper.selectPage(page, new LambdaQueryWrapper<Picture>()
                        .orderByAsc(Picture::getId));

                List<Picture> records = picturePage.getRecords();
                if (CollUtil.isEmpty(records)) {
                    break;
                }

                List<PictureDocument> documents = records.stream()
                        .map(PictureDocument::fromEntity)
                        .collect(Collectors.toList());

                pictureRepository.saveAll(documents);

                processedCount += records.size();
                pageNum++;
                log.info("已同步 {}/{} 条图片数据", processedCount, total);
            }

            log.info("图片数据全量同步完成，共同步 {} 条记录", processedCount);
        } catch (Exception e) {
            log.error("全量同步图片数据失败", e);
            throw new RuntimeException("全量同步图片数据失败", e);
        }
    }

    @Override
    public int syncPictureIncremental(long startTime, long endTime) {
        Date start = new Date(startTime);
        Date end = new Date(endTime);
        log.info("开始增量同步图片数据，时间范围: {} - {}", 
                DateUtil.formatDateTime(start), DateUtil.formatDateTime(end));

        int count = 0;
        try {
            // 查询时间范围内更新的图片
            LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<Picture>()
                    .between(Picture::getUpdateTime, start, end);
            
            List<Picture> pictures = pictureMapper.selectList(queryWrapper);
            if (CollUtil.isEmpty(pictures)) {
                return 0;
            }

            // 批量处理
            List<PictureDocument> documents = new ArrayList<>();
            for (Picture picture : pictures) {
                PictureDocument document = PictureDocument.fromEntity(picture);
                documents.add(document);
                
                // 如果已删除，标记为删除
                if (picture.getIsDelete() == 1) {
                    pictureRepository.deleteById(picture.getId());
                }
            }

            // 保存未删除的文档
            List<PictureDocument> toSave = documents.stream()
                    .filter(doc -> doc.getIsDelete() == 0)
                    .collect(Collectors.toList());
            
            if (!toSave.isEmpty()) {
                pictureRepository.saveAll(toSave);
            }
            
            count = pictures.size();
            log.info("增量同步完成，共同步 {} 条记录", count);
        } catch (Exception e) {
            log.error("增量同步图片数据失败", e);
            throw new RuntimeException("增量同步图片数据失败", e);
        }
        
        return count;
    }

    @Override
    public boolean syncPictureById(Long pictureId) {
        if (pictureId == null) {
            return false;
        }

        try {
            Picture picture = pictureMapper.selectById(pictureId);
            if (picture == null) {
                return false;
            }

            // 如果已删除，则从ES中删除
            if (picture.getIsDelete() == 1) {
                pictureRepository.deleteById(pictureId);
                return true;
            }

            // 否则更新或插入
            PictureDocument document = PictureDocument.fromEntity(picture);
            pictureRepository.save(document);
            return true;
        } catch (Exception e) {
            log.error("同步单个图片数据失败，pictureId={}", pictureId, e);
            return false;
        }
    }

    @Override
    public boolean deletePictureFromES(Long pictureId) {
        if (pictureId == null) {
            return false;
        }

        try {
            pictureRepository.deleteById(pictureId);
            return true;
        } catch (Exception e) {
            log.error("从ES中删除图片数据失败，pictureId={}", pictureId, e);
            return false;
        }
    }

    /**
     * 将ES文档转换为VO对象
     */
    private PictureVO convertToVO(PictureDocument document) {
        if (document == null) {
            return null;
        }

        PictureVO vo = new PictureVO();
        BeanUtils.copyProperties(document, vo);
        
        // 处理标签转换 - 如果需要将tags列表转为JSON字符串
        if (document.getTags() != null) {
            vo.setTags(Collections.singletonList(JSONUtil.toJsonStr(document.getTags())));
        }
        
        return vo;
    }
} 