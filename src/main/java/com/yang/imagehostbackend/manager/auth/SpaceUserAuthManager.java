package com.yang.imagehostbackend.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONUtil;
import com.github.xiaoymin.knife4j.core.util.StrUtil;
import com.yang.imagehostbackend.manager.auth.model.SpaceUserAuthConfig;
import com.yang.imagehostbackend.manager.auth.model.SpaceUserRole;
import com.yang.imagehostbackend.model.entity.Space;
import com.yang.imagehostbackend.model.entity.SpaceUser;
import com.yang.imagehostbackend.model.entity.User;
import com.yang.imagehostbackend.model.enums.SpaceRoleEnum;
import com.yang.imagehostbackend.model.enums.SpaceTypeEnum;
import com.yang.imagehostbackend.service.SpaceUserService;
import com.yang.imagehostbackend.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 加载配置文件到文件
 * @Author 小小星仔
 * @Create 2025-05-13 10:38
 */
@Component
public class SpaceUserAuthManager {
    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService  userService;

    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;
    static {
        String json = ResourceUtil.readUtf8Str("biz/spaceAuthConfig.json");
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    //根据角色获取权限列表
    public List<String> getPermissionsByRole(String spaceUserRole) {
        if(StrUtil.isBlank(spaceUserRole)) return new ArrayList<>();
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles().stream()
                .filter(r -> spaceUserRole.equals(r.getKey()))
                .findFirst()
                .orElse(null);
        if(role == null) return new ArrayList<>();
        return role.getPermissions();
    }

    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return new ArrayList<>();
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }

}
