package sweetie.evaware.luma.wrapper.shader

object LumaGlslLibrary {
    private val importPattern = Regex("""^\s*#(?:import|include)\s*(?:<([a-zA-Z0-9_./-]+)>|"([a-zA-Z0-9_./-]+)")\s*$""")
    private val paths = linkedMapOf<String, String>()
    private val sources = mutableMapOf<String, String>()
    private val resolvedSources = mutableMapOf<String, String>()

    fun register(name: String, path: String) = apply {
        paths[name] = path
        resolvedSources.clear()
    }

    fun attach() = apply {
        sources.clear()
        resolvedSources.clear()
        for ((name, path) in paths) {
            sources[name] = load(path)
        }
    }

    fun resolve(source: String): String = resolvedSources.getOrPut(source) {
        resolveImports(source, mutableSetOf())
    }

    fun resolveResource(path: String): String = resolve(load(path))

    private fun resolveImports(source: String, stack: MutableSet<String>): String {
        val builder = StringBuilder(source.length + 64)

        for (line in source.lineSequence()) {
            val match = importPattern.matchEntire(line)
            if (match == null) {
                builder.append(line).append('\n')
                continue
            }

            val name = match.groupValues[1].ifBlank { match.groupValues[2] }
            check(stack.add(name)) { "Recursive GLSL import: $name" }
            builder.append(resolveImports(sourceOf(name), stack))
            stack.remove(name)
        }

        return builder.toString()
    }

    private fun sourceOf(name: String): String {
        val cached = sources[name]
        if (cached != null) return cached

        val path = paths[name] ?: error("Missing GLSL library: $name")
        val source = load(path)
        sources[name] = source
        return source
    }

    private fun load(path: String) = javaClass.classLoader.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Missing GLSL library resource: $path")
}
