# JAFAR Introduction - 45 Minute Presentation Outline

**Target Audience**: Software Engineers
**Duration**: 45 minutes
**Format**: Talk + Live Demos + Interactive Q&A

---

## Presentation Structure

### Part 1: The Problem (5 minutes)
**Slides 1-3**

**Opening Hook** (1 min)
- "Who here has used JFR for profiling?" [Show of hands]
- "Who found parsing JFR files programmatically... painful?" [Laughter/agreement]
- Quick stat: "JFR files contain hundreds of event types, millions of events, nested structures"

**The JFR Parsing Challenge** (2 min)
- **Slide**: "Why Existing Solutions Fall Short"
  - JMC API: Heavy, requires full JMC stack, complex
  - Custom parsing: Binary format complexity, constant pool deduplication
  - Other tools: Limited programmatic access, slow, memory-hungry

**Real-World Use Cases** (2 min)
- **Slide**: "When You Need Programmatic JFR Access"
  - CI/CD performance regression detection
  - Automated profiling analysis in production
  - Custom dashboards and monitoring
  - Batch analysis of thousands of recordings
  - Export to data warehouses (Parquet, DuckDB, ClickHouse)

---

### Part 2: Enter JAFAR (5 minutes)
**Slides 4-6**

**What is JAFAR?** (2 min)
- **Slide**: "JAFAR: Fast, Minimal JFR Parser"
  - Experimental, focused API
  - No JMC dependencies
  - Streaming architecture
  - Zero-allocation parsing paths
  - Two complementary APIs

**Design Philosophy** (1 min)
- **Slide**: "Design Principles"
  - Minimal ceremony
  - Pay for what you use
  - Fast by default
  - Type safety when you want it
  - Flexibility when you need it

**Quick Architecture Overview** (2 min)
- **Slide**: "Architecture at a Glance"
  ```
  ┌─────────────────────┐
  │   Typed API         │  Compile-time safety
  │   @JfrType("...")   │  IDE auto-complete
  └──────────┬──────────┘
             │
  ┌──────────▼──────────┐
  │   Untyped API       │  Map-based, flexible
  │   Map<String, Obj>  │  Performance tuning
  └──────────┬──────────┘
             │
  ┌──────────▼──────────┐
  │   Core Parser       │  Streaming, fast
  │   Chunk-by-chunk    │  Memory efficient
  └─────────────────────┘
  ```

**TRANSITION**: "Let me show you how easy this is..."

---

### DEMO 1: Quick Win - Untyped API (7 minutes)
**Live Coding**

**Setup** (1 min)
- Open IDE with prepared project
- Show `pom.xml` or `build.gradle` - single dependency
- "This is literally all you need"

**Demo: Count Events in 10 Lines** (3 min)
```java
Map<String, Long> eventCounts = new HashMap<>();

try (UntypedJafarParser parser = UntypedJafarParser.open(recording)) {
    parser.handle((type, event, ctl) -> {
        eventCounts.merge(type.getName(), 1L, Long::sum);
    });
    parser.run();
}

eventCounts.forEach((type, count) ->
    System.out.printf("%s: %,d\n", type, count));
```

**Run It** (2 min)
- Execute against a real JFR file
- Show output: "Look, 280+ event types in seconds"
- Point out: "No dependencies, no setup, just works"

**Quick Enhancements** (1 min)
- Show filtering: `if (type.getName().startsWith("jdk.File"))`
- Show field access: `Long bytes = (Long) event.get("bytesRead")`

**INTERACTIVE**: "What events would YOU want to analyze?" [Take 1-2 suggestions]

---

### Part 3: The Typed API (6 minutes)
**Slides 7-9 + Mini Demo**

**The Problem with Maps** (1 min)
- **Slide**: "Untyped API Drawbacks"
  - Runtime casting
  - No IDE help
  - Typos in field names
  - Refactoring is hard

**Enter Type Safety** (2 min)
- **Slide**: "Typed API: Interfaces + Annotations"
  ```java
  @JfrType("jdk.ExecutionSample")
  interface ExecutionSample {
      long startTime();
      Thread sampledThread();
      StackTrace stackTrace();
  }
  ```
  - Compile-time safety
  - IDE auto-completion
  - Refactoring-friendly

**The Gradle Plugin Magic** (2 min)
- **Slide**: "Auto-Generate Types"
  ```gradle
  generateJafarTypes {
      inputFile = file('sample.jfr')
      targetPackage = 'com.example.types'
      eventTypeFilter { it.startsWith('jdk.') }
  }
  ```
  - Run once, get all types
  - No manual interface writing
  - Stays in sync with JFR metadata

**Quick Typed Demo** (1 min)
- Show pre-generated types in IDE
- Show auto-completion: `event.sampledThread().` [TAB]
- "This is the same performance, just type-safe"

**INTERACTIVE**: "Hands up if you'd use the typed API for production?" [Gauge interest]

---

### DEMO 2: Real Analysis - CPU Profiling (8 minutes)
**Live Coding - The Showstopper**

**Problem Setup** (1 min)
- "Let's find the top 10 hottest methods in this recording"
- Open a real production-like JFR file
- "This is 2GB, 10 million events"

**Live Code: CPU Profiler** (4 min)
```java
Map<String, Long> methodSamples = new HashMap<>();

try (TypedJafarParser parser = TypedJafarParser.open(recording)) {
    parser.handle(JFRExecutionSample.class, (event, ctl) -> {
        StackFrame[] frames = event.stackTrace().frames();
        if (frames.length > 0) {
            Method method = frames[0].method();
            String signature = method.type().name() + "." + method.name();
            methodSamples.merge(signature, 1L, Long::sum);
        }
    });
    parser.run();
}

// Print top 10
methodSamples.entrySet().stream()
    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
    .limit(10)
    .forEach(e -> System.out.printf("%s: %,d samples\n",
        e.getKey(), e.getValue()));
```

**Run It** (2 min)
- Show execution time: "Parsed 2GB in X seconds"
- Show results: Real hotspots
- "This is production-ready analysis code"

**Add Flamegraph Export** (1 min)
- Quickly add: "Want a flamegraph? 5 more lines"
- Show flamegraph-compatible output format
- "Pipe to speedscope or d3-flamegraph"

**INTERACTIVE**: "What other analyses would be useful?" [Take suggestions, show how easy they'd be]

---

### Part 4: The JFR Shell - Interactive Power (7 minutes)
**Slides 10-11 + Live Demo**

**The Problem** (1 min)
- **Slide**: "Ad-Hoc Analysis Pain"
  - Need to write code for every question
  - Compile, run, repeat
  - Slow iteration cycle
  - Hard to explore unknown data

**JFR Shell Introduction** (2 min)
- **Slide**: "Interactive JFR Exploration"
  - SQL-like query language (JfrPath)
  - Tab completion
  - Multiple sessions
  - Export capabilities

**DEMO: JFR Shell Live** (4 min)

*Start Shell*
```bash
./gradlew :jfr-shell:run --console=plain
```

*Show Basic Commands*
```bash
jfr> open recording.jfr
jfr> show events/jdk.ExecutionSample | count()
jfr> show events/jdk.ExecutionSample | groupBy(thread/name) | top(5)
```

*Show Power Query*
```bash
jfr> show events/jdk.FileRead[bytesRead>=1048576] | groupBy(path) | sum(bytesRead) | top(10)
```

*Show Metadata Exploration*
```bash
jfr> metadata class jdk.ExecutionSample --tree
jfr> help show
```

**Key Points**
- "Zero code, instant insights"
- "Tab completion shows available fields"
- "Great for exploration, then write code for automation"

**INTERACTIVE**: "Let's answer YOUR question" [Take one question, query it live]

---

### Part 5: Performance & Production (5 minutes)
**Slides 12-14**

**Performance Numbers** (2 min)
- **Slide**: "Benchmarks vs JMC"
  - Show comparative throughput
  - Memory usage comparison
  - Startup time comparison
  - "3-5x faster in our tests"

**Production Patterns** (2 min)
- **Slide**: "Real-World Deployments"
  - **CI/CD Integration**: Performance regression tests
  - **Production Monitoring**: Continuous profiling analysis
  - **Batch Processing**: Analyze 1000s of recordings
  - **Data Pipelines**: Export to warehouses

**ParsingContext for Performance** (1 min)
- **Slide**: "Optimize Multi-File Processing"
  ```java
  ParsingContext ctx = ParsingContext.create();
  for (Path file : recordings) {
      try (TypedJafarParser parser = TypedJafarParser.open(file, ctx)) {
          // Reuses generated bytecode, metadata
      }
  }
  ```
  - Caches generated handlers
  - Shares expensive resources
  - "Up to 10x faster for batch processing"

---

### Part 6: Advanced Features - Quick Tour (3 minutes)
**Slide 15**

**Slide**: "Beyond Basics"

**1. Chunk-Level Control** (30 sec)
```java
parser.withParserListener(new ChunkParserListener() {
    @Override
    public void onChunkStart(ParserContext ctx, ChunkInfo chunk) {
        System.out.println("Processing chunk: " + chunk.startTimestamp());
    }
});
```

**2. Untyped Performance Tuning** (30 sec)
```java
// Sparse access for filtering
parser = ctx.newUntypedParser(file, UntypedStrategy.SPARSE_ACCESS);

// Full iteration for export
parser = ctx.newUntypedParser(file, UntypedStrategy.FULL_ITERATION);
```

**3. Values Utility** (30 sec)
```java
// Navigate nested structures easily
Object methodName = Values.get(event, "stackTrace", "frames", 0, "method", "name");
```

**4. Control Flow** (30 sec)
```java
parser.handle((event, ctl) -> {
    if (found >= 100) {
        ctl.stop();  // Early termination
    }
});
```

**Quick mention**: "All in the docs, tutorials cover everything"

---

### Part 6B: Under the Hood - Architecture Deep Dive (5 minutes)
**Slides 16-18 - Technical Deep Dive**

> **Note**: This section showcases the clever engineering that makes JAFAR fast. Engineers love seeing "how it works" under the hood.

---

#### Slide 16: "The Secret Sauce - Dynamic Bytecode Generation"

**Explain**: "Here's why JAFAR is fast - we generate optimized bytecode at runtime"

```
┌─────────────────────────────────────────────────────────────┐
│         TYPED API: BYTECODE GENERATION MAGIC                │
└─────────────────────────────────────────────────────────────┘

Step 1: Your Code                  Step 2: First Parse
┌──────────────────────┐          ┌─────────────────────┐
│ parser.handle(       │          │ JFR Metadata        │
│   JFRExecutionSample,│   ────>  │   - Field types     │
│   (event, ctl) -> {  │          │   - Field offsets   │
│     event.thread()   │          │   - CP indices      │
│   }                  │          └──────────┬──────────┘
│ )                    │                     │
└──────────────────────┘                     │
                                             ▼
                          ┌─────────────────────────────────┐
                          │   ASM Bytecode Generation       │
                          │                                 │
                          │  Generate class:                │
                          │  JFRExecutionSample$Impl        │
                          │                                 │
                          │  public Thread thread() {       │
                          │    long cpIndex = readLong(24); │
                          │    return resolveThread(cpIndex)│
                          │  }                              │
                          └────────────┬────────────────────┘
                                       │
                                       ▼
                          ┌─────────────────────────────────┐
                          │  ClassLoader.defineClass()      │
                          │  ✓ Optimized field access       │
                          │  ✓ No reflection                │
                          │  ✓ JIT-friendly                 │
                          │  ✓ Zero overhead                │
                          └─────────────────────────────────┘

Result: event.thread() is as fast as a normal method call!
        No reflection, no maps, just plain Java bytecode.
```

**Key Points** (1 min):
- "First handler registration triggers bytecode generation"
- "Subsequent events use the optimized class"
- "JIT compiler optimizes it further - inlining, loop unrolling"
- "This is why the first chunk is slightly slower, then it screams"

---

#### Slide 17: "Lazy Evaluation & Constant Pool Tricks"

**Explain**: "We only deserialize what you actually access"

```
┌─────────────────────────────────────────────────────────────┐
│              LAZY DESERIALIZATION STRATEGY                  │
└─────────────────────────────────────────────────────────────┘

JFR File Structure:
┌────────────────────────────────────────────────────────────┐
│ Chunk 1: Metadata + Constant Pool + Events                │
│                                                            │
│ Constant Pool:                                             │
│  [0]: Thread{id=1, name="main"}                           │
│  [1]: Thread{id=2, name="GC Thread"}                      │
│  [2]: StackTrace{frames=[...100 frames...]}               │
│  [3]: Class{name="java.lang.String"}                      │
│  ... (1000s of entries)                                    │
│                                                            │
│ Event: ExecutionSample                                     │
│   - startTime: 12345678                                    │
│   - sampledThread: CP_REF[0]  ◄─── Just an index!         │
│   - stackTrace: CP_REF[2]     ◄─── Just an index!         │
└────────────────────────────────────────────────────────────┘

Traditional Parser (JMC):          JAFAR Approach:
┌──────────────────────┐          ┌─────────────────────┐
│ Read Event           │          │ Read Event          │
│   ↓                  │          │   ↓                 │
│ Resolve ALL CP refs  │          │ Store CP indices    │
│   ↓                  │          │   ↓                 │
│ Build full object    │          │ Return to handler   │
│   tree               │          └─────────┬───────────┘
│   ↓                  │                    │
│ Return to handler    │          Your handler accesses:
└──────────────────────┘          event.startTime()  ✓ Fast
                                  (No CP lookup needed)
Cost: O(all refs)
Even if you only                  Your handler skips:
use startTime!                    event.stackTrace()
                                  (CP never resolved!)

                                  Cost: O(accessed fields)
                                  Pay only for what you use!

┌─────────────────────────────────────────────────────────────┐
│ CONSTANT POOL CACHING                                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Same CP index used multiple times?                         │
│                                                             │
│  Event 1: stackTrace = CP[42] ───┐                         │
│  Event 2: stackTrace = CP[42] ───┼──> Cache hit! ✓         │
│  Event 3: stackTrace = CP[42] ───┘    No re-parsing        │
│                                                             │
│  LRU cache per chunk for hot entries                        │
│  Typical hit rate: 70-90% (stack traces reused heavily)    │
└─────────────────────────────────────────────────────────────┘
```

**Key Points** (1.5 min):
- "Traditional parsers eagerly deserialize everything - wasteful"
- "JAFAR stores CP indices, resolves on-demand"
- "Filtering by `startTime`? Never touches the constant pool"
- "Accessing nested fields? Only resolves that path"
- "Hot CP entries cached - same stack trace reused 1000s of times"

---

#### Slide 18: "Untyped API - Two Strategies for Different Access Patterns"

**Explain**: "Map-based API is tunable - we pick the right strategy for your pattern"

```
┌─────────────────────────────────────────────────────────────┐
│         UNTYPED API: STRATEGY OPTIMIZATION                  │
└─────────────────────────────────────────────────────────────┘

Strategy 1: SPARSE_ACCESS (Default)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Use case: Filtering, accessing 1-3 fields per event

Event Structure in Memory:
┌──────────────────────────────────────────┐
│ Map<String, Object>                      │
│                                          │
│  "startTime"    → [not yet read]        │  ◄── Lazy
│  "duration"     → [not yet read]        │  ◄── Lazy
│  "sampledThread"→ ComplexType(cpIdx=5)  │  ◄── Wrapper
│  "stackTrace"   → [not yet read]        │  ◄── Lazy
│  ...                                     │
└──────────────────────────────────────────┘

Your code:
  Long time = (Long) event.get("startTime");
  ↓
  Only now: read & box the value

Cost: O(1) per accessed field
Memory: Minimal - only accessed values materialized


Strategy 2: FULL_ITERATION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Use case: Exporting all fields, iterating entrySet()

Event Structure in Memory:
┌──────────────────────────────────────────┐
│ Map<String, Object>                      │
│                                          │
│  "startTime"    → 123456789L  ✓         │  ◄── Pre-read
│  "duration"     → 50000L      ✓         │  ◄── Pre-read
│  "sampledThread"→ Map{...}    ✓         │  ◄── Resolved
│  "stackTrace"   → Map{...}    ✓         │  ◄── Resolved
│  ...            → ...          ✓         │
└──────────────────────────────────────────┘

Your code:
  for (Map.Entry<String, Object> e : event.entrySet()) {
      export(e.getKey(), e.getValue());
  }
  ↓
  No parsing during iteration - all ready!

Cost: O(all fields) upfront, O(1) per access
Memory: Higher - all values materialized
Throughput: Much faster iteration


┌─────────────────────────────────────────────────────────────┐
│ PERFORMANCE COMPARISON                                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Access Pattern      │ SPARSE_ACCESS │ FULL_ITERATION      │
│ ────────────────────┼───────────────┼─────────────────    │
│ Get 2-3 fields      │   ⚡⚡⚡ Best  │   ⚡ Slower         │
│ Get 50% of fields   │   ⚡⚡ Good    │   ⚡⚡⚡ Best      │
│ Iterate all fields  │   ⚡ Slow      │   ⚡⚡⚡ Best      │
│ Filter & skip       │   ⚡⚡⚡ Best  │   ⚡ Wasteful       │
│ Export to JSON      │   ⚡ Slow      │   ⚡⚡⚡ Best      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Key Points** (1.5 min):
- "Not one-size-fits-all - you tell us your access pattern"
- "SPARSE: Perfect for filtering - skip fields you don't need"
- "FULL_ITERATION: Perfect for export - pay upfront, fast iteration"
- "Default is SPARSE - most use cases access few fields"
- "Switching strategies is one line of code"

---

#### Slide 19: "Streaming Architecture - Constant Memory"

**Explain**: "We process chunk-by-chunk, never loading the entire file"

```
┌─────────────────────────────────────────────────────────────┐
│           STREAMING CHUNK-BY-CHUNK PROCESSING               │
└─────────────────────────────────────────────────────────────┘

Traditional Approach (Many Parsers):
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  1. Load entire JFR into memory                  ⚠️ 10GB │
│  2. Parse all chunks                             ⚠️ Slow │
│  3. Build full event tree                        ⚠️ GC   │
│  4. Finally: call your handler                            │
│                                                            │
│  Memory usage: O(file size)                               │
│  Latency: High (wait for full parse)                      │
└────────────────────────────────────────────────────────────┘


JAFAR Streaming Approach:
┌────────────────────────────────────────────────────────────┐
│ 10GB JFR File on disk                                      │
└────────────────────────────────────────────────────────────┘
     │
     │ Memory-mapped I/O or streaming read
     ▼
┌─────────────────────┐
│ Chunk 1 (50MB)      │ ◄─── Only this chunk in memory
├─────────────────────┤
│ • Read metadata     │
│ • Parse CP          │      ┌──────────────────┐
│ • Stream events     │ ───> │ Your Handler     │
│   - Event 1    ──────────> │   process()      │
│   - Event 2    ──────────> │   if (filter)    │
│   - Event 3    ──────────> │     analyze()    │
└─────────────────────┘      └──────────────────┘
     │
     │ Chunk 1 done? Discard from memory ✓
     │
     ▼
┌─────────────────────┐
│ Chunk 2 (50MB)      │ ◄─── Next chunk loaded
├─────────────────────┤
│ • Read metadata     │
│ • Parse CP          │      ┌──────────────────┐
│ • Stream events     │ ───> │ Your Handler     │
│   - Event 1    ──────────> │   process()      │
│   - Event 2    ──────────> │                  │
└─────────────────────┘      └──────────────────┘
     │
     │ Continue...
     ▼

Memory usage: O(chunk size) ~50-200MB constant
Latency: Low (handler called immediately)
GC pressure: Minimal (no huge object graphs)

┌─────────────────────────────────────────────────────────────┐
│ EARLY TERMINATION                                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Looking for first 100 events matching criteria?           │
│                                                             │
│  parser.handle((event, ctl) -> {                           │
│      if (matches && ++count >= 100) {                       │
│          ctl.stop();  ◄─── Stop immediately!                │
│      }                                                       │
│  });                                                         │
│                                                             │
│  No need to parse the remaining 9.9GB!                      │
│  Traditional parsers: Parse everything anyway               │
│  JAFAR: Stop the moment you're done ✓                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Key Points** (1 min):
- "Process chunk-by-chunk, not file-at-once"
- "Memory usage doesn't grow with file size"
- "Can handle 100GB+ files with <1GB heap"
- "Early termination saves time - stop when you found what you need"
- "This is why JAFAR works on resource-constrained environments"

---

### Part 7: Getting Started (2 minutes)
**Slides 16-17**

**Slide**: "Start Using JAFAR Today"

**Installation** (30 sec)
```gradle
dependencies {
    implementation 'io.btrace:jafar-parser:0.3.0-SNAPSHOT'
}
```

**Resources** (1 min)
- **Slide**: "Complete Documentation"
  - GitHub: github.com/btrace/jafar
  - Typed API Tutorial: Step-by-step with Gradle plugin
  - Untyped API Tutorial: Performance optimization guide
  - JFR Shell Tutorial: Interactive analysis
  - 100+ code examples

**Quick Wins** (30 sec)
- Start with Untyped API for exploration
- Use JFR Shell for ad-hoc queries
- Move to Typed API for production
- Generate types with Gradle plugin

---

### DEMO 3: Audience Choice (3 minutes)
**Interactive**

**Options to Offer**:
1. "Export to JSON/Parquet for analysis in Python"
2. "Memory leak detection - find allocation hotspots"
3. "GC pause analysis and recommendations"
4. "File I/O bottleneck identification"
5. "Thread contention analysis"

**Vote**: "Which demo would you like to see?" [Quick poll]

**Execute**: Live code the winner in 2-3 minutes

---

### Closing & Q&A (4 minutes)
**Slides 18-19**

**Summary Slide** (1 min)
- "JAFAR: Fast, Minimal, Flexible"
- Two APIs: Type-safe or dynamic
- JFR Shell: Interactive exploration
- Production-ready: Fast, reliable
- Complete tutorials and examples

**Call to Action** (30 sec)
- Try it today: Single dependency
- Check out tutorials
- Join community / report issues
- "We want YOUR feedback"

**Open Q&A** (2.5 min)
- "What questions do you have?"
- Prepared answers for common questions (see below)

---

## Preparation Checklist

### Before Presentation

**Technical Setup**:
- [ ] Fresh JFR file ready (~100MB-2GB, production-like)
- [ ] IDE open with pre-created project
- [ ] JFR Shell tested and working
- [ ] All demos rehearsed and timed
- [ ] Backup JFR files in case of issues
- [ ] Screen recording as fallback for demos

**Materials**:
- [ ] Slides finalized
- [ ] Code snippets saved for quick paste
- [ ] Performance benchmark numbers ready
- [ ] Tutorial URLs bookmarked
- [ ] GitHub repo URL ready

**Contingency**:
- [ ] Screen recording of all demos as backup
- [ ] Offline copy of tutorials
- [ ] Pre-generated flamegraph as backup
- [ ] Backup laptop ready

---

## Anticipated Questions & Answers

### Technical Questions

**Q: "How does performance compare to JMC?"**
A: "3-5x faster in our benchmarks. No JMC dependencies means no overhead. We can show specific numbers from our test suite."

**Q: "What about memory usage with large files?"**
A: "Streaming architecture - constant memory regardless of file size. We've parsed 10GB+ files with <1GB heap."

**Q: "Can I use this in production?"**
A: "Yes, but it's marked experimental. API may evolve. We use it internally and have good test coverage. Would love your production feedback."

**Q: "Does it support compressed JFR files?"**
A: "Yes, transparently handles JFR compression."

**Q: "Can I filter events while parsing, not after?"**
A: "Yes, in your handler - check event type first, return early. Very efficient."

**Q: "What Java version is required?"**
A: "Java 8+ for the parser. Java 21+ to use the Gradle plugin with runtime metadata."

### Integration Questions

**Q: "Can I use this with [tool X]?"**
A: "Yes! Untyped API makes it easy to export to any format. We have examples for JSON, Parquet, CSV."

**Q: "Does it work with custom JFR events?"**
A: "Absolutely. Both APIs support custom events. Just use the same @JfrType annotation with your event name."

**Q: "Can I combine Typed and Untyped APIs?"**
A: "Yes! Use Typed for known events, Untyped for dynamic/exploratory analysis. Same recording, same parser context."

### Comparison Questions

**Q: "Why not just use JMC?"**
A: "JMC is great for UI. JAFAR is for programmatic access - CI/CD, automation, batch processing, custom analysis. Different use cases."

**Q: "What about JFR Event Streaming API?"**
A: "That's for live streaming. JAFAR is for file parsing. Complementary tools."

**Q: "How is this different from [other parser]?"**
A: "Focus on minimal ceremony and performance. Two APIs for different needs. Complete query language in JFR Shell. Active development."

### Project Questions

**Q: "Is this production-ready?"**
A: "Marked experimental, but we use it in production. API may evolve. Strong test coverage. Your feedback shapes the future."

**Q: "Who maintains this?"**
A: "Part of the BTrace project family. Open source, accepting contributions."

**Q: "Roadmap?"**
A: "Stabilizing APIs, performance improvements, more JFR Shell features, potentially JMC integration. We listen to community needs."

---

## Timing Breakdown

### Option A: With Architecture Deep Dive (50 minutes - Extended Version)

| Section | Duration | Type |
|---------|----------|------|
| Problem & Introduction | 5 min | Slides |
| Enter JAFAR | 5 min | Slides |
| **DEMO 1: Untyped API** | **7 min** | **Live Demo** |
| Typed API Intro | 6 min | Slides + Mini Demo |
| **DEMO 2: CPU Profiling** | **8 min** | **Live Demo** |
| **JFR Shell** | **7 min** | **Slides + Live Demo** |
| Performance & Production | 5 min | Slides |
| **Architecture Deep Dive** | **5 min** | **Slides (Technical)** |
| Advanced Features | 3 min | Slides |
| Getting Started | 2 min | Slides |
| **DEMO 3: Audience Choice** | **3 min** | **Interactive** |
| Closing & Q&A | 4 min | Slides + Interactive |
| **TOTAL** | **50 min** | |

**Demo Time**: 18 minutes (36%)
**Architecture**: 5 minutes (10%)
**Slides/Talk**: 25 minutes (50%)
**Q&A**: 4 minutes (8%) + ongoing

---

### Option B: Standard Version Without Deep Dive (45 minutes - Original)

| Section | Duration | Type |
|---------|----------|------|
| Problem & Introduction | 5 min | Slides |
| Enter JAFAR | 5 min | Slides |
| **DEMO 1: Untyped API** | **7 min** | **Live Demo** |
| Typed API Intro | 6 min | Slides + Mini Demo |
| **DEMO 2: CPU Profiling** | **8 min** | **Live Demo** |
| **JFR Shell** | **7 min** | **Slides + Live Demo** |
| Performance & Production | 5 min | Slides |
| Advanced Features | 3 min | Slides |
| Getting Started | 2 min | Slides |
| **DEMO 3: Audience Choice** | **3 min** | **Interactive** |
| Closing & Q&A | 4 min | Slides + Interactive |
| **TOTAL** | **45 min** | |

**Demo Time**: 18 minutes (40%)
**Slides/Talk**: 25 minutes (55%)
**Q&A**: 4 minutes (9%) + ongoing

---

### Recommendation

**For highly technical audience (performance engineers, library authors)**:
- Use **Option A** with Architecture Deep Dive
- Engineers love seeing "under the hood"
- Justifies the performance claims
- Shows clever engineering

**For general software engineers**:
- Use **Option B** without deep dive initially
- Have architecture slides ready as **backup**
- If Q&A time allows, show 1-2 architecture slides
- "Want to see how it works internally?" gauge interest

**Flexible approach**:
- Start with Option B
- After Demo 2, ask: "Want to see the magic under the hood?"
- If yes → show Slides 16-18 (5 min)
- If no/time short → skip to Advanced Features

---

---

## Using the Architecture Slides Effectively

### When to Show Architecture Deep Dive

**Strong signals to include it**:
- Audience asks "How is this so fast?" early on
- Lots of performance engineers or JVM experts in room
- Questions about implementation details during demos
- Tech lead says "I want to understand the internals first"

**Weak signals - skip or defer**:
- General application developers
- Focus is on "how do I use it?" not "how does it work?"
- Time pressure to finish on schedule
- Audience seems impatient during technical details

### How to Present the Architecture Slides

**Slide 16 - Bytecode Generation**:
- Start with: "Want to see the magic? Here's how typed API gets its speed"
- Emphasize: No reflection, pure Java bytecode
- Key takeaway: "It's as fast as if you wrote the code yourself"

**Slide 17 - Lazy Evaluation**:
- Start with: "Here's the trick that makes filtering so efficient"
- Compare Traditional vs JAFAR side-by-side
- Key takeaway: "Pay only for what you access"

**Slide 18 - Untyped Strategies**:
- Start with: "This is why we have two strategies"
- Show the decision matrix
- Key takeaway: "One line of code changes the optimization"

**Slide 19 - Streaming**:
- Start with: "This is how we handle 100GB files"
- Show memory staying constant
- Key takeaway: "Streaming means no memory limit"

### Architecture Q&A Additions

Add these to your Q&A prep:

**Q: "How much overhead does bytecode generation add?"**
A: "~10-50ms one-time cost per event type on first handler registration. Amortized over millions of events, it's negligible. That's why first chunk is slightly slower."

**Q: "What about reflection at all?"**
A: "Zero reflection after handler generation. We use ASM to generate bytecode that directly reads from buffers. Pure method calls."

**Q: "Does lazy evaluation work with the Typed API too?"**
A: "Yes! If you don't call `event.stackTrace()`, we never resolve that constant pool entry. Works for both APIs."

**Q: "Can I see the generated bytecode?"**
A: "Yes, enable debug logging. We can add a flag to dump generated classes. File an issue if you want this feature."

**Q: "What about GC pressure from all the lazy objects?"**
A: "Actually less GC pressure. We only allocate what you access. Traditional parsers allocate entire object trees upfront."

---

## Presentation Tips

### Engagement Strategies

1. **Start with hands**: Get audience participation early ("Who has used JFR?")
2. **Live demos, not recordings**: Shows confidence, allows improvisation
3. **Take suggestions**: "What would you analyze?" makes it interactive
4. **Real data**: Use actual production-like JFR files, not toy examples
5. **Show failures**: If something breaks, explain why - more authentic

### Demo Tips

1. **Zoom your terminal**: Make sure everyone can read
2. **Use IDE with large font**: 16-18pt minimum
3. **Comment as you type**: Explain what you're doing
4. **Have fallbacks**: Pre-written code to paste if needed
5. **Test wifi**: Download dependencies beforehand

### Pacing

- **Slides**: Move quickly, demos are the star
- **Demos**: Go slower, let them see you type
- **Questions**: Encourage throughout, not just at end
- **Energy**: High energy for demos, calmer for technical depth

### Red Flags to Avoid

- Don't apologize for experimental status - emphasize maturity
- Don't bash other tools - position as complementary
- Don't skip demos due to time - cut slides instead
- Don't rush the "why" - engineers need the problem context

---

## Backup Slides (Optional)

Have these ready if time allows or questions arise:

1. **Architecture Deep Dive**: Bytecode generation, constant pools
2. **Benchmark Details**: Methodology, test cases, numbers
3. **Type Generation Process**: How the plugin works internally
4. **Untyped Strategies**: Deep dive on SPARSE vs FULL_ITERATION
5. **Production Case Studies**: Real deployments and learnings

---

## Post-Presentation Actions

1. **Share slides** and demo code on GitHub
2. **Record screencast** of demos for those who missed it
3. **Collect feedback**: Google Form or GitHub Discussions
4. **Follow up** on specific questions via email/Slack
5. **Blog post**: "JAFAR Introduction - Key Takeaways"

---

## Success Metrics

**Good outcome**:
- 5+ engineers try JAFAR within a week
- 2-3 follow-up questions via email/Slack
- Positive feedback on demos

**Great outcome**:
- Integration into CI/CD pipeline discussion
- Production use case proposals
- Contribution interest
- Feature requests

---

**Remember**: Engineers love code, not slides. Make the demos the hero of the presentation!
