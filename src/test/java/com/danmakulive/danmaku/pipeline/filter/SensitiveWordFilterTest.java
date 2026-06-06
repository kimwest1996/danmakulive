package com.danmakulive.danmaku.pipeline.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter();
        filter.init();
    }

    @Test
    void noSensitiveWordReturnsOriginal() {
        String result = filter.filter("普通弹幕内容");
        assertEquals("普通弹幕内容", result);
    }

    @Test
    void singleSensitiveWordReplaced() {
        String result = filter.filter("包含敏感词1的内容");
        assertEquals("包含****的内容", result);
    }

    @Test
    void multipleSensitiveWordsReplaced() {
        String result = filter.filter("敏感词1和敏感词2混在一起");
        assertEquals("****和****混在一起", result);
    }

    @Test
    void emptyTextReturnsEmpty() {
        String result = filter.filter("");
        assertEquals("", result);
    }

    @Test
    void nullTextReturnsNull() {
        assertNull(filter.filter(null));
    }

    @Test
    void englishSensitiveWordReplaced() {
        String result = filter.filter("what the fuck is this");
        assertEquals("what the **** is this", result);
    }
}
