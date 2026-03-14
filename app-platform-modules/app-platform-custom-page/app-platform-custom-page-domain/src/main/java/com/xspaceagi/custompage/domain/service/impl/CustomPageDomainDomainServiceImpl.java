package com.xspaceagi.custompage.domain.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.xspaceagi.custompage.domain.model.CustomPageDomainModel;
import com.xspaceagi.custompage.domain.repository.ICustomPageDomainRepository;
import com.xspaceagi.custompage.domain.service.ICustomPageDomainDomainService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.dto.ReqResult;
import com.xspaceagi.system.spec.exception.BizException;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustomPageDomainDomainServiceImpl implements ICustomPageDomainDomainService {

    @Resource
    private ICustomPageDomainRepository customPageDomainRepository;

    @Override
    public List<CustomPageDomainModel> listByProjectId(Long projectId) {
        return customPageDomainRepository.listByProjectId(projectId);
    }

    @Override
    public CustomPageDomainModel getById(Long id) {
        return customPageDomainRepository.getById(id);
    }

    @Override
    public ReqResult<CustomPageDomainModel> create(CustomPageDomainModel model, UserContext userContext) {
        log.info("[create] 创建域名绑定, projectId={}, domain={}", model.getProjectId(), model.getDomain());

        // 校验域名是否已存在
        CustomPageDomainModel existing = customPageDomainRepository.getByDomain(model.getDomain());
        if (existing != null) {
            log.error("[create] 域名已存在, domain={}", model.getDomain());
            return ReqResult.error("0001", "域名已存在");
        }

        model.setTenantId(userContext.getTenantId());
        model.setCreated(new Date());
        model.setModified(new Date());

        CustomPageDomainModel result = customPageDomainRepository.add(model);

        log.info("[create] 创建域名绑定成功, id={}", result.getId());
        return ReqResult.success(result);
    }

    @Override
    public ReqResult<CustomPageDomainModel> update(CustomPageDomainModel model, UserContext userContext) {
        log.info("[update] 更新域名绑定, id={}, domain={}", model.getId(), model.getDomain());

        CustomPageDomainModel existing = customPageDomainRepository.getById(model.getId());
        if (existing == null) {
            log.error("[update] 域名绑定不存在, id={}", model.getId());
            return ReqResult.error("0002", "域名绑定不存在");
        }

        // 校验租户
        if (!existing.getTenantId().equals(userContext.getTenantId())) {
            log.error("[update] 无权限操作该域名绑定, id={}", model.getId());
            throw new BizException("0003", "无权限操作该域名绑定");
        }

        // 校验域名是否已被其他记录使用
        CustomPageDomainModel domainDuplicate = customPageDomainRepository.getByDomain(model.getDomain());
        if (domainDuplicate != null && !domainDuplicate.getId().equals(model.getId())) {
            log.error("[update] 域名已被其他记录使用, domain={}", model.getDomain());
            return ReqResult.error("0004", "域名已被其他记录使用");
        }

        existing.setDomain(model.getDomain());
        existing.setProjectId(model.getProjectId());
        existing.setModified(new Date());

        customPageDomainRepository.updateById(existing);

        log.info("[update] 更新域名绑定成功, id={}", existing.getId());
        return ReqResult.success(existing);
    }

    @Override
    public ReqResult<Void> delete(Long id, UserContext userContext) {
        log.info("[delete] 删除域名绑定, id={}", id);

        CustomPageDomainModel existing = customPageDomainRepository.getById(id);
        if (existing == null) {
            log.error("[delete] 域名绑定不存在, id={}", id);
            return ReqResult.error("0005", "域名绑定不存在");
        }

        // 校验租户
        if (!existing.getTenantId().equals(userContext.getTenantId())) {
            log.error("[delete] 无权限操作该域名绑定, id={}", id);
            throw new BizException("0006", "无权限操作该域名绑定");
        }

        customPageDomainRepository.removeById(id);

        log.info("[delete] 删除域名绑定成功, id={}", id);
        return ReqResult.success(null);
    }

    @Override
    public List<String> listAllDomains() {
        return customPageDomainRepository.listAllDomains();
    }
}
