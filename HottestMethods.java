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

public class HottestMethods {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: HottestMethods <path-to-jfr-file>");
            System.exit(1);
        }

        Path recording = Path.of(args[0]);

        long ts = System.nanoTime();
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        ConcurrentHashMap<String, LongAdder> methodSamplesTmp = new ConcurrentHashMap<>();

        try (TypedJafarParser parser = JafarParser.newTypedParser(recording)) {
            // Handle jdk.ExecutionSample events
            parser.handle(JFRJdkExecutionSample.class, (event, ctl) -> {
                processExecutionSample(event.stackTrace(), methodSamplesTmp);
            });

            // Handle datadog.ExecutionSample events
            parser.handle(JFRDatadogExecutionSample.class, (event, ctl) -> {
                processExecutionSample(event.stackTrace(), methodSamplesTmp);
            });

            parser.run();
        }

        Map<String, Long> methodSamples = new HashMap<>();
        methodSamplesTmp.forEach((method, adder) -> methodSamples.put(method, adder.sum()));
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        long duration = System.nanoTime() - ts;

        System.out.println("Top 10 Hottest Methods:");
        methodSamples.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(e -> System.out.printf("  %s: %,d samples%n", e.getKey(), e.getValue()));

        System.out.println("\nAnalyzed in " + (duration / 1_000_000L) + "ms");
    }

    private static void processExecutionSample(JFRJdkTypesStackTrace stackTrace, ConcurrentHashMap<String, LongAdder> methodSamples) {
        if (stackTrace != null) {
            JFRJdkTypesStackFrame[] frames = stackTrace.frames();
            if (frames != null && frames.length > 0) {
                JFRJdkTypesStackFrame topFrame = frames[0];
                JFRJdkTypesMethod method = topFrame.method();
                if (method != null) {
                    JFRJavaLangClass clazz = method.type();
                    String className = clazz != null ? clazz.name() : "Unknown";
                    String methodName = method.name();
                    String signature = className + "." + methodName;
                    methodSamples.computeIfAbsent(signature, k -> new LongAdder()).increment();
                }
            }
        }
    }
}
