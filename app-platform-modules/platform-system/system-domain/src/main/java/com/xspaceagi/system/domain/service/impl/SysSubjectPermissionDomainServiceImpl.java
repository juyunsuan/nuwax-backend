package com.xspaceagi.system.domain.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xspaceagi.system.domain.service.SysSubjectPermissionDomainService;
import com.xspaceagi.system.infra.dao.entity.SysSubjectPermission;
import com.xspaceagi.system.infra.dao.service.SysSubjectPermissionService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.enums.PermissionSubjectTypeEnum;
import com.xspaceagi.system.spec.enums.PermissionTargetTypeEnum;
import com.xspaceagi.system.spec.enums.YnEnum;
import com.xspaceagi.system.spec.exception.BizException;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysSubjectPermissionDomainServiceImpl implements SysSubjectPermissionDomainService {

    @Resource
    private SysSubjectPermissionService sysSubjectPermissionService;

    @Override
    public List<SysSubjectPermission> getBySubject(PermissionSubjectTypeEnum subjectType, Long subjectId) {
        if (subjectType == null) {
            throw new BizException("主体类型不能为空");
        }
        if (subjectId == null) {
            throw new BizException("主体ID不能为空");
        }
        return sysSubjectPermissionService.list(Wrappers.<SysSubjectPermission>lambdaQuery()
                .eq(SysSubjectPermission::getSubjectType, subjectType.getCode())
                .eq(SysSubjectPermission::getSubjectId, subjectId)
                .eq(SysSubjectPermission::getYn, YnEnum.Y.getKey()));
    }

    @Override
    public List<Long> listSubjectIdsByTarget(PermissionTargetTypeEnum targetType, Long targetId, PermissionSubjectTypeEnum subjectType) {
        if (targetType == null || subjectType == null) {
            throw new BizException("目标类型/主体类型不能为空");
        }
        if (targetId == null) {
            throw new BizException("目标ID不能为空");
        }
        return sysSubjectPermissionService.list(Wrappers.<SysSubjectPermission>lambdaQuery()
                        .eq(SysSubjectPermission::getTargetType, targetType.getCode())
                        .eq(SysSubjectPermission::getTargetId, targetId)
                        .eq(SysSubjectPermission::getSubjectType, subjectType.getCode())
                        .eq(SysSubjectPermission::getYn, YnEnum.Y.getKey()))
                .stream()
                .map(SysSubjectPermission::getSubjectId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public void replaceSubjectsByTarget(PermissionTargetTypeEnum targetType, Long targetId,
                                        PermissionSubjectTypeEnum subjectType, List<Long> subjectIds,
                                        UserContext userContext) {
        if (targetType == null || subjectType == null) {
            throw new BizException("目标类型/主体类型不能为空");
        }
        if (targetId == null) {
            throw new BizException("目标ID不能为空");
        }

        // 全量覆盖：先删除旧绑定
        sysSubjectPermissionService.remove(Wrappers.<SysSubjectPermission>lambdaQuery()
                .eq(SysSubjectPermission::getTargetType, targetType.getCode())
                .eq(SysSubjectPermission::getTargetId, targetId)
                .eq(SysSubjectPermission::getSubjectType, subjectType.getCode())
                .eq(SysSubjectPermission::getYn, YnEnum.Y.getKey()));

        if (CollectionUtils.isEmpty(subjectIds)) {
            return;
        }
        // 去重后插入
        Set<Long> uniq = new HashSet<>();
        for (Long sid : subjectIds) {
            if (sid != null) {
                uniq.add(sid);
            }
        }
        for (Long sid : uniq) {
            addTarget(subjectType, sid, targetType, targetId, userContext);
        }
    }

    @Override
    public void addTarget(PermissionSubjectTypeEnum subjectType, Long subjectId,
                          PermissionTargetTypeEnum targetType, Long targetId,
                          UserContext userContext) {
        if (subjectType == null || targetType == null) {
            throw new BizException("主体类型/目标类型不能为空");
        }
        if (subjectId == null || targetId == null) {
            throw new BizException("主体ID/目标ID不能为空");
        }

        SysSubjectPermission exist = sysSubjectPermissionService.getOne(Wrappers.<SysSubjectPermission>lambdaQuery()
                .eq(SysSubjectPermission::getSubjectType, subjectType.getCode())
                .eq(SysSubjectPermission::getSubjectId, subjectId)
                .eq(SysSubjectPermission::getTargetType, targetType.getCode())
                .eq(SysSubjectPermission::getTargetId, targetId)
                .eq(SysSubjectPermission::getYn, YnEnum.Y.getKey()));
        if (exist != null) {
            return;
        }

        SysSubjectPermission toSave = new SysSubjectPermission();
        toSave.setSubjectType(subjectType.getCode());
        toSave.setSubjectId(subjectId);
        toSave.setTargetType(targetType.getCode());
        toSave.setTargetId(targetId);
        toSave.setCreatorId(userContext.getUserId());
        toSave.setCreator(userContext.getUserName());
        toSave.setYn(YnEnum.Y.getKey());
        sysSubjectPermissionService.save(toSave);
    }

    @Override
    public void removeTarget(PermissionSubjectTypeEnum subjectType, Long subjectId,
                             PermissionTargetTypeEnum targetType, Long targetId,
                             UserContext userContext) {
        if (subjectType == null || targetType == null) {
            throw new BizException("主体类型/目标类型不能为空");
        }
        if (subjectId == null || targetId == null) {
            throw new BizException("主体ID/目标ID不能为空");
        }

        sysSubjectPermissionService.remove(Wrappers.<SysSubjectPermission>lambdaQuery()
                .eq(SysSubjectPermission::getSubjectType, subjectType.getCode())
                .eq(SysSubjectPermission::getSubjectId, subjectId)
                .eq(SysSubjectPermission::getTargetType, targetType.getCode())
                .eq(SysSubjectPermission::getTargetId, targetId));
    }

    @Override
    public void replaceTargetsBySubject(PermissionSubjectTypeEnum subjectType, Long subjectId,
                                         List<Long> roleIds, List<Long> groupIds,
                                         UserContext userContext) {
        if (subjectType == null) {
            throw new BizException("主体类型不能为空");
        }
        if (subjectId == null) {
            throw new BizException("主体ID不能为空");
        }

        // 全量覆盖：先删除该主体下所有目标绑定
        sysSubjectPermissionService.remove(Wrappers.<SysSubjectPermission>lambdaQuery()
                .eq(SysSubjectPermission::getSubjectType, subjectType.getCode())
                .eq(SysSubjectPermission::getSubjectId, subjectId)
                .eq(SysSubjectPermission::getYn, YnEnum.Y.getKey()));

        if (CollectionUtils.isEmpty(roleIds) && CollectionUtils.isEmpty(groupIds)) {
            return;
        }
        Set<Long> uniqRoleIds = new HashSet<>();
        if (roleIds != null) {
            roleIds.stream().filter(Objects::nonNull).forEach(uniqRoleIds::add);
        }
        Set<Long> uniqGroupIds = new HashSet<>();
        if (groupIds != null) {
            groupIds.stream().filter(Objects::nonNull).forEach(uniqGroupIds::add);
        }
        for (Long roleId : uniqRoleIds) {
            addTarget(subjectType, subjectId, PermissionTargetTypeEnum.ROLE, roleId, userContext);
        }
        for (Long groupId : uniqGroupIds) {
            addTarget(subjectType, subjectId, PermissionTargetTypeEnum.GROUP, groupId, userContext);
        }
    }
}
