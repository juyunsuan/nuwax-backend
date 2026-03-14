package com.xspaceagi.agent.web.ui.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "推送技能到智能体请求DTO")
public class PushSkillDto implements Serializable {

    private Long skillId;

    @JsonProperty("cId")
    private Long cId;
}
