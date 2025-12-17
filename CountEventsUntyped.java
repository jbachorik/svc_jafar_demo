///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.btrace:jafar-parser:0.3.11
//REPOS mavencentral,sonatype-snapshots=https://oss.sonatype.org/content/repositories/snapshots/

import io.jafar.parser.api.JafarParser;
import io.jafar.parser.api.MetadataLookup;
import io.jafar.parser.api.ParserContext;
import io.jafar.parser.api.UntypedJafarParser;
import io.jafar.parser.impl.UntypedParserContextFactory;
import io.jafar.parser.internal_api.ChunkParserListener;
import io.jafar.parser.internal_api.ParserContextFactory;
import io.jafar.parser.internal_api.StreamingChunkParser;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class CountEventsUntyped {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: Demo <path-to-jfr-file>");
            System.exit(1);
        }

        Path recording = Path.of(args[0]);

        long ts = System.nanoTime();

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        Map<String, Long> eventCounts = new ConcurrentHashMap<>();
        try (UntypedJafarParser parser = JafarParser.newUntypedParser(recording)) {
            parser.handle((type, event, ctl) -> {
                eventCounts.merge(type.getName(), 1L, Long::sum);
            });
            parser.run();
        }
/// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        long duration = System.nanoTime() - ts;
        System.out.println("Event counts:");
        eventCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .forEach(e -> System.out.printf("  %s: %,d%n", e.getKey(), e.getValue()));

        System.out.println("Collected in " + (duration / 1_000_000L) + "ms");
    }
}
