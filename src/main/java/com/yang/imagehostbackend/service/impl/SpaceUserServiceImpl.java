package com.yang.imagehostbackend.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.exception.ThrowUtils;
import com.yang.imagehostbackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yang.imagehostbackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yang.imagehostbackend.model.entity.Space;
import com.yang.imagehostbackend.model.entity.SpaceUser;
import com.yang.imagehostbackend.model.entity.User;
import com.yang.imagehostbackend.model.enums.SpaceRoleEnum;
import com.yang.imagehostbackend.model.vo.SpaceUserVO;
import com.yang.imagehostbackend.model.vo.SpaceVO;
import com.yang.imagehostbackend.service.SpaceService;
import com.yang.imagehostbackend.service.SpaceUserService;
import com.yang.imagehostbackend.mapper.SpaceUserMapper;
import com.yang.imagehostbackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author Decades
* @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
* @createDate 2025-05-12 21:58:13
*/
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
    implements SpaceUserService{

    @Resource
    private UserService  userService;

    @Resource
    private SpaceService spaceService;

    // 添加成员
    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        //数据库操作
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

    // 校验空间成员，add参数区分创建数据时校验还是编辑时校验
    // 创建成员时，要检查用户是否存在
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        Long spaceId= spaceUser.getSpaceId();
        Long userId= spaceUser.getUserId();
        if(add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId),ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null,ErrorCode.NOT_FOUND_ERROR,"用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null,ErrorCode.PARAMS_ERROR,"空间不存在");

        }
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum  spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if(spaceRole!=null&&spaceRoleEnum==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间角色不存在");
        }
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        // 对象转封装类
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        Long userId = spaceUserVO.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            spaceUserVO.setUser(userService.getUserVO(user));
        }
        //  空间信息
        Long spaceId = spaceUserVO.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            spaceUserVO.setSpace(spaceService.getSpaceVO(space,request));
        }
        return spaceUserVO;
    }

    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 判断输入列表是否为空
        if (spaceUserList == null) {
            return null;
        }

        return null;
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }

}




