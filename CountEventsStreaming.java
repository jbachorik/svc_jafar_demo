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

public class CountEventsStreaming {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: Demo <path-to-jfr-file>");
            System.exit(1);
        }

        Path recording = Path.of(args[0]);

        long ts = System.nanoTime();
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        ConcurrentHashMap<String, LongAdder> eventCountsTmp = new ConcurrentHashMap<>();
        ParserContextFactory contextFactory = new UntypedParserContextFactory();

        try (StreamingChunkParser parser = new StreamingChunkParser(contextFactory)) {
            parser.parse(recording, new ChunkParserListener() {
                private final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, LongAdder>> perChunkTypeIdCounts = new ConcurrentHashMap<>();

                @Override
                public boolean onChunkStart(ParserContext context, int chunkIndex, io.jafar.parser.internal_api.ChunkHeader header) {
                    // Create a new map for this chunk
                    perChunkTypeIdCounts.put(chunkIndex, new ConcurrentHashMap<>());
                    return true;
                }

                @Override
                public boolean onEvent(ParserContext context, long typeId, long eventStartPos, long rawSize, long payloadSize) {
                    int chunkIndex = context.getChunkIndex();
                    ConcurrentHashMap<Long, LongAdder> chunkCounts = perChunkTypeIdCounts.get(chunkIndex);
                    if (chunkCounts != null) {
                        chunkCounts.computeIfAbsent(typeId, k -> new LongAdder()).increment();
                    }
                    return true;
                }

                @Override
                public boolean onChunkEnd(ParserContext context, int chunkIndex, boolean skipped) {
                    if (!skipped) {
                        // Get and remove this chunk's counts
                        ConcurrentHashMap<Long, LongAdder> chunkCounts = perChunkTypeIdCounts.remove(chunkIndex);
                        if (chunkCounts != null) {
                            // Resolve type IDs using THIS chunk's metadata
                            MetadataLookup metadata = context.getMetadataLookup();
                            chunkCounts.forEach((typeId, adder) -> {
                                MetadataClass clazz = metadata.getClass(typeId);
                                if (clazz != null) {
                                    // Skip simple types (primitives like byte, short, etc.)
                                    if (!clazz.isSimpleType()) {
                                        String typeName = clazz.getName();
                                        eventCountsTmp.computeIfAbsent(typeName, k -> new LongAdder()).add(adder.sum());
                                    }
                                } else {
                                    // Type ID not found in metadata - this might indicate a corrupted recording
                                    String typeName = "Unknown[" + typeId + "]";
                                    eventCountsTmp.computeIfAbsent(typeName, k -> new LongAdder()).add(adder.sum());
                                }
                            });
                        }
                    }
                    return true;
                }
            });
        } catch (Exception e) {
            throw new IOException("Failed to parse recording", e);
        }

        Map<String, Long> eventCounts = new HashMap<>();
        eventCountsTmp.forEach((typeName, adder) -> eventCounts.put(typeName, adder.sum()));
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        long duration = System.nanoTime() - ts;
        System.out.println("Event counts:");
        eventCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .forEach(e -> System.out.printf("  %s: %,d%n", e.getKey(), e.getValue()));

        System.out.println("Collected in " + (duration / 1_000_000L) + "ms");
    }
}
