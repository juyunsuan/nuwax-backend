package com.xspaceagi.agent.core.adapter.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TopicGenDto implements Serializable {

    @JsonPropertyDescription("符合上下文交流的标题，控制在5到15字，禁止生成与主题意义不想干的标题，对于毫无意义的上下文，例如用户输入错误或者随意输入的内容不用总结，返回空即可")
    private String topic;

}
