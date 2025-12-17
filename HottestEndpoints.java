///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.btrace:jafar-parser:0.3.11
//REPOS mavencentral,sonatype-snapshots=https://oss.sonatype.org/content/repositories/snapshots/
//SOURCES ../jafar/demo/build/generated/sources/jafar/**/*.java

import io.jafar.parser.api.JafarParser;
import io.jafar.parser.api.TypedJafarParser;
import io.jafar.demo.types.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class HottestEndpoints {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: HottestEndpoints <path-to-jfr-file>");
            System.exit(1);
        }

        Path recording = Path.of(args[0]);

        long ts = System.nanoTime();
        Map<String, Long> endpointSamples = findHottestEndpoints(recording);
        long duration = System.nanoTime() - ts;

        System.out.println("Top 3 Hottest Endpoints:");
        endpointSamples.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .forEach(e -> System.out.printf("  %s: %,d samples%n", e.getKey(), e.getValue()));

        System.out.println("\nAnalyzed in " + (duration / 1_000_000L) + "ms");
    }

    private static Map<String, Long> findHottestEndpoints(Path recording) throws Exception {
        // Map localRootSpanId -> endpoint name
        ConcurrentHashMap<Long, String> spanIdToEndpoint = new ConcurrentHashMap<>();

        // Map localRootSpanId -> sample count
        ConcurrentHashMap<Long, LongAdder> spanIdSamples = new ConcurrentHashMap<>();
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        try (TypedJafarParser parser = JafarParser.newTypedParser(recording)) {
            // First, collect endpoint names by localRootSpanId
            parser.handle(JFRDatadogEndpoint.class, (event, ctl) -> {
                long spanId = event.localRootSpanId();
                String endpoint = event.endpoint();
                if (spanId != 0 && endpoint != null && !endpoint.isEmpty()) {
                    spanIdToEndpoint.put(spanId, endpoint);
                }
            });

            // Then, count execution samples by localRootSpanId
            parser.handle(JFRDatadogExecutionSample.class, (event, ctl) -> {
                long spanId = event.localRootSpanId();
                if (spanId != 0) {
                    spanIdSamples.computeIfAbsent(spanId, k -> new LongAdder()).increment();
                }
            });

            parser.run();
        }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // Aggregate samples by endpoint name
        ConcurrentHashMap<String, LongAdder> endpointCounts = new ConcurrentHashMap<>();
        spanIdSamples.forEach((spanId, samples) -> {
            String endpoint = spanIdToEndpoint.get(spanId);
            if (endpoint != null) {
                endpointCounts.computeIfAbsent(endpoint, k -> new LongAdder()).add(samples.sum());
            }
        });

        // Convert to final result map
        Map<String, Long> result = new HashMap<>();
        endpointCounts.forEach((endpoint, adder) -> result.put(endpoint, adder.sum()));
        return result;
    }
}
