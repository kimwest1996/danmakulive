package com.danmakulive.common;

import com.danmakulive.common.result.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void successWithData() {
        Result<String> r = Result.success("hello");
        assertTrue(r.isSuccess());
        assertEquals("0", r.getCode());
        assertEquals("hello", r.getData());
    }

    @Test
    void successVoid() {
        Result<Void> r = Result.success();
        assertTrue(r.isSuccess());
        assertNull(r.getData());
    }

    @Test
    void failure() {
        Result<Void> r = Result.failure("A000001", "error");
        assertFalse(r.isSuccess());
        assertEquals("A000001", r.getCode());
        assertEquals("error", r.getMessage());
    }
}
