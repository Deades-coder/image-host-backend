package com.yang.imagehostbackend.api.imagesearch.sub;

import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author 小小星仔
 * @Create 2025-05-11 10:57
 */
@Slf4j
public class GetImagePageUrlApi {
    // 获取图片的页面地址
    public static String getImagePageUrl(String imageUrl) {
        log.info("图片地址：" + imageUrl);
       // 参数准备
        Map<String,Object> formData = new HashMap<>();
        formData.put("image",imageUrl);
        formData.put("tn","pc");
        formData.put("from","pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        // 获取当前时间戳
        long uptime = System.currentTimeMillis();
        // 请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;
        String header = "1742876883046_1742910942849_5FXTkMQcCOfuBbkM4E4mi2Sq6XU8d8cT08OncVQ0YrUw1o15GdAeK1+" +
                "OPpJeX3gdYGVoKzehG+BytZd+DI5cXF7yr1ZlNH0j07A4LIh4QNHMV88X8vIC8qGkqfTL2PFBOATy7PustDgBQqwJ7s6cJaKZm" +
                "XS77UAN19eQYisQdrWgel9cNrDDc/J7eS0LpXmgauHZpjPzpxkkySvbv" +
                "Tp2L4coy310BVCAyn9R7mRrAKlwJRi4tLqw77KqREQCH0tOOD+TSa3IaGJzqEiM0Z+" +
                "+bMdXAMSj5qaDF8OC6Vx8v1Sk5psZ+lxPzBRgYKJYfg/q+Nc0JAhphWVy7ytYcfU3UXbsmbXzOUAH9+oWOgaJ2Rp5cdUHdF96hRy" +
                "DK2AZWhlUAD08minHpNwlJeY6D3AFX0oVXz7sjw9GTOIgz4YBeUo=";
        try {
            // 发送post请求
            HttpResponse response = HttpRequest.post(url).
                    form(formData).
                    header("Acs-Token",header).
                    timeout(5000).
                    execute();
            // 判断相应状态
            if(HttpStatus.HTTP_OK!= response.getStatus()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"接口调用失败");
            }
            // 解析响应
            String responseBody = response.body();
            Map<String,Object> result = JSONUtil.toBean(responseBody, Map.class);
            //处理结果
            if(result == null||!Integer.valueOf(0).equals(result.get("status"))){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"接口调用失败");
            }
            Map<String,Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String) data.get("url");
            //解码
            String searchResUrl = URLUtil.decode(rawUrl);
            if(searchResUrl == null){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"未返回有用的结果");
            }
            return searchResUrl;
        }catch (Exception e){
            log.error("搜索失败",e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"搜索失败");
        }
    }

    public static void main(String[] args) {
        //测试功能
        String imageurl = "";
        String result = getImagePageUrl(imageurl);
        System.out.println("结果列表" + result);
    }
}
