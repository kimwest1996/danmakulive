package com.danmakulive.benchmark;

import com.danmakulive.danmaku.pipeline.filter.SensitiveWordFilter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class SensitiveWordFilterBenchmark {

    private DFAFilter filter;
    private String textShort_10chars;
    private String textShort_50chars;
    private String textShort_200chars;
    private String textWithMatch;
    private String textAllMatch;
    private String textNoMatch;

    @Setup
    public void setup() throws Exception {
        filter = new DFAFilter();

        ClassPathResource resource = new ClassPathResource("sensitive_words.txt");
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                filter.addWord(line);
            }
        }
        reader.close();

        // Generate test strings
        textShort_10chars = generateChineseText(10);
        textShort_50chars = generateChineseText(50);
        textShort_200chars = generateChineseText(200);
        textNoMatch = generateChineseText(200);
        textWithMatch = generateChineseText(180) + "敏感词1" + generateChineseText(10);
        textAllMatch = "敏感词1敏感词2测试敏感词fuckshit敏感词1";
    }

    private String generateChineseText(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) (0x4e00 + (i % 20902)));
        }
        return sb.toString();
    }

    @Benchmark
    public void filter_10chars(Blackhole bh) {
        bh.consume(filter.filter(textShort_10chars));
    }

    @Benchmark
    public void filter_50chars(Blackhole bh) {
        bh.consume(filter.filter(textShort_50chars));
    }

    @Benchmark
    public void filter_200chars(Blackhole bh) {
        bh.consume(filter.filter(textShort_200chars));
    }

    @Benchmark
    public void filter_noMatch(Blackhole bh) {
        bh.consume(filter.filter(textNoMatch));
    }

    @Benchmark
    public void filter_withMatch(Blackhole bh) {
        bh.consume(filter.filter(textWithMatch));
    }

    @Benchmark
    public void filter_allMatch(Blackhole bh) {
        bh.consume(filter.filter(textAllMatch));
    }

    /**
     * Inline DFA filter to avoid Spring dependency in benchmark.
     * Same algorithm as SensitiveWordFilter.java.
     */
    static class DFAFilter {
        private static final char REPLACE_CHAR = '*';
        private final Map<Character, Object> root = new HashMap<>();

        @SuppressWarnings("unchecked")
        void addWord(String word) {
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
            node.put(null, Boolean.TRUE);
        }

        @SuppressWarnings("unchecked")
        String filter(String text) {
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

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(SensitiveWordFilterBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
