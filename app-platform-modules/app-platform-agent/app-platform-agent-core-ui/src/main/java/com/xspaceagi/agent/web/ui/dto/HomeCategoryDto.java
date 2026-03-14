package com.xspaceagi.agent.web.ui.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeCategoryDto {

    private String name;

    private String icon;

    private String type;

    private HomeCategoryTypeEnum categoryType;

    private Integer sort;

    public enum HomeCategoryTypeEnum {

        //智能体收藏
        AGENT_COLLECT("我的收藏"),
        //智能体推荐
        AGENT_RECOMMEND("官方推荐"),

        PERSONAL_SPACE("个人空间"),

        TEAM_SPACE("团队空间");

        private String name;

        HomeCategoryTypeEnum(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static String getSpaceTypeName(Long id) {
        return "SPACE_" + id;
    }
}
