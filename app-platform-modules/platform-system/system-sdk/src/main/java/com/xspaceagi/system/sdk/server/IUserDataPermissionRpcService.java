package com.xspaceagi.system.sdk.server;

import com.xspaceagi.system.sdk.service.dto.UserDataPermissionDto;

/**
 * 用户数据权限查询接口（供内部模块 RPC 调用）
 */
public interface IUserDataPermissionRpcService {

    /**
     * 查询用户的数据权限
     */
    UserDataPermissionDto getUserDataPermission(Long userId);

}
