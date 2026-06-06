package com.danmakulive.danmaku.pipeline.filter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);
    private static final char REPLACE_CHAR = '*';

    private final Map<Character, Object> root = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("sensitive_words.txt");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    addWord(line);
                    count++;
                }
            }
            reader.close();
            log.info("Loaded {} sensitive words", count);
        } catch (Exception e) {
            log.warn("Failed to load sensitive_words.txt: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void addWord(String word) {
        Map<Character, Object> node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Object child = node.get(c);
            if (child == null) {
                child = new HashMap<Character, Object>();
                node.put(c, child);
            }
            node = (Map<Character, Object>) child;
        }
        node.put(null, Boolean.TRUE); // 词尾标记
    }

    @SuppressWarnings("unchecked")
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text);
        int len = text.length();

        for (int i = 0; i < len; i++) {
            Map<Character, Object> node = root;
            int matchEnd = -1;

            for (int j = i; j < len; j++) {
                char c = text.charAt(j);
                Object child = node.get(c);
                if (child == null) {
                    break;
                }
                node = (Map<Character, Object>) child;
                if (node.containsKey(null)) {
                    matchEnd = j;
                }
            }

            if (matchEnd >= 0) {
                for (int k = i; k <= matchEnd; k++) {
                    result.setCharAt(k, REPLACE_CHAR);
                }
                i = matchEnd;
            }
        }

        return result.toString();
    }
}
