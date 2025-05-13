package com.yang.imagehostbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.xiaoymin.knife4j.core.util.StrUtil;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.exception.ThrowUtils;
import com.yang.imagehostbackend.manager.sharding.DynamicShardingManager;
import com.yang.imagehostbackend.model.dto.space.SpaceAddRequest;
import com.yang.imagehostbackend.model.dto.space.SpaceQueryRequest;
import com.yang.imagehostbackend.model.entity.Space;
import com.yang.imagehostbackend.model.entity.SpaceUser;
import com.yang.imagehostbackend.model.entity.User;
import com.yang.imagehostbackend.model.enums.SpaceLevelEnum;
import com.yang.imagehostbackend.model.enums.SpaceRoleEnum;
import com.yang.imagehostbackend.model.enums.SpaceTypeEnum;
import com.yang.imagehostbackend.model.vo.SpaceVO;
import com.yang.imagehostbackend.model.vo.UserVO;
import com.yang.imagehostbackend.service.SpaceService;
import com.yang.imagehostbackend.mapper.SpaceMapper;
import com.yang.imagehostbackend.service.SpaceUserService;
import com.yang.imagehostbackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author Decades
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-05-10 22:43:41
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate  transactionTemplate;

    @Resource
    @Lazy
    private DynamicShardingManager dynamicShardingManager;
    @Resource
    @Lazy
    private SpaceUserService spaceUserService;


    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest,space);
        if(StrUtil.isBlank(space.getSpaceName())){
            space.setSpaceName("默认空间" + UUID.randomUUID().toString().substring(0,5));
        }
        if(space.getSpaceLevel() == null){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if(spaceAddRequest.getSpaceType() == null){
            spaceAddRequest.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 将实体类和DTO进行转换
        BeanUtils.copyProperties(spaceAddRequest,space);

        // 填充容量和大小
        this.fillSpaceBySpaceLevel(space);
        //校验参数
        this.validSpace(space,true);
        // 权限校验
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if(SpaceLevelEnum.COMMON.getValue()!=space.getSpaceLevel()&&!userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 控制同一用户只能创建一个私有空间
        String lock = String.valueOf(userId).intern();
        synchronized (lock){
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 普通用户只能创建一个团队空间
                if(!userService.isAdmin(loginUser)){
                    // 判断是否已有空间
                    boolean exists = this.lambdaQuery().eq(Space::getUserId,userId).exists();
                    // 如果已有空间，则不允许创建
                    ThrowUtils.throwIf(exists,ErrorCode.OPERATION_ERROR,"每个用户仅能有一个私有空间");
                }
                // 创建
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");
                // 如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                dynamicShardingManager.createSpacePictureTable(space);
                return space.getId();
            });
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeE = SpaceTypeEnum.getEnumByValue(spaceType);
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if(spaceType == null){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类型不能为空");
            }

            // 修改数据时，如果要改空间级别
            if(spaceType !=null && spaceTypeE == null){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类型不存在");
            }


            if(StrUtil.isBlank(spaceName)){
                ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "空间名不能为空");
            }
            if(spaceLevel == null){
                ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "空间等级不能为空");
            }
        }
        // 修改空间级别时
        if(spaceLevel != null && spaceLevelEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间级别不存在");
        }
        if(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称过长");
        }
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 查询用户信息，获取空间关联的用户信息
        Long userId = space.getUserId();
        if(userId != null && userId > 0){
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if(CollUtil.isEmpty(spaceList)){
            return spaceVOPage;
        }
        // 对象列表 ==》封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 关联查询用户信息
        Set<Long> userIdSet = spaceVOList.stream().map(SpaceVO::getUserId).collect(Collectors.toSet());
        Map<Long, User> userIdUserMap = userService.listByIds(userIdSet).stream().collect(Collectors.toMap(User::getId, user -> user));
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user= null;
            if (userIdUserMap.containsKey(userId)) {
                user = userIdUserMap.get(userId);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

}




