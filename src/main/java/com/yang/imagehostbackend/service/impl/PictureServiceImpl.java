package com.yang.imagehostbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.imagehostbackend.model.entity.Picture;
import com.yang.imagehostbackend.service.PictureService;
import com.yang.imagehostbackend.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
* @author Decades
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-05-09 21:08:59
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

}




