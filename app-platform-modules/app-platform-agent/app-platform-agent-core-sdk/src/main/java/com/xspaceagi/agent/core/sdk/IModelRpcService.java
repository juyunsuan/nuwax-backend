package com.xspaceagi.agent.core.sdk;

import org.springframework.core.ParameterizedTypeReference;

public interface IModelRpcService {

    <T> T call(String sysPrompt, String userPrompt, ParameterizedTypeReference<T> type);
}
