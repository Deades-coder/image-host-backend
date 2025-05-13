package com.yang.imagehostbackend.controller;

import cn.dev33.satoken.filter.SaPathCheckFilterForServlet;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.yang.imagehostbackend.annotation.AuthCheck;
import com.yang.imagehostbackend.api.aliyunai.AliYunAiApi;
import com.yang.imagehostbackend.api.aliyunai.model.ExpanedImageTaskResponse;
import com.yang.imagehostbackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yang.imagehostbackend.api.imagesearch.ImageSearchApiFacade;
import com.yang.imagehostbackend.api.imagesearch.model.ImageSearchResult;
import com.yang.imagehostbackend.manager.auth.SpaceUserAuthManager;
import com.yang.imagehostbackend.manager.auth.StpKit;
import com.yang.imagehostbackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.yang.imagehostbackend.common.BaseResponse;
import com.yang.imagehostbackend.common.DeleteRequest;
import com.yang.imagehostbackend.common.ResultUtils;
import com.yang.imagehostbackend.constant.SpaceUserPermissionConstant;
import com.yang.imagehostbackend.constant.UserConstant;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.exception.ThrowUtils;
import com.yang.imagehostbackend.model.entity.ExpendImageTask;
import com.yang.imagehostbackend.model.enums.PictureReviewStatusEnum;
import com.yang.imagehostbackend.model.dto.picture.*;
import com.yang.imagehostbackend.model.entity.Picture;
import com.yang.imagehostbackend.model.entity.User;
import com.yang.imagehostbackend.model.entity.Space;
import com.yang.imagehostbackend.model.vo.PictureTagCategory;
import com.yang.imagehostbackend.model.vo.PictureVO;
import com.yang.imagehostbackend.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 22:37
 */
@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;

    @Resource(name = "uploadTaskExecutor")
    private Executor uploadTaskExecutor;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private Cache<String, String> listVOPage;

    @Autowired
    private Cache<Long, PictureVO> hotImage;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private ExpendImageTaskService expendImageTaskService;

    @Resource
    private SpaceUserAuthManager  spaceUserAuthManager;

    private static final String LIST_PICVO_BY_PAGE = "yupicture:listPictureVOByPage";


    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public CompletableFuture<BaseResponse<?>> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        // 记录主线程信息
        long startTime = System.currentTimeMillis();
        long mainThreadId = Thread.currentThread().getId();
        log.info("主线程 [{}] 开始处理上传请求，时间: {}", mainThreadId, startTime);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 异步执行上传
        CompletableFuture<BaseResponse<?>> future = CompletableFuture.supplyAsync(() -> {
            try {
                // 调用 PictureService 上传图片并保存
                PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
                return ResultUtils.success(pictureVO);
            } catch (BusinessException e) {
                log.error("图片上传失败: {}", e.getMessage());
                return ResultUtils.error(e.getCode(), e.getMessage());
            } catch (Exception e) {
                log.error("图片上传失败: {}", e.getMessage());
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "上传失败: " + e.getMessage());
            }
        }, uploadTaskExecutor);
        System.out.println("主线程在召唤。。。。。。");
        log.info("主线程 [{}] 提交异步任务并返回，耗时: {}ms", mainThreadId, System.currentTimeMillis() - startTime);
        return future;

    }

    /**
     * 通过 URL 上传图片
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)

    public CompletableFuture<BaseResponse<?>> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        CompletableFuture<BaseResponse<?>> future = CompletableFuture.supplyAsync(() -> {
            try {
                // 调用 PictureService 上传图片并保存
                PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                return ResultUtils.success(pictureVO);
            } catch (BusinessException e) {
                log.error("图片上传失败: {}", e.getMessage());
                return ResultUtils.error(e.getCode(), e.getMessage());
            } catch (Exception e) {
                log.error("图片上传失败: {}", e.getMessage());
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "上传失败: " + e.getMessage());
            }
        }, uploadTaskExecutor);
        return future;
    }

    @PostMapping("/list/page/vo/cache")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<Page<PictureVO>> listPictureVOByPageCache(@RequestBody PictureQueryRequest pictureQueryRequest,HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        //  分页限制
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR);
        
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库
            // 普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
//            User loginUser = userService.getLoginUser(request);
//            Space space = spaceService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
        }

        // 构建 key
        String queryCondidition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondidition.getBytes());
        String cacheKey = String.format(LIST_PICVO_BY_PAGE + hashKey);

        // 本地缓存查
        String cachedValue = listVOPage.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中，返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue,Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 从缓存中查询
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        String cacheData = valueOperations.get(cacheKey);
        if (cacheData != null) {
            Page<PictureVO> cachedPage = JSONUtil.toBean(cacheData,Page.class);
            return ResultUtils.success(cachedPage);
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 更新 Redis 缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 设置缓存的过期时间，5 - 10 分钟过期，防止缓存雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        valueOperations.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        listVOPage.put(cacheKey, cacheValue);
        return ResultUtils.success(pictureVOPage);

    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(deleteRequest.getId(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture, loginUser);

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 空间的图片需要鉴权
        Space space = null;
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }

        // 获取权限列表
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        // 优先从缓存获取
        PictureVO pictureVO = hotImage.getIfPresent(id);
        if (pictureVO != null) {
            return ResultUtils.success(pictureVO);
        }
        pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);

        // 获取封装类
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量编辑图片
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }


    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }
    
    // 批量爬取图片
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<CompletableFuture<Integer>> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        CompletableFuture<Integer> uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }
    /**
     * 以图搜图
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> resultList = null;
        resultList = ImageSearchApiFacade.searchImage(oldPicture.getThumbnailUrl());
        return ResultUtils.success(resultList);
    }

    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }
    /**
     * 创建 AI 扩图任务
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<ExpanedImageTaskResponse> createPictureOutPaintingTask(@RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
                                                                               HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        ExpanedImageTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        
        // 首先从数据库中查询任务
        ExpendImageTask expendImageTask = expendImageTaskService.getByTaskId(taskId);
        if (expendImageTask == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "扩图任务不存在");
        }
        
        // 如果任务已经成功完成，直接返回结果
        if ("SUCCEEDED".equals(expendImageTask.getTask_status()) && StrUtil.isNotBlank(expendImageTask.getOutput_image_url())) {
            GetOutPaintingTaskResponse response = new GetOutPaintingTaskResponse();
            GetOutPaintingTaskResponse.Output output = new GetOutPaintingTaskResponse.Output();
            output.setTaskId(expendImageTask.getTask_id());
            output.setTaskStatus(expendImageTask.getTask_status());
            output.setOutputImageUrl(expendImageTask.getOutput_image_url());
            response.setOutput(output);
            return ResultUtils.success(response);
        }
        
        // 如果任务还在进行中，查询最新状态
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        
        // 更新数据库中的任务状态
        if (task != null && task.getOutput() != null && 
            !expendImageTask.getTask_status().equals(task.getOutput().getTaskStatus())) {
            expendImageTaskService.processOutPaintingTaskResult(taskId);
        }
        
        return ResultUtils.success(task);
    }

}
