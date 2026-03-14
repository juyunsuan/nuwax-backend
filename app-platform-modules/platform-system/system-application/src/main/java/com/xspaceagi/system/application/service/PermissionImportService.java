package com.xspaceagi.system.application.service;

import com.xspaceagi.system.infra.dao.entity.Tenant;

/**
 * 权限数据导入服务
 */
public interface PermissionImportService {

    /**
     * 将指定版本的权限导入到目标租户
     */
    void importToTenant(Tenant tenant, String version);
}
