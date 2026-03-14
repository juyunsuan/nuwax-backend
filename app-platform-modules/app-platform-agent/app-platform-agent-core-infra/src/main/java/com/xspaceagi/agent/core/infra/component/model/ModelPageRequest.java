package com.xspaceagi.agent.core.infra.component.model;

import com.xspaceagi.system.spec.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ModelPageRequest {

    @Resource
    private RedisUtil redisUtil;

    public String getPageRequestResult(String requestId, String dataType) {
        //从redis中读取内容，直到不为空，30秒超时
        Long start = System.currentTimeMillis();
        Object val = redisUtil.get(buildKey(requestId));
        while (val == null) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (System.currentTimeMillis() - start > 30000) {
                break;
            }
            val = redisUtil.get(buildKey(requestId));
        }
        return val == null ? "页面数据获取失败" : val.toString();
    }

    public void setPageRequestResult(String requestId, String result) {
        redisUtil.set(buildKey(requestId), result, 5);// 有效期5秒
    }

    private String buildKey(String requestId) {
        return "model.page.request:" + requestId;
    }
}
