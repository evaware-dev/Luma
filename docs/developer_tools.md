# Developer tools

The optional `developer-tools` artifact exposes backend decorators without adding diagnostics to the core runtime.

```kotlin
val glBackend = Backend()
val stats = StatsBackend(glBackend)
Luma.backend = stats

Luma.render {
    scene.render()
}

println(stats.lastFrame.drawCalls)
println(stats.lastFrame.instances)
println(stats.lastFrame.cpuNanos)
```

`currentFrame`, `lastFrame`, and `lifetime` are reused mutable snapshots. Reading them does not allocate per frame; retaining `lastFrame` retains the live snapshot rather than an immutable historical copy.

Keep the concrete backend when using backend-specific tools. For example, raw OpenGL can be isolated with `glBackend.externalGl { ... }` while statistics continue through the decorator.
