package com.xspaceagi.log.spec;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 支持多种时间格式，并将 UTC 时间转换为东八区（北京时间）
 */
@Slf4j
public class FlexibleDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
    private static final DateTimeFormatter FORMATTER_WITH_NANOS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter FORMATTER_WITH_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter FORMATTER_WITHOUT_FRACTION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    // 东八区时区
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateString = p.getText().trim();
        if (StringUtils.isBlank(dateString)) {
            return null;
        }
        try {
            // 直接解析为 Instant，这是正确处理 UTC 时间的方式
            Instant instant;

            // 根据不同格式解析字符串为 Instant
            if (dateString.matches(".*\\.\\d{6}Z$")) {
                instant = Instant.from(FORMATTER_WITH_NANOS.parse(dateString));
            } else if (dateString.matches(".*\\.\\d{3}Z$")) {
                instant = Instant.from(FORMATTER_WITH_MILLIS.parse(dateString));
            } else {
                instant = Instant.from(FORMATTER_WITHOUT_FRACTION.parse(dateString));
            }

            // 直接将 Instant 转换为东八区的 LocalDateTime
            return LocalDateTime.ofInstant(instant, BEIJING_ZONE);

        } catch (DateTimeParseException e) {
            log.error("无法解析日期时间: {}", dateString, e);
            throw new JsonParseException(p, "无法解析日期时间: " + dateString, e);
        }
    }
}