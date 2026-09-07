# Backend benchmarks

The `benchmarks` module contains comparable OpenGL and Blaze3D Vulkan benchmarks. It reuses the backend window lifecycle from `standalone-app`, while all workloads, launchers, and benchmark shaders remain isolated in the benchmark module. Both backends run the same workloads and load the same shader source files through `shader-translator`; only the translation target differs.

Run both backends in isolated JVM processes:

```bash
./gradlew :benchmarks:runBenchmarks
```

Run one backend:

```bash
./gradlew :benchmarks:runGlBenchmark
./gradlew :benchmarks:runGlPreserveBenchmark
./gradlew :benchmarks:runVulkanBenchmark
```

Run the interactive standalone application by selecting a backend:

```bash
./gradlew :standalone-app:runStandaloneApp --args='--backend=gl'
./gradlew :standalone-app:runStandaloneApp --args='--backend=vulkan'
```

Each benchmark performs warm-up samples before recording measurements. Results are sorted and reported as average, median, minimum, maximum, p05, p95, and p05–p95 spread. Every workload reports three metrics:

- `submit` measures CPU-side command construction and submission.
- `complete` additionally waits for the GPU to finish the submitted work.
- `allocation` reports JVM bytes allocated on the submitting thread per operation.

The workloads are grouped by `lifecycle`, `state`, `binding`, `draw`, `pass`, `upload`, and `resource`. They cover empty frames before and after peak load, flat and nested render-target stacks, redundant and alternating state commands, texture binding, clean and dirty uniforms, programs, textures, triangles, lines, quads, batching, instancing, render-pass switches, texture uploads, render-target creation, and shader creation.

For allocation type stacks and allocations performed by other JVM threads, record a Java Flight Recorder profile:

```bash
./gradlew :benchmarks:profileGlAllocations
./gradlew :benchmarks:profileGlPreserveAllocations
./gradlew :benchmarks:profileVulkanAllocations
```

Recordings are written to `benchmarks/build/jfr`. The normal benchmark has no profiler dependency or agent overhead; allocation byte counts use the JDK management API.

The OpenGL benchmark runs both `GlStatePolicy.OWNED` and `GlStatePolicy.PRESERVE`. The owned profile measures Luma with exclusive context control. The preserve profile measures the state capture and restoration required when Luma shares a context with another renderer, as in Minecraft. Vulkan `complete` measurements attach a fence to the workload's real command submission rather than submitting a separate empty command buffer.

Texture upload submission differs by backend: OpenGL performs the upload call immediately, while Vulkan records the upload for the next frame submission. Use `complete` when comparing end-to-end upload cost and `submit` when investigating CPU-side API overhead.

The suite covers individual draw submissions, batched triangles, instanced triangles, texture creation/upload, and subimage upload. Geometry is rendered outside the clip region so the measurements focus on Luma and backend command paths rather than fragment fill rate.

These are local microbenchmarks, not FPS measurements. Compare runs on the same machine, driver, JVM, and power profile; use a profiler and a representative application scene before drawing architectural conclusions.
