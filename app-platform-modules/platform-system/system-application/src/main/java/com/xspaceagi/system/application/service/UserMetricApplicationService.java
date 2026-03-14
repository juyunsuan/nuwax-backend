package com.xspaceagi.system.application.service;

import com.xspaceagi.system.application.dto.UserMetricDto;

import java.util.List;

public interface UserMetricApplicationService {

    /**
     * 根据用户ID查询该用户的所有计量数据
     */
    List<UserMetricDto> queryByUserId(Long userId);
}
