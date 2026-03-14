package com.xspaceagi.agent.core.adapter.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("published")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Published {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("_tenant_id")
    private Long tenantId;

    private Long spaceId;

    @TableField("user_id")
    private Long userId;

    @TableField("target_type")
    private TargetType targetType;

    private TargetSubType targetSubType;

    @TableField("target_id")
    private Long targetId;

    private String name;

    private String description;

    private String icon;

    private String config;

    private String remark;

    private PublishChannel channel;

    private PublishScope scope;

    private String category;

    private Integer allowCopy;

    private Integer onlyTemplate;

    private Integer accessControl;

    private Date modified;

    private Date created;

    public enum PublishStatus {
        Developing,
        Applying,
        Published,
        Rejected
    }

    //发布渠道:Square 广场;Space空间
    public enum PublishChannel {
        Square,
        System,
        Space
    }

    //Tenant', 'Global'
    public enum PublishScope {
        //空间
        Space,
        Tenant,
        Global
    }

    public enum TargetType {
        Agent,
        Plugin,
        Workflow,
        Knowledge,
        Table,
        Skill
    }

    public enum TargetSubType {
        Multi,
        Single,
        WorkflowChat,
        ChatBot,
        TaskAgent,
        Agent,
        PageApp
    }

    //根据targetType返回名称
    public static String getTargetTypeName(TargetType targetType) {
        return switch (targetType) {
            case Agent -> "智能体";
            case Plugin -> "插件";
            case Workflow -> "工作流";
            case Knowledge -> "知识库";
            case Table -> "数据表";
            case Skill -> "技能";
            default -> "Unknown";
        };
    }
}
