package com.yang.imagehostbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.yang.imagehostbackend.api.aliyunai.AliYunAiApi;
import com.yang.imagehostbackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yang.imagehostbackend.api.aliyunai.model.ExpanedImageTaskResponse;
import com.yang.imagehostbackend.config.AsyncConfig;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.exception.ThrowUtils;
import com.yang.imagehostbackend.manager.CosManager;
import com.yang.imagehostbackend.manager.FileManager;
import com.yang.imagehostbackend.manager.upload.FilePictureUpload;
import com.yang.imagehostbackend.manager.upload.PictureUploadTemplate;
import com.yang.imagehostbackend.manager.upload.UrlPictureUpload;
import com.yang.imagehostbackend.model.dto.picture.*;
import com.yang.imagehostbackend.model.entity.ExpendImageTask;
import com.yang.imagehostbackend.model.entity.Space;
import com.yang.imagehostbackend.model.enums.PictureReviewStatusEnum;
import com.yang.imagehostbackend.model.dto.file.UploadPictureResult;
import com.yang.imagehostbackend.model.entity.Picture;
import com.yang.imagehostbackend.model.entity.User;
import com.yang.imagehostbackend.model.vo.PictureVO;
import com.yang.imagehostbackend.model.vo.UserVO;
import com.yang.imagehostbackend.producer.ExpandImageTaskProducer;
import com.yang.imagehostbackend.service.ExpendImageTaskService;
import com.yang.imagehostbackend.service.PictureService;
import com.yang.imagehostbackend.mapper.PictureMapper;
import com.yang.imagehostbackend.service.SpaceService;
import com.yang.imagehostbackend.service.UserService;
import com.yang.imagehostbackend.service.ThumbService;
import com.yang.imagehostbackend.utils.ColorSimilarUtils;
import com.yang.imagehostbackend.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
* @author Decades
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-05-09 21:08:59
*/
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{
    @Autowired
    private Cache<String, String> listVOPage;

    @Resource
    private UserService userService;
    
    @Resource
    private ThumbService thumbService;
    
    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AsyncConfig asyncConfig;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Autowired
    private Cache<Long, PictureVO> hotImage;

    private static final String LIST_PICVO_BY_PAGE = "yupicture:listPictureVOByPage";

    @Resource
    private ExpendImageTaskService expendImageTaskService;
    
    @Resource
    private ExpandImageTaskProducer expandImageTaskProducer;

    private static final String PICTURE_COUNT_PREFIX = "picture:count:";
    private static final String HOT_PICTURES_ZSET = "hot:pictures:";

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }


    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Long spaceId = pictureUploadRequest.getSpaceId();
        if(spaceId != null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不存在");
            // 校验是否有空间的权限，仅空间管理员才能上传
//            if(!loginUser.getId().equals(space.getUserId())){
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验额度
            if(space.getTotalCount() >= space.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if(space.getTotalSize() >= space.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }

        }
        // 判断是新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，需要校验图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑图片
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            //没传 spaceId，则复用原有图片的 spaceId
            if(spaceId == null){
                if(oldPicture.getSpaceId() != null){
                    spaceId = oldPicture.getSpaceId();
                }else{
                    // 传了 spaceId，必须和原图片的空间 id 一致
                    if(ObjUtil.notEqual(spaceId,oldPicture.getSpaceId())){
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                    }
                }
            }
        }
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if(spaceId == null){
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else{
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据 inputSource 的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setSpaceId(spaceId);
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();

        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));

        picture.setUserId(loginUser.getId());
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要清除缓存（第一次删除）
            clearPictureCache(pictureId);
            
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 上传图片事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
            if (finalSpaceId != null) {
                // 更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        
        // 如果是更新操作，延迟一段时间后再次删除缓存（双删策略）
        if (pictureId != null) {
            asyncDeleteCache(pictureId);
        }
        return PictureVO.objToVo(picture);

    }




    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        
        // 添加：获取当前登录用户，判断是否已点赞
        try {
            User loginUser = userService.getLoginUser(request);
            if (loginUser != null) {
                // 查询当前用户是否已点赞该图片
                Long loginUserId = loginUser.getId();
                Boolean hasThumb = thumbService.hasThumb(picture.getId(), loginUserId);
                pictureVO.setHasThumb(hasThumb);
            }
        } catch (Exception e) {
            // 未登录或其他异常情况下，默认未点赞
            pictureVO.setHasThumb(false);
            log.warn("获取用户点赞状态失败: {}", e.getMessage());
        }
        
        long id = picture.getId();
        // 统计访问量
        String redisKey = PICTURE_COUNT_PREFIX + id;
        stringRedisTemplate.opsForValue().increment(redisKey);
        stringRedisTemplate.opsForZSet().incrementScore(HOT_PICTURES_ZSET,String.valueOf(id),1); // 更新分数
        return pictureVO;
    }

    /**
     * 将分页查询到的Picture实体列表转换为PictureVO(View Object)分页对象，并填充关联的用户信息。
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if(CollUtil.isEmpty(pictureList)) return pictureVOPage;
        List<PictureVO> pictureVOList = pictureList.stream().map(picture -> {
            PictureVO pictureVO = this.getPictureVO(picture, request);
            return pictureVO;
        }).collect(Collectors.toList());
        Set<Long> userIdSet = pictureVOList.stream().map(pictureVO -> pictureVO.getUser().getId()).collect(Collectors.toSet());
        Map<Long,List<User>> userIdUserMap = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user= null;
            if (userIdUserMap.containsKey(userId)) {
                user = userIdUserMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer  reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        // 新增时间查询
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        if (reviewStatus == null || id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否是旧图片
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已审核
        ThrowUtils.throwIf(oldPicture.getReviewStatus().equals(reviewStatus), ErrorCode.OPERATION_ERROR, "图片已审核,请勿重复审核");
        // 仅管理员可审核，更新审核状态
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if(userService.isAdmin(loginUser)){
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动审核通过");
            picture.setReviewTime(new Date());
        }else{
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    @Async("uploadTaskExecutor")
    public CompletableFuture<Integer> uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 50, ErrorCode.PARAMS_ERROR, "一次最多上传50张图片");
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);

        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");

        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger nameIndex = new AtomicInteger(1);
        List<CompletableFuture<Void>> uploadTasks = new ArrayList<>();

        for (Element imgElement : imgElementList) {
            if (uploadCount.get() >= count) {
                break;
            }

            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }

            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + nameIndex.getAndIncrement());

            String finalFileUrl = fileUrl;
            CompletableFuture<Void> uploadTask = CompletableFuture.runAsync(() -> {
                try {
                    PictureVO pictureVO = this.uploadPicture(finalFileUrl, pictureUploadRequest, loginUser);
                    log.info("图片上传成功，id = {}", pictureVO.getId());
                    uploadCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("图片上传失败", e);
                }
            });

            uploadTasks.add(uploadTask);
        }

        return CompletableFuture.allOf(uploadTasks.toArray(new CompletableFuture[0]))
                .thenApply(v -> uploadCount.get());
    }
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断改图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除图片
        cosManager.deleteObject(pictureUrl);
        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 判断是否存在
        Picture oldPicture  = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        
        // 第一次删除缓存
        clearPictureCache(pictureId);
        
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                    .setSql("totalCount = totalCount - 1")
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return true;
        });
        
        // 延迟双删
        asyncDeleteCache(pictureId);
        
        // 清理文件
        this.clearPictureFile(oldPicture);
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 将list转为string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        
        // 第一次删除缓存
        clearPictureCache(id);
        
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        
        // 延迟双删
        asyncDeleteCache(id);
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if(spaceId == null){
            // 公共图库，仅本人或管理员操作
            if(!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }else{
            // 私有空间，仅仅空间管理员可操作
            if(!picture.getUserId().equals(loginUserId)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }

    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 查询该空间下所有图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(picColor);
        // 计算相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                // 取前 10 个
                .limit(10)
                .collect(Collectors.toList());

        // 转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 校验参数
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList)||spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 查询指定图片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                .in(Picture::getId, pictureIdList)
                .eq(Picture::getSpaceId, spaceId)
                .list();
        if(pictureList.isEmpty()){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在或不属于该空间");
        }
        
        // 第一次删除缓存（批量）
        for (Picture picture : pictureList) {
            clearPictureCache(picture.getId());
        }
        
        // 分批处理，每批次50个
        int batchSize = 50;
        List<List<Picture>> batches = new ArrayList<>();
        for (int i = 0; i < pictureList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, pictureList.size());
            batches.add(pictureList.subList(i, end));
        }
        
        // 使用线程池并发处理每个批次
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (List<Picture> batch : batches) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                // 更新分类和标签
                batch.forEach(picture -> {
                    if(StrUtil.isNotBlank(category)){
                        picture.setCategory(category);
                    }
                    if(CollUtil.isNotEmpty(tags)){
                        picture.setTags(JSONUtil.toJsonStr(tags));
                    }
                    picture.setEditTime(new Date());
                });
                // 批量更新
                return this.updateBatchById(batch);
            }, asyncConfig.uploadTaskExecutor());
            futures.add(future);
        }
        
        // 等待所有批次处理完成
        boolean allSuccess = true;
        try {
            for (CompletableFuture<Boolean> future : futures) {
                boolean result = future.get();
                if (!result) {
                    allSuccess = false;
                    break;
                }
            }
        } catch (Exception e) {
            log.error("批量编辑图片异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量处理失败: " + e.getMessage());
        }
        
        ThrowUtils.throwIf(!allSuccess, ErrorCode.OPERATION_ERROR, "部分批次更新失败");
        
        // 延迟双删（批量）
        for (Picture picture : pictureList) {
            asyncDeleteCache(picture.getId());
        }
    }

    @Override
    public ExpanedImageTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 校验权限
        checkPictureAuth(loginUser, picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        ExpanedImageTaskResponse response = aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
        
        if (response != null && response.getOutput() != null && StrUtil.isNotBlank(response.getOutput().getTaskId())) {
            String taskId = response.getOutput().getTaskId();
            log.info("创建扩图任务成功，taskId={}", taskId);
            
            // 保存任务到数据库
            ExpendImageTask expendImageTask = new ExpendImageTask();
            expendImageTask.setTask_id(taskId);
            expendImageTask.setPicture_id(pictureId);
            expendImageTask.setUser_id(loginUser.getId());
            expendImageTask.setTask_status(response.getOutput().getTaskStatus());
            expendImageTask.setCreate_time(new Date());
            expendImageTask.setUpdate_time(new Date());
            expendImageTask.setIs_delete(0);
            expendImageTaskService.save(expendImageTask);
            
            // 发送消息到Kafka队列进行异步轮询
            ExpandImageTaskMessage message = ExpandImageTaskMessage.builder()
                    .taskId(taskId)
                    .pictureId(pictureId)
                    .userId(loginUser.getId())
                    .createTime(LocalDateTime.now())
                    .retryCount(0)
                    .taskStatus(response.getOutput().getTaskStatus())
                    .build();
            expandImageTaskProducer.sendMessage(message);
        }
        
        return response;
    }

    /**
     * 清除图片相关的所有缓存
     * @param pictureId 图片ID
     */
    public void clearPictureCache(Long pictureId) {
        if (pictureId == null) return;
        
        // 清除本地缓存
        hotImage.invalidate(pictureId);
        
        // 清除Redis缓存 - 包括与该图片相关的所有查询缓存
        Set<String> keys = stringRedisTemplate.keys(LIST_PICVO_BY_PAGE + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        
        // 清除本地的分页缓存 - 因为无法精确知道哪些分页包含该图片，所以清空所有
        listVOPage.invalidateAll();
        
        // 清除热点图片计数和排名
        String redisKey = PICTURE_COUNT_PREFIX + pictureId;
        stringRedisTemplate.delete(redisKey);
        stringRedisTemplate.opsForZSet().remove(HOT_PICTURES_ZSET, String.valueOf(pictureId));
        
        log.debug("Cleared cache for picture ID: {}", pictureId);
    }
    
    /**
     * 异步延迟执行第二次缓存删除操作
     * @param pictureId 图片ID
     */
    @Async("uploadTaskExecutor")
    public void asyncDeleteCache(Long pictureId) {
        try {
            // 延迟一段时间再次删除缓存，通常建议50-100ms
            Thread.sleep(100);
            clearPictureCache(pictureId);
            log.debug("Executed delayed cache deletion for picture ID: {}", pictureId);
        } catch (InterruptedException e) {
            log.error("Delayed cache deletion interrupted for picture ID: {}", pictureId, e);
            Thread.currentThread().interrupt();
        }
    }

}




