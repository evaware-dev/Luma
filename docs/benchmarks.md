# Backend benchmarks

The `benchmarks` module contains comparable OpenGL and Blaze3D Vulkan benchmarks. It reuses the backend window lifecycle from `standalone-app`, while all workloads, launchers, and benchmark shaders remain isolated in the benchmark module. Both backends run the same workloads and load the same shader source files through `shader-translator`; only the translation target differs.

Run both backends in isolated JVM processes:

```bash
./gradlew :benchmarks:runBenchmarks
```

Run one backend:

```bash
./gradlew :benchmarks:runGlBenchmark
./gradlew :benchmarks:runVulkanBenchmark
```

Run the interactive standalone application by selecting a backend:

```bash
./gradlew :standalone-app:runStandaloneApp --args='--backend=gl'
./gradlew :standalone-app:runStandaloneApp --args='--backend=vulkan'
```

Each benchmark performs warm-up samples before recording measurements. Results are sorted and reported as average, median, minimum, maximum, p05, p95, and p05–p95 spread. Every workload reports two timings:

- `submit` measures CPU-side command construction and submission.
- `complete` additionally waits for the GPU to finish the submitted work.

The OpenGL benchmark owns its standalone context and therefore uses `GlStatePolicy.OWNED`, avoiding state queries that are only needed when Luma shares a context with another renderer. `GlStatePolicy.PRESERVE` remains the default for integrations such as Minecraft. Vulkan `complete` measurements attach a fence to the workload's real command submission rather than submitting a separate empty command buffer.

Texture upload submission differs by backend: OpenGL performs the upload call immediately, while Vulkan records the upload for the next frame submission. Use `complete` when comparing end-to-end upload cost and `submit` when investigating CPU-side API overhead.

The suite covers individual draw submissions, batched triangles, instanced triangles, texture creation/upload, and subimage upload. Geometry is rendered outside the clip region so the measurements focus on Luma and backend command paths rather than fragment fill rate.

These are local microbenchmarks, not FPS measurements. Compare runs on the same machine, driver, JVM, and power profile; use a profiler and a representative application scene before drawing architectural conclusions.
