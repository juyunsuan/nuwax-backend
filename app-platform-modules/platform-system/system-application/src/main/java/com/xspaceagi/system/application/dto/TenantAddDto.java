package com.xspaceagi.system.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TenantAddDto {

    @NotNull(message = "租户名称不能为空")
    private String name;

    private String description;

    private String logo;

    @NotNull(message = "租户域名不能为空")
    private String domain;

    @NotNull(message = "租户管理员用户名不能为空")
    private String userName;

    private String nickName;

    @NotNull(message = "租户管理员手机号不能为空")
    private String phone;

    @NotNull(message = "租户管理员邮箱不能为空")
    private String email;

    @NotNull(message = "租户管理员密码不能为空")
    private String password;
}
