---
marp: true
theme: default
paginate: true
backgroundColor: #fff
---

<!-- _paginate: false -->
<!-- _backgroundColor: #1e3a8a -->
<!-- _color: #fff -->

# JAFAR

## Fast, Minimal JFR Parser

**45 (or so) Minute Technical Deep Dive**

---

# Who's Used JFR for Profiling?

**Show of hands?** (just joking)

---

# And how are you analyzing the collected JFR files?

**The Challenge:**
- Hundreds of event types
- Millions of events
- Complex nested structures
- Binary format complexity

---

# What Existing Solutions Do

**JMC API**
- Extremely versatile, powers JMC (JDK Mission Control)
- Rather heavy, a lot of processing afront
- Limited access to JFR internal info
- Steep learning curve

**JDK JFR Parser**
- Also a lot of processing afront
- Inherently single-threaded
- Very much 'map-based'

**Custom parsing**
- No official format spec, need to reverse engineer

---

# When You Need Light Programmatic JFR Access

- **CI/CD**: Performance regression detection
- **Production**: Automated profiling analysis
- **Monitoring**: Custom dashboards
- **Batch Processing**: Analyze thousands of recordings
- **Data Export**: Parquet, DuckDB, ClickHouse

---

# Enter JAFAR

**Fast, Minimal JFR Parser**

- Streaming architecture
- Heavily parallelizable
- Zero-allocation parsing paths
- Pick-your-poison set of APIs

---

# Design Principles

- **Minimal ceremony**: Get started quickly
- **Pay for what you use**: Lazy evaluation
- **Fast by default**: Optimized for throughput
- **Type safety when you want it**: Typed API
- **Flexibility when you need it**: Untyped API

---

# Architecture at a Glance

```
┌─────────────────────┐      ┌─────────────────────┐
│   Typed API         │      │   Untyped API       │
│   @JfrType("...")   │      │   Map<String, Obj>  │
│                     │      │                     │
│ Compile-time safety │      │ Map-based, flexible │
│ IDE auto-complete   │      │ Performance tuning  │
└──────────┬──────────┘      └──────────┬──────────┘
           │                            │
           └──────────┬─────────────────┘
                      │
           ┌──────────▼──────────┐
           │   Core Parser       │
           │   Chunk-by-chunk    │
           │                     │
           │  Streaming, fast    │
           │  Memory efficient   │
           | Access to metadata, |
           | const.pools, etc.   |
           └─────────────────────┘
```

---

<!-- _backgroundColor: #059669 -->
<!-- _color: #fff -->

# DEMO 1

## Quick Win - Untyped API

### CountEventsUntyped
### CountEventsStreaming

_!!! Usable JFR inspections with a few lines of code !!!_

---

# Untyped API Drawbacks

- Runtime casting
- No IDE help
- Typos in field names
- Map based values -> allocation pressure, boxed primitives
- Refactoring is hard

**Solution?** Type Safety!

---

# Typed API: Interfaces + Annotations

```java
@JfrType("jdk.ExecutionSample")
interface ExecutionSample {
    long startTime();
    Thread sampledThread();
    StackTrace stackTrace();
}
```

- ✅ Compile-time safety
- ✅ IDE auto-completion
- ✅ Refactoring-friendly

---

# Typed API: Advanced features

```java
@JfrType("jdk.ExecutionSample")
interface ExecutionSample {
    @JfrField("startTime")
    long startTimeX();
    
    @JfrField(value = "sampledThread", raw = true)
    long sampledThread();
    
    StackTrace stackTrace();
    
    @JfrIgnore
    default long computeConvolutedValue() {
        // do very complex computation based on the event attributes
        return 42;
    }
}
```
---

# Auto-Generate Types

**Gradle Plugin Magic:**

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

---

<!-- _backgroundColor: #059669 -->
<!-- _color: #fff -->

# DEMO 2

## Real Analysis - CPU Profiling

### HottestEnpdoints
### HottestMethods

---

# Ad-Hoc Analysis Pain

**The Problem:**
- Need to write code for every question
- Compile, run, repeat
- Slow iteration cycle
- Hard to explore unknown data

**Solution?** Interactive Shell!

---

# JFR Shell: Interactive JFR Exploration

- Path-oriented query language (JfrPath)
- Tab completion
- Multiple sessions
- Export capabilities

---

<!-- _backgroundColor: #059669 -->
<!-- _color: #fff -->

# DEMO 3

## JFR Shell Live

---

# JFR Shell: Basic Commands

```bash
jfr> open recording.jfr
jfr> show events/jdk.ExecutionSample | count()
jfr> show events/jdk.ExecutionSample | groupBy(thread/name) | top(5)
```

**Power Query:**
```bash
jfr> show events/jdk.FileRead[bytesRead>=1048576] |
     groupBy(path) | sum(bytesRead) | top(10)
```

**Metadata Exploration:**
```bash
jfr> metadata class jdk.ExecutionSample --tree
jfr> help show
```

---

# Comparison vs JMC and JDK JFR Parser

**Performance:**
- 3-5x faster throughput
- Lower memory usage
- Faster startup time

**Why?**
- Streaming architecture
- Zero-allocation paths
- Lazy constant pool resolution
- Optimized bytecode generation

**Not as complete**
- Simplified annotation handling
- No units, etc.

---

# Optimize Multi-File Processing

**ParsingContext for Performance:**

```java
ParsingContext ctx = ParsingContext.create();
for (Path file : recordings) {
    try (TypedJafarParser parser = TypedJafarParser.open(file, ctx)) {
        // Reuses generated bytecode, metadata
    }
}
```

- Caches generated handlers
- Caches compatible metadata
- Shares expensive resources
- **Significantly faster for batch processing or repeated processing of similar JFRs**

---

# Beyond Basics

**1. Chunk-Level Control**
```java
parser.withParserListener(new ChunkParserListener() {
    @Override
    public void onChunkStart(ParserContext ctx, ChunkInfo chunk) {
        System.out.println("Processing: " + chunk.startTimestamp());
    }
});
```

**2. Untyped Performance Tuning**
```java
// Sparse access for filtering
parser = ctx.newUntypedParser(file, UntypedStrategy.SPARSE_ACCESS);

// Full iteration for export
parser = ctx.newUntypedParser(file, UntypedStrategy.FULL_ITERATION);
```

---

# Beyond Basics (continued)

**3. Values Utility**
```java
// Navigate nested structures easily
Object methodName = Values.get(event, "stackTrace",
                               "frames", 0, "method", "name");
```

**4. Control Flow**
```java
parser.handle((event, ctl) -> {
    if (found >= 100) {
        ctl.stop();  // Early termination
    }
});
```

---

<!-- _backgroundColor: #dc2626 -->
<!-- _color: #fff -->

# ARCHITECTURE DEEP DIVE

## How JAFAR Gets Its Speed

---

# The Secret Sauce: Dynamic Bytecode Generation

**Step 1: Your Code**
```java
parser.handle(
  JFRExecutionSample,
  (event, ctl) -> {
    event.thread()
  }
)
```

**Step 2: First Parse**
- JFR Metadata → Field types, offsets, CP indices

**Step 3: ASM Bytecode Generation**
```java
public Thread thread() {
  long cpIndex = readLong(24);
  return resolveThread(cpIndex);
}
```

---

# Bytecode Generation Magic

**Result:**
- `event.thread()` is as fast as a normal method call
- No reflection
- No maps
- Just plain Java bytecode
- JIT compiler optimizes further
- Non-exposed attributes are skipped - no deserialization cost!

**Why first chunk is slower:**
- One-time bytecode generation
- Then it screams!
- Generated bytecode can be reused via parsing context!

---

# Lazy Evaluation & Constant Pool Tricks

**Traditional Parser (JMC):**
1. Read Event
2. Resolve ALL CP refs
3. Build full object tree
4. Return to handler

**Cost:** O(all refs) - even if you only use `startTime`!

**JAFAR Approach:**
1. Read Event
2. Store CP indices
3. Return to handler
4. Resolve only what you access

**Cost:** O(accessed fields) - pay only for what you use!

---

# Constant Pool Caching

```
Event 1: stackTrace = CP[42] ───┐
Event 2: stackTrace = CP[42] ───┼──> Cache hit! ✓
Event 3: stackTrace = CP[42] ───┘    No re-parsing
```

But this is what all parser do, so no biggie

**LRU cache per chunk for hot entries**
- Typical hit rate: 70-90%
- Stack traces reused heavily
- This gets more interesting - reducing the required memory

---

# Untyped API: Two Strategies

**SPARSE_ACCESS (Default)**
- Use case: Filtering, accessing 1-3 fields
- Cost: O(1) per accessed field
- Memory: Minimal - lazy evaluation

**FULL_ITERATION**
- Use case: Exporting all fields, iterating entrySet()
- Cost: O(all fields) upfront, O(1) per access
- Memory: Higher - all values materialized
- Throughput: Much faster iteration

---

# Strategy Performance Comparison

| Access Pattern      | SPARSE_ACCESS | FULL_ITERATION |
|---------------------|---------------|----------------|
| Get 2-3 fields      | ⚡⚡⚡ Best    | ⚡ Slower      |
| Get 50% of fields   | ⚡⚡ Good      | ⚡⚡⚡ Best    |
| Iterate all fields  | ⚡ Slow       | ⚡⚡⚡ Best    |
| Filter & skip       | ⚡⚡⚡ Best    | ⚡ Wasteful    |
| Export to JSON      | ⚡ Slow       | ⚡⚡⚡ Best    |

**Switching strategies:** One line of code!

---

# Streaming Architecture: Constant Memory

**Traditional Approach:**
1. Load entire JFR into memory (⚠️ 10GB)
2. Parse all chunks (⚠️ Slow)
3. Build full event tree (⚠️ GC)
4. Finally: call your handler

**Memory usage:** O(file size)

---

# JAFAR Streaming Approach

```
10GB JFR File on disk
    ↓ Memory-mapped I/O
┌────────────────────────────────────────────────────────┐
│ Chunk 1 (50MB)                                         │ ◄───  Memory mapped file!
│ • Read metadata                                        │
│ • Parse CP, store offsets, do not deserialize          │ ───> Your Handler
│ • Stream events                                        │        process()
└────────────────────────────────────────────────────────┘
    ↓ Discard ✓ (cached entries survive)
┌─────────────────────┐
│ Chunk 2 (50MB)      │ ◄─── Next chunk
└─────────────────────┘
```

**Memory usage:** Independent of the recording size (chunks processed as memory mapped file)
**Can handle 100GB+ files with <1GB heap**

---

# Early Termination

```java
parser.handle((event, ctl) -> {
    if (matches && ++count >= 100) {
        ctl.stop();  // ◄─── Stop immediately!
    }
});
```

**Looking for first 100 events?**
- No need to parse remaining 9.9GB!
- Traditional parsers: Parse everything anyway
- JAFAR: Stop the moment you're done ✓

---

# Start Using JAFAR Today

**Installation:**
```gradle
dependencies {
    implementation 'io.btrace:jafar-parser:0.3.0-SNAPSHOT'
}
```

**Resources:**
- [Jafar GitHub](https://github.com/btraceio/jafar)
- Typed API Tutorial
- Untyped API Tutorial
- JFR Shell Tutorial
- 100+ code examples

---

# Quick Wins

1. Start with **Untyped API** for exploration
2. Use **JFR Shell** for ad-hoc queries
3. Move to **Typed API** for production
4. Generate types with **Gradle plugin**

---

<!-- _backgroundColor: #059669 -->
<!-- _color: #fff -->

# DEMO 4

## Audience Choice

What would you like to see?

1. Export to JSON/Parquet for Python analysis
2. Memory leak detection - allocation hotspots
3. GC pause analysis and recommendations
4. File I/O bottleneck identification
5. Thread contention analysis

---

# Summary

**JAFAR: Fast, Minimal, Flexible**

✅ Two APIs: Type-safe or dynamic
✅ JFR Shell: Interactive exploration
✅ Production-ready: Fast, reliable
✅ Complete tutorials and examples

---

# Call to Action

- **Try it today**: Single dependency
- **Check out tutorials**
- **Join community** / report issues
- **We want YOUR feedback**

---

<!-- _paginate: false -->
<!-- _backgroundColor: #1e3a8a -->
<!-- _color: #fff -->

# Questions?

**Thank you!**

GitHub: github.com/btrace/jafar
