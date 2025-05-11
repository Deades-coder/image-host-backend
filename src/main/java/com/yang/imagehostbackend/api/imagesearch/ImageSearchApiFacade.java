package com.yang.imagehostbackend.api.imagesearch;

import com.yang.imagehostbackend.api.imagesearch.model.ImageSearchResult;
import com.yang.imagehostbackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.yang.imagehostbackend.api.imagesearch.sub.GetImageListApi;
import com.yang.imagehostbackend.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @Author 小小星仔
 * @Create 2025-05-11 12:38
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        List<ImageSearchResult> imageList = searchImage("");
        System.out.println("结果列表" + imageList);
    }
}