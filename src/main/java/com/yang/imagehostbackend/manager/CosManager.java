package com.yang.imagehostbackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.transfer.Transfer;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.Upload;
import com.yang.imagehostbackend.config.CosClientConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 21:02
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private TransferManager transferManager;


    @Resource
    private COSClient cosClient;

    // 上传对象，仅做测试
    public Transfer uploadFile(String key, File file) throws CosServiceException, CosClientException {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        Upload upload = transferManager.upload(putObjectRequest);
        return upload;
    }

    public void uploadFileAndWait(String key, File file) throws InterruptedException, CosServiceException, CosClientException {
        Transfer upload = uploadFile(key, file);
        upload.waitForCompletion();
    }
    public void uploadFileAndWaitPic(String key, File file) throws InterruptedException, CosServiceException, CosClientException {
        Transfer upload = uploadFile(key, file);
        upload.waitForCompletion();
    }
    /**
     * 上传解析图片
     */
    public PutObjectResult putPictureObject(String key, MultipartFile file) throws Exception{
        try(InputStream inputStream = file.getInputStream()){
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                    inputStream,metadata);
            // 对图片进行处理（获取基本信息也被视作为一种处理）
            PicOperations picOperations = new PicOperations();
            // 1 表示返回原图信息
            picOperations.setIsPicInfo(1);
            // 构造处理参数
            putObjectRequest.setPicOperations(picOperations);
            return cosClient.putObject(putObjectRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }
}
