package com.xspaceagi.knowledge.core.application.service.impl;

import com.xspaceagi.knowledge.core.application.service.IKnowledgeConfigApplicationService;
import com.xspaceagi.knowledge.core.application.service.IKnowledgeFullTextSyncService;
import com.xspaceagi.knowledge.core.application.vo.KnowledgeConfigApplicationRequestVo;
import com.xspaceagi.knowledge.domain.model.KnowledgeConfigModel;
import com.xspaceagi.knowledge.domain.repository.IKnowledgeConfigRepository;
import com.xspaceagi.knowledge.domain.service.IKnowledgeConfigDomainService;
import com.xspaceagi.system.sdk.permission.SpacePermissionService;
import com.xspaceagi.system.spec.common.UserContext;
import com.xspaceagi.system.spec.exception.BizExceptionCodeEnum;
import com.xspaceagi.system.spec.exception.KnowledgeException;
import com.xspaceagi.system.spec.page.PageQueryParamVo;
import com.xspaceagi.system.spec.page.PageQueryVo;
import com.xspaceagi.system.spec.page.SuperPage;
import com.xspaceagi.system.infra.service.QueryVoListDelegateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class KnowledgeConfigApplicationService implements IKnowledgeConfigApplicationService {

    @Resource
    private IKnowledgeConfigDomainService knowledgeConfigDomainService;

    @Resource
    private SpacePermissionService spacePermissionService;

    @Resource
    private IKnowledgeConfigRepository knowledgeConfigRepository;
    @Resource
    private QueryVoListDelegateService queryVoListDelegateService;

    @Resource
    private IKnowledgeFullTextSyncService fullTextSyncService;

    @Override
    public SuperPage<KnowledgeConfigModel> querySearchConfigs(
            PageQueryVo<KnowledgeConfigApplicationRequestVo> pageQueryVo) {

        var filter = pageQueryVo.getQueryFilter();
        pageQueryVo.setQueryFilter(filter);

        PageQueryParamVo pageQueryParamVo = new PageQueryParamVo(pageQueryVo);

        SuperPage<KnowledgeConfigModel> superPage = this.queryVoListDelegateService.queryVoList(
                this.knowledgeConfigRepository,
                pageQueryParamVo, null);

        return superPage;
    }

    @Override
    public KnowledgeConfigModel queryOneInfoById(Long id) {
        return this.knowledgeConfigDomainService.queryOneInfoById(id);
    }

    @Override
    public void deleteById(Long id, UserContext userContext) {
        var existObj = this.knowledgeConfigDomainService.queryOneInfoById(id);
        if (Objects.isNull(existObj)) {
            throw KnowledgeException.build(BizExceptionCodeEnum.KNOWLEDGE_ERROR_5001);
        }
        // 校验用户和空间对应权限
        var spaceId = existObj.getSpaceId();
        spacePermissionService.checkSpaceUserPermission(spaceId);

        // 删除知识库（包括数据库、向量库、全文检索，在 Domain 层事务内处理）
        this.knowledgeConfigDomainService.deleteById(id, userContext);
    }

    @Override
    public Long updateInfo(KnowledgeConfigModel model, UserContext userContext) {

        var existObj = this.knowledgeConfigDomainService.queryOneInfoById(model.getId());
        if (Objects.isNull(existObj)) {
            throw KnowledgeException.build(BizExceptionCodeEnum.KNOWLEDGE_ERROR_5001);
        }
        // 校验用户和空间对应权限
        var spaceId = existObj.getSpaceId();
        spacePermissionService.checkSpaceUserPermission(spaceId);

        return this.knowledgeConfigDomainService.updateInfo(model, userContext);
    }

    @Override
    public Long addInfo(KnowledgeConfigModel model, UserContext userContext) {
        var spaceId = model.getSpaceId();
        spacePermissionService.checkSpaceUserPermission(spaceId);

        return this.knowledgeConfigDomainService.addInfo(model, userContext);
    }

    @Override
    public List<KnowledgeConfigModel> queryListBySpaceId(Long spaceId) {
        return knowledgeConfigDomainService.queryListBySpaceId(spaceId);
    }

    @Override
    public Long queryTotalFileSize(Long kbId) {
        return this.knowledgeConfigDomainService.queryTotalFileSize(kbId);
    }

}
