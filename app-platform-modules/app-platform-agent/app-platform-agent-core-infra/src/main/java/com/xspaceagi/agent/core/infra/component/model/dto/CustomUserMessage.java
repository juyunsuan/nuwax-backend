package com.xspaceagi.agent.core.infra.component.model.dto;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Media;

import java.util.Date;
import java.util.List;

public class CustomUserMessage extends UserMessage {

    private Date time;

    public CustomUserMessage(String content) {
        super(content);
    }

    public CustomUserMessage(String content, List<Media> mediaList, Date time) {
        super(content, mediaList);
        this.metadata.put("time", time);
    }

    public Date getTime() {
        return time;
    }
}
