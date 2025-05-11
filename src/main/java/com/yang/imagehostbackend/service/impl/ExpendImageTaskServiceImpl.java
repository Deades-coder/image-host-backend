package com.yang.imagehostbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.imagehostbackend.api.aliyunai.AliYunAiApi;
import com.yang.imagehostbackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yang.imagehostbackend.mapper.ExpendImageTaskMapper;
import com.yang.imagehostbackend.model.entity.ExpendImageTask;
import com.yang.imagehostbackend.service.ExpendImageTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * ExpendImageTask Service Implementation
 * @author 小小星仔
 * @Create 2025-05-11 22:23
 */
@Service
@Slf4j
public class ExpendImageTaskServiceImpl extends ServiceImpl<ExpendImageTaskMapper, ExpendImageTask> implements ExpendImageTaskService {

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Override
    public boolean processOutPaintingTaskResult(String taskId) {
        // 获取阿里云任务结果
        GetOutPaintingTaskResponse taskResponse = aliYunAiApi.getOutPaintingTask(taskId);
        if (taskResponse == null || taskResponse.getOutput() == null) {
            log.error("获取扩图任务结果失败，taskId={}", taskId);
            return false;
        }
        
        // 获取任务状态
        String taskStatus = taskResponse.getOutput().getTaskStatus();
        
        // 查询数据库中的任务
        ExpendImageTask expendImageTask = getByTaskId(taskId);
        if (expendImageTask == null) {
            log.error("找不到对应的扩图任务记录，taskId={}", taskId);
            return false;
        }
        
        // 更新任务状态
        expendImageTask.setTask_status(taskStatus);
        expendImageTask.setUpdate_time(new Date());
        
        // 如果任务已完成，保存结果URL
        if ("SUCCEEDED".equals(taskStatus)) {
            expendImageTask.setOutput_image_url(taskResponse.getOutput().getOutputImageUrl());
            log.info("扩图任务完成，taskId={}，outputImageUrl={}", taskId, taskResponse.getOutput().getOutputImageUrl());
        } else if ("FAILED".equals(taskStatus)) {
            log.error("扩图任务失败，taskId={}, message={}", taskId, 
                    taskResponse.getOutput().getMessage() != null ? taskResponse.getOutput().getMessage() : "未知错误");
        }
        
        // 更新数据库
        return this.updateById(expendImageTask);
    }

    @Override
    public ExpendImageTask getByTaskId(String taskId) {
        LambdaQueryWrapper<ExpendImageTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ExpendImageTask::getTask_id, taskId);
        queryWrapper.eq(ExpendImageTask::getIs_delete, 0);
        return this.getOne(queryWrapper);
    }

    @Override
    public GetOutPaintingTaskResponse getOutPaintingTaskResult(String taskId) {
        return aliYunAiApi.getOutPaintingTask(taskId);
    }
}




