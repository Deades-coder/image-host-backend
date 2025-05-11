package com.yang.imagehostbackend.manager;

import cn.hutool.core.io.FileUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private final List<String> ALLOW_FILE_TYPE = Arrays.asList("png", "jpg", "jpeg");

    // 上传对象，分块多线程上传，仅做测试
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
            // 表示返回原图信息
            picOperations.setIsPicInfo(1);
            List<PicOperations.Rule> rules = new ArrayList<>();
            String webpKey = FileUtil.mainName(key) + ".webp";
            PicOperations.Rule compressRule = new PicOperations.Rule();
            compressRule.setFileId(webpKey);
            compressRule.setBucket(cosClientConfig.getBucket());
            compressRule.setRule("imageMogr2/format/webp");
            rules.add(compressRule);
            // 缩略图处理，仅对 > 20 KB 的图片生成缩略图
            if (file.getSize() > 20 * 1024) {
                String thumbnailKey;
                PicOperations.Rule thumbnailRule = new PicOperations.Rule();
                if(!ALLOW_FILE_TYPE.contains(FileUtil.getSuffix(key))){
                    thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + "png";
                }else {
                    // 拼接缩略图的路径，使用webp格式
                    thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
                }
                thumbnailRule.setFileId(thumbnailKey);
                thumbnailRule.setBucket(cosClientConfig.getBucket());
                // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
                thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
                rules.add(thumbnailRule);
            }else{
                if (!ALLOW_FILE_TYPE.contains(FileUtil.getSuffix(key))) {
                    PicOperations.Rule transferRule = new PicOperations.Rule();
                    transferRule.setBucket(cosClientConfig.getBucket());
                    transferRule.setRule("imageMogr2/format/png");
                    String transferKey = FileUtil.mainName(key) + "_transfer" + ".png";
                    transferRule.setFileId(transferKey);
                    rules.add(transferRule);
                }
            }

            // 构造处理参数
            picOperations.setRules(rules);
            putObjectRequest.setPicOperations(picOperations);
            return cosClient.putObject(putObjectRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 普通同步上传文件，需要文件落地，
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种图片的处理）
        PicOperations picOperations = new PicOperations();
        // 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 图片处理规则列表
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 图片压缩（转成 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setRule("imageMogr2/format/webp");
        rules.add(compressRule);
        // 缩略图处理，仅对 > 20 KB 的图片生成缩略图
        if (file.length() > 20 * 1024) {
            String thumbnailKey;
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            if(!ALLOW_FILE_TYPE.contains(FileUtil.getSuffix(key))){
                thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + "png";
            }else {
                // 拼接缩略图的路径，使用webp格式
                thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            }
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
            rules.add(thumbnailRule);
        }else{
            if (!ALLOW_FILE_TYPE.contains(FileUtil.getSuffix(key))) {
                PicOperations.Rule transferRule = new PicOperations.Rule();
                transferRule.setBucket(cosClientConfig.getBucket());
                transferRule.setRule("imageMogr2/format/png");
                String transferKey = FileUtil.mainName(key) + "_transfer" + ".png";
                transferRule.setFileId(transferKey);
                rules.add(transferRule);
            }
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
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

    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}
