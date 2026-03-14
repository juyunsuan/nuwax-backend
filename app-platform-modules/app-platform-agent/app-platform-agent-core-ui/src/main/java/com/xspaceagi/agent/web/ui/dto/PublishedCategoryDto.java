package com.xspaceagi.agent.web.ui.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PublishedCategoryDto implements Serializable {

    private String key;

    private String label;

    private String icon;

    private CategoryType type;

    private List<PublishedCategoryDto> children;

    public enum CategoryType {
        Agent, Plugin, Workflow, Template, Skill, Component, PageApp
    }
}
