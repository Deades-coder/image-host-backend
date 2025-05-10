package com.yang.imagehostbackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.exception.ThrowUtils;
import com.yang.imagehostbackend.manager.CosManager;
import com.yang.imagehostbackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @Author 小小星仔
 * @Create 2025-05-10 12:59
 */
@Service
@Slf4j
public class UrlPictureUpload extends PictureUploadTemplate {

    @Resource
    private CosManager cosManager;



    @Override
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 校验图片
        validPicture(inputSource);
        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginFilename(inputSource);
        // 自己拼接文件上传路径，而不是使用原始文件名称，可以增强安全性
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;
        try {
            // 创建临时文件，获取文件到服务器
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源
            processFile(inputSource, file);
            // 上传图片到对象存储
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if(CollUtil.isNotEmpty(objectList)){
                CIObject compressedObject = objectList.get(0);

                return buildResult(originalFilename, compressedObject);
            }
            // 获取图片信息对象，封装返回结果
            return buildResult(originalFilename, file, uploadPath, imageInfo);
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 临时文件清理
            this.deleteTempFile(file);
        }
    }

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 1. 校验非空
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");

        // 2. 校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 3. 校验 URL 的协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址"
        );
        // 4. 发送 HEAD 请求验证文件是否存在
        HttpResponse httpResponse = null;
        try {
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    .execute();
            // 未正常返回，无需执行其他判断
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 5. 文件存在，文件类型校验
            String contentType = httpResponse.header("Content-Type");
            // 不为空，才校验是否合法，这样校验规则相对宽松
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 6. 文件存在，文件大小校验
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_M = 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > 50 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 50MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }
            }
        } finally {
            // 记得释放资源
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件到临时目录
        HttpUtil.downloadFile(fileUrl, file);
    }
}