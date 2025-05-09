package com.yang.imagehostbackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.transfer.Transfer;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.Upload;
import com.yang.imagehostbackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 21:02
 */
@Component
public class FileManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private TransferManager transferManager;


    @Resource
    private COSClient cosClient;

    // 上传对象
    public Transfer uploadFile(String key, File file) throws CosServiceException, CosClientException {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        Upload upload = transferManager.upload(putObjectRequest);
        return upload;
    }

    public void uploadFileAndWait(String key, File file) throws InterruptedException, CosServiceException, CosClientException {
        Transfer upload = uploadFile(key, file);
        upload.waitForCompletion();
    }
    /**
     * 上传对象（附带图片信息），数据万象解析
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }


}
