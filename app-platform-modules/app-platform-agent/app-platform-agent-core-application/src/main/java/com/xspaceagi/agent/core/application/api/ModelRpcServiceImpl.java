package com.xspaceagi.agent.core.application.api;

import com.xspaceagi.agent.core.adapter.application.ModelApplicationService;
import com.xspaceagi.agent.core.sdk.IModelRpcService;
import jakarta.annotation.Resource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

@Service
public class ModelRpcServiceImpl implements IModelRpcService {

    @Resource
    private ModelApplicationService modelApplicationService;

    @Override
    public <T> T call(String sysPrompt, String userPrompt, ParameterizedTypeReference<T> type) {
        return modelApplicationService.call(sysPrompt, userPrompt, type);
    }
}
