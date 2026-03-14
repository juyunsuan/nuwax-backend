package com.xspaceagi.modelproxy.spec.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnthropicSSEParser {

    private static final Pattern INPUT_TOKENS_PATTERN = Pattern.compile("\"input_tokens\"\\s*:\\s*(\\d+)");
    private static final Pattern OUTPUT_TOKENS_PATTERN = Pattern.compile("\"output_tokens\"\\s*:\\s*(\\d+)");
    private static final Pattern TEXT_DELTA_PATTERN = Pattern.compile("\"type\"\\s*:\\s*\"text_delta\"");
    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");

    public static TokenUsage extractTokenUsage(String sseData) {
        TokenUsage usage = new TokenUsage();
        StringBuilder textBuilder = new StringBuilder();

        String[] lines = sseData.split("\n");

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();

                if (data.equals("[DONE]")) {
                    continue;
                }

                Matcher inputMatcher = INPUT_TOKENS_PATTERN.matcher(data);
                if (inputMatcher.find()) {
                    usage.inputTokens = Integer.parseInt(inputMatcher.group(1));
                }

                Matcher outputMatcher = OUTPUT_TOKENS_PATTERN.matcher(data);
                if (outputMatcher.find()) {
                    usage.outputTokens = Integer.parseInt(outputMatcher.group(1));
                }

                if (TEXT_DELTA_PATTERN.matcher(data).find()) {
                    usage.outputTokensDelta++;
                    Matcher textMatcher = TEXT_PATTERN.matcher(data);
                    if (textMatcher.find()) {
                        textBuilder.append(textMatcher.group(1));
                    }
                }
            }
        }

        usage.text = textBuilder.toString();
        return usage;
    }

    public static class TokenUsage {
        public long inputTokens = 0;
        public long outputTokens = 0;
        public int outputTokensDelta = 0;
        public String text = "";

        @Override
        public String toString() {
            return String.format("Input Tokens: %d, Output Tokens: %d, Text Delta Count: %d, Text: %s",
                    inputTokens, outputTokens, outputTokensDelta, text);
        }
    }
}
