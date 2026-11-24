# Backend Benchmarks

The `benchmarks` module runs comparable OpenGL (`OWNED` and `PRESERVE`) and Blaze3D Vulkan microbenchmarks in isolated JVM processes. All profiles execute identical workloads and translate the same shader sources via `shader-translator`.

## Running

```bash
./gradlew :benchmarks:runBenchmarks
```

Or run an individual backend:

```bash
./gradlew :benchmarks:runGlBenchmark
./gradlew :benchmarks:runGlPreserveBenchmark
./gradlew :benchmarks:runVulkanBenchmark
```

Standalone verification app:

```bash
./gradlew :standalone-app:runStandaloneApp --args='--backend=gl --verify-only'
./gradlew :standalone-app:runStandaloneApp --args='--backend=vulkan --verify-only'
```

JFR allocation profiles (output written to `benchmarks/build/jfr`):

```bash
./gradlew :benchmarks:profileGlAllocations
./gradlew :benchmarks:profileGlPreserveAllocations
./gradlew :benchmarks:profileVulkanAllocations
```

## Methodology

Each workload runs warm-up samples before recording sorted measurements (`avg`, `median`, `min`, `max`, `p05`, `p95`, `spread`) across three metrics:

- **`submit`**: CPU-side command recording and submission time (`ns/op`).
- **`complete`**: End-to-end time including GPU completion synchronization (`ns/op`). On Vulkan, the completion fence is attached to the workload's real command buffer submission.
- **`allocation`**: JVM bytes allocated on the submitting thread (`B/op`, measured via `ThreadMXBean`, zero agent overhead).

Workloads are grouped by `lifecycle`, `state`, `binding`, `draw` (including `DrawList` replay), `pass`, `upload`, and `resource`. Geometry is placed outside the clip volume so measurements focus on Luma and backend submission paths rather than fragment fill rate.

## Notes

- OpenGL runs both `GlStatePolicy.OWNED` (exclusive context control) and `GlStatePolicy.PRESERVE` (lazy state capture/restoration when sharing a context, as in Minecraft).
- Texture uploads differ by backend: OpenGL uploads synchronously on the call, whereas Vulkan stages the upload for the next frame submission. Compare `complete` for end-to-end cost and `submit` for CPU-side overhead.
- These are local microbenchmarks, not FPS measurements. Compare runs only on the same machine, driver, JVM, and power profile.
