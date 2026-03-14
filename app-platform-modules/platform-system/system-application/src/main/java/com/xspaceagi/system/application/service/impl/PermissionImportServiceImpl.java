package com.xspaceagi.system.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xspaceagi.system.application.dto.permission.export.*;
import com.xspaceagi.system.application.service.PermissionImportService;
import com.xspaceagi.system.application.service.SysUserPermissionCacheService;
import com.xspaceagi.system.domain.model.ResourceNode;
import com.xspaceagi.system.infra.dao.entity.*;
import com.xspaceagi.system.infra.dao.service.*;
import com.xspaceagi.system.spec.common.RequestContext;
import com.xspaceagi.system.spec.constants.PermissionSyncConstants;
import com.xspaceagi.system.spec.enums.SourceEnum;
import com.xspaceagi.system.spec.enums.YnEnum;
import com.xspaceagi.system.spec.exception.BizException;
import com.xspaceagi.system.spec.jackson.JsonSerializeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限数据导入服务实现
 * 使用code防重：存在则更新，不存在则新增
 */
@Slf4j
@Service
public class PermissionImportServiceImpl implements PermissionImportService {

    private static final String CREATOR_SYSTEM = "system";

    @Resource
    private SysResourceService sysResourceService;
    @Resource
    private SysMenuService sysMenuService;
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysGroupService sysGroupService;
    @Resource
    private SysMenuResourceService sysMenuResourceService;
    @Resource
    private SysRoleMenuService sysRoleMenuService;
    @Resource
    private SysGroupMenuService sysGroupMenuService;
    @Resource
    private SysDataPermissionService sysDataPermissionService;
    @Resource
    private SysUserPermissionCacheService sysUserPermissionCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importToTenant(Tenant tenant, String version) {
        try {
            if (tenant == null || tenant.getId() == null) {
                throw new BizException("菜单权限导入失败,租户ID无效");
            }
            RequestContext.setThreadTenantId(tenant.getId());
            doImport(tenant, version);

            sysUserPermissionCacheService.clearCacheAllByTenant(tenant.getId());
        } finally {
            RequestContext.remove();
        }
    }

    private void doImport(Tenant tenant, String version) {
        Long tenantId = tenant.getId();
        PermissionExportDto dto = loadFromClasspath(version);
        if (dto == null) {
            log.warn("权限导入：未找到版本 {} 的JSON，跳过", version);
            return;
        }

        Map<String, Long> resourceCodeToId = new HashMap<>();
        Map<String, Long> menuCodeToId = new HashMap<>();
        Map<String, Long> roleCodeToId = new HashMap<>();
        Map<String, Long> groupCodeToId = new HashMap<>();

        // 1. Resource（按parentCode排序，先父后子）
        List<ResourceExportDto> sortedResources = sortResourcesByParent(dto.getResources());
        for (ResourceExportDto r : sortedResources) {
            SysResource entity = resolveOrCreateResource(tenantId, r, resourceCodeToId);
            if (entity != null) {
                resourceCodeToId.put(r.getCode(), entity.getId());
            }
        }

        // 2. Menu
        List<MenuExportDto> sortedMenus = sortMenusByParent(dto.getMenus());
        for (MenuExportDto m : sortedMenus) {
            SysMenu entity = resolveOrCreateMenu(tenantId, m, menuCodeToId);
            if (entity != null) {
                menuCodeToId.put(m.getCode(), entity.getId());
            }
        }

        // 3. Role
        for (RoleExportDto r : dto.getRoles()) {
            SysRole entity = resolveOrCreateRole(tenantId, r);
            if (entity != null) {
                roleCodeToId.put(r.getCode(), entity.getId());
            }
        }
        // 4. Group
        for (GroupExportDto g : dto.getGroups()) {
            SysGroup entity = resolveOrCreateGroup(tenantId, g);
            if (entity != null) {
                groupCodeToId.put(g.getCode(), entity.getId());
            }
        }

        // 5. MenuResource（防重：menuCode+resourceCode）
        for (MenuResourceExportDto mr : dto.getMenuResources()) {
            Long menuId = menuCodeToId.get(mr.getMenuCode());
            Long resourceId = resourceCodeToId.get(mr.getResourceCode());
            if (menuId == null || resourceId == null) continue;
            resolveOrCreateMenuResource(tenantId, menuId, resourceId, mr);
        }

        // 6. RoleMenu
        for (RoleMenuExportDto rm : dto.getRoleMenus()) {
            Long roleId = roleCodeToId.get(rm.getRoleCode());
            Long menuId = menuCodeToId.get(rm.getMenuCode());
            if (roleId == null || menuId == null) continue;
            String resourceTreeJson = buildResourceTreeJson(rm.getResourceTree(), resourceCodeToId);
            resolveOrCreateRoleMenu(tenantId, roleId, menuId, rm, resourceTreeJson);
        }

        // 7. GroupMenu
        for (GroupMenuExportDto gm : dto.getGroupMenus()) {
            Long groupId = groupCodeToId.get(gm.getGroupCode());
            Long menuId = menuCodeToId.get(gm.getMenuCode());
            if (groupId == null || menuId == null) continue;
            String resourceTreeJson = buildResourceTreeJson(gm.getResourceTree(), resourceCodeToId);
            resolveOrCreateGroupMenu(tenantId, groupId, menuId, gm, resourceTreeJson);
        }

        // 8. DataPermission
        for (DataPermissionExportDto dp : dto.getDataPermissions()) {
            Long targetId = null;
            if (Integer.valueOf(2).equals(dp.getTargetType())) { // ROLE
                targetId = roleCodeToId.get(dp.getTargetCode());
            } else if (Integer.valueOf(3).equals(dp.getTargetType())) { // GROUP
                targetId = groupCodeToId.get(dp.getTargetCode());
            }
            if (targetId == null) continue;
            resolveOrCreateDataPermission(tenantId, dp, targetId);
        }

        log.info("权限导入完成，租户ID：{}，版本：{}", tenantId, version);
    }

    private PermissionExportDto loadFromClasspath(String version) {
        String path = PermissionSyncConstants.buildClasspathPath(version);
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return null;
            }
            String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return JsonSerializeUtil.parseObject(json, PermissionExportDto.class);
        } catch (IOException e) {
            log.warn("读取权限JSON失败：{}", path, e);
            return null;
        }
    }

    private List<ResourceExportDto> sortResourcesByParent(List<ResourceExportDto> list) {
        Map<String, ResourceExportDto> byCode = list.stream().collect(Collectors.toMap(ResourceExportDto::getCode, x -> x, (a, b) -> a));
        List<ResourceExportDto> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        for (ResourceExportDto r : list) {
            if (StringUtils.isBlank(r.getParentCode()) || !byCode.containsKey(r.getParentCode())) {
                queue.add(r.getCode());
            }
        }
        while (!queue.isEmpty()) {
            String code = queue.poll();
            if (added.contains(code)) continue;
            ResourceExportDto r = byCode.get(code);
            if (r == null) continue;
            result.add(r);
            added.add(code);
            for (ResourceExportDto child : list) {
                if (code.equals(child.getParentCode()) && !added.contains(child.getCode())) {
                    queue.add(child.getCode());
                }
            }
        }
        for (ResourceExportDto r : list) {
            if (!added.contains(r.getCode())) result.add(r);
        }
        return result;
    }

    private List<MenuExportDto> sortMenusByParent(List<MenuExportDto> list) {
        Map<String, MenuExportDto> byCode = list.stream().collect(Collectors.toMap(MenuExportDto::getCode, x -> x, (a, b) -> a));
        List<MenuExportDto> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        for (MenuExportDto m : list) {
            if (StringUtils.isBlank(m.getParentCode()) || !byCode.containsKey(m.getParentCode())) {
                queue.add(m.getCode());
            }
        }
        while (!queue.isEmpty()) {
            String code = queue.poll();
            if (added.contains(code)) {
                continue;
            }
            MenuExportDto m = byCode.get(code);
            if (m == null) {
                continue;
            }
            result.add(m);
            added.add(code);
            for (MenuExportDto child : list) {
                if (code.equals(child.getParentCode()) && !added.contains(child.getCode())) {
                    queue.add(child.getCode());
                }
            }
        }
        for (MenuExportDto m : list) {
            if (!added.contains(m.getCode())) {
                result.add(m);
            }
        }
        return result;
    }

    private SysResource resolveOrCreateResource(Long tenantId, ResourceExportDto r, Map<String, Long> codeToId) {
        SysResource existing = sysResourceService.getOne(Wrappers.<SysResource>lambdaQuery()
                .eq(SysResource::getTenantId, tenantId).eq(SysResource::getCode, r.getCode()).eq(SysResource::getYn, YnEnum.Y.getKey()));
        Long parentId;
        if (StringUtils.isNotBlank(r.getParentCode())) {
            parentId = codeToId.get(r.getParentCode());
            if (parentId == null) {
                return null;
            }
        } else {
            parentId = 0L;
        }

        SysResource entity = existing != null ? existing : new SysResource();
        entity.setTenantId(tenantId);
        entity.setCode(r.getCode());
        entity.setName(r.getName());
        entity.setDescription(r.getDescription());
        entity.setSource(SourceEnum.SYSTEM.getCode());
        entity.setType(r.getType());
        entity.setParentId(parentId);
        entity.setPath(r.getPath());
        entity.setIcon(r.getIcon());
        entity.setSortIndex(r.getSortIndex());
        entity.setStatus(r.getStatus());
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysResourceService.updateById(entity);
        } else {
            sysResourceService.save(entity);
        }
        return entity;
    }

    private SysMenu resolveOrCreateMenu(Long tenantId, MenuExportDto m, Map<String, Long> codeToId) {
        SysMenu existing = sysMenuService.getOne(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getTenantId, tenantId).eq(SysMenu::getCode, m.getCode()).eq(SysMenu::getYn, YnEnum.Y.getKey()));
        Long parentId;
        if (StringUtils.isNotBlank(m.getParentCode())) {
            parentId = codeToId.get(m.getParentCode());
            if (parentId == null) {
                return null;
            }
        } else {
            parentId = 0L;
        }

        SysMenu entity = existing != null ? existing : new SysMenu();
        entity.setTenantId(tenantId);
        entity.setParentId(parentId);
        entity.setCode(m.getCode());
        entity.setName(m.getName());
        entity.setDescription(m.getDescription());
        entity.setSource(SourceEnum.SYSTEM.getCode());
        entity.setPath(m.getPath());
        entity.setOpenType(m.getOpenType());
        entity.setIcon(m.getIcon());
        entity.setSortIndex(m.getSortIndex());
        entity.setStatus(m.getStatus());
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysMenuService.updateById(entity);
        } else {
            sysMenuService.save(entity);
        }
        return entity;
    }

    private SysRole resolveOrCreateRole(Long tenantId, RoleExportDto r) {
        SysRole existing = sysRoleService.getOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getTenantId, tenantId).eq(SysRole::getCode, r.getCode()).eq(SysRole::getYn, YnEnum.Y.getKey()));
        SysRole entity = existing != null ? existing : new SysRole();
        entity.setTenantId(tenantId);
        entity.setCode(r.getCode());
        entity.setName(r.getName());
        entity.setDescription(r.getDescription());
        entity.setSource(SourceEnum.SYSTEM.getCode());
        entity.setStatus(r.getStatus());
        entity.setSortIndex(r.getSortIndex());
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysRoleService.updateById(entity);
        } else {
            sysRoleService.save(entity);
        }
        return entity;
    }

    private SysGroup resolveOrCreateGroup(Long tenantId, GroupExportDto g) {
        SysGroup existing = sysGroupService.getOne(Wrappers.<SysGroup>lambdaQuery()
                .eq(SysGroup::getTenantId, tenantId).eq(SysGroup::getCode, g.getCode()).eq(SysGroup::getYn, YnEnum.Y.getKey()));
        SysGroup entity = existing != null ? existing : new SysGroup();
        entity.setTenantId(tenantId);
        entity.setCode(g.getCode());
        entity.setName(g.getName());
        entity.setDescription(g.getDescription());
        entity.setMaxUserCount(g.getMaxUserCount());
        entity.setSource(SourceEnum.SYSTEM.getCode());
        entity.setStatus(g.getStatus());
        entity.setSortIndex(g.getSortIndex());
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysGroupService.updateById(entity);
        } else {
            sysGroupService.save(entity);
        }
        return entity;
    }

    private void resolveOrCreateMenuResource(Long tenantId, Long menuId, Long resourceId, MenuResourceExportDto mr) {
        SysMenuResource existing = sysMenuResourceService.getOne(Wrappers.<SysMenuResource>lambdaQuery()
                .eq(SysMenuResource::getTenantId, tenantId).eq(SysMenuResource::getMenuId, menuId)
                .eq(SysMenuResource::getResourceId, resourceId).eq(SysMenuResource::getYn, YnEnum.Y.getKey()));
        SysMenuResource entity = existing != null ? existing : new SysMenuResource();
        entity.setTenantId(tenantId);
        entity.setMenuId(menuId);
        entity.setResourceId(resourceId);
        entity.setResourceBindType(mr.getResourceBindType() != null ? mr.getResourceBindType() : 0);
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysMenuResourceService.updateById(entity);
        } else {
            sysMenuResourceService.save(entity);
        }
    }

    private void resolveOrCreateRoleMenu(Long tenantId, Long roleId, Long menuId, RoleMenuExportDto rm, String resourceTreeJson) {
        SysRoleMenu existing = sysRoleMenuService.getOne(Wrappers.<SysRoleMenu>lambdaQuery()
                .eq(SysRoleMenu::getTenantId, tenantId).eq(SysRoleMenu::getRoleId, roleId)
                .eq(SysRoleMenu::getMenuId, menuId).eq(SysRoleMenu::getYn, YnEnum.Y.getKey()));
        SysRoleMenu entity = existing != null ? existing : new SysRoleMenu();
        entity.setTenantId(tenantId);
        entity.setRoleId(roleId);
        entity.setMenuId(menuId);
        entity.setMenuBindType(rm.getMenuBindType() != null ? rm.getMenuBindType() : 0);
        entity.setResourceTreeJson(resourceTreeJson);
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysRoleMenuService.updateById(entity);
        } else {
            sysRoleMenuService.save(entity);
        }
    }

    private void resolveOrCreateGroupMenu(Long tenantId, Long groupId, Long menuId, GroupMenuExportDto gm, String resourceTreeJson) {
        SysGroupMenu existing = sysGroupMenuService.getOne(Wrappers.<SysGroupMenu>lambdaQuery()
                .eq(SysGroupMenu::getTenantId, tenantId).eq(SysGroupMenu::getGroupId, groupId)
                .eq(SysGroupMenu::getMenuId, menuId).eq(SysGroupMenu::getYn, YnEnum.Y.getKey()));
        SysGroupMenu entity = existing != null ? existing : new SysGroupMenu();
        entity.setTenantId(tenantId);
        entity.setGroupId(groupId);
        entity.setMenuId(menuId);
        entity.setMenuBindType(gm.getMenuBindType() != null ? gm.getMenuBindType() : 0);
        entity.setResourceTreeJson(resourceTreeJson);
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysGroupMenuService.updateById(entity);
        } else {
            sysGroupMenuService.save(entity);
        }
    }

    private void resolveOrCreateDataPermission(Long tenantId, DataPermissionExportDto dp, Long targetId) {
        SysDataPermission existing = sysDataPermissionService.getOne(Wrappers.<SysDataPermission>lambdaQuery()
                .eq(SysDataPermission::getTenantId, tenantId).eq(SysDataPermission::getTargetType, dp.getTargetType())
                .eq(SysDataPermission::getTargetId, targetId).eq(SysDataPermission::getYn, YnEnum.Y.getKey()));
        SysDataPermission entity = existing != null ? existing : new SysDataPermission();
        entity.setTenantId(tenantId);
        entity.setTargetType(dp.getTargetType());
        entity.setTargetId(targetId);
        entity.setTokenLimit(dp.getTokenLimit());
        entity.setMaxSpaceCount(dp.getMaxSpaceCount());
        entity.setMaxAgentCount(dp.getMaxAgentCount());
        entity.setMaxPageAppCount(dp.getMaxPageAppCount());
        entity.setMaxKnowledgeCount(dp.getMaxKnowledgeCount());
        entity.setKnowledgeStorageLimitGb(dp.getKnowledgeStorageLimitGb());
        entity.setMaxDataTableCount(dp.getMaxDataTableCount());
        entity.setMaxScheduledTaskCount(dp.getMaxScheduledTaskCount());
        entity.setAgentComputerCpuCores(dp.getAgentComputerCpuCores());
        entity.setAgentComputerMemoryGb(dp.getAgentComputerMemoryGb());
        entity.setAgentComputerSwapGb(dp.getAgentComputerSwapGb());
        entity.setAgentFileStorageDays(dp.getAgentFileStorageDays());
        entity.setAgentDailyPromptLimit(dp.getAgentDailyPromptLimit());
        entity.setPageDailyPromptLimit(dp.getPageDailyPromptLimit());
        entity.setCreatorId(0L);
        entity.setCreator(CREATOR_SYSTEM);
        entity.setModifierId(null);
        entity.setModifier(null);
        entity.setYn(YnEnum.Y.getKey());

        if (existing != null) {
            sysDataPermissionService.updateById(entity);
        } else {
            sysDataPermissionService.save(entity);
        }
    }

    private String buildResourceTreeJson(List<ResourceNodeExportDto> tree, Map<String, Long> resourceCodeToId) {
        if (CollectionUtils.isEmpty(tree)) {
            return null;
        }
        List<ResourceNode> nodes = tree.stream()
                .map(n -> convertExportToNode(n, resourceCodeToId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (nodes.isEmpty()) {
            return null;
        }
        return JsonSerializeUtil.toJSONString(nodes);
    }

    private ResourceNode convertExportToNode(ResourceNodeExportDto dto, Map<String, Long> resourceCodeToId) {
        if (dto == null || StringUtils.isBlank(dto.getCode())) {
            return null;
        }
        Long id = resourceCodeToId.get(dto.getCode());
        if (id == null) {
            return null;
        }
        ResourceNode node = new ResourceNode();
        node.setId(id);
        node.setResourceBindType(dto.getResourceBindType());
        if (CollectionUtils.isNotEmpty(dto.getChildren())) {
            node.setChildren(dto.getChildren().stream()
                    .map(c -> convertExportToNode(c, resourceCodeToId))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        return node;
    }
}
