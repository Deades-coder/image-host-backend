package com.yang.imagehostbackend.service;

import com.yang.imagehostbackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yang.imagehostbackend.model.entity.ExpendImageTask;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Decades
* @description 针对表【expend_image_task(扩图任务表)】的数据库操作Service
* @createDate 2025-05-11 22:20:10
*/
public interface ExpendImageTaskService extends IService<ExpendImageTask> {
    /**
     * 处理扩图任务结果
     * @param taskId 阿里云任务ID
     * @return 处理结果
     */
    boolean processOutPaintingTaskResult(String taskId);

    /**
     * 根据taskId获取任务
     * @param taskId 阿里云任务ID
     * @return 任务实体
     */
    ExpendImageTask getByTaskId(String taskId);

    /**
     * 获取任务结果
     * @param taskId 阿里云任务ID
     * @return 任务结果
     */
    GetOutPaintingTaskResponse getOutPaintingTaskResult(String taskId);

}
