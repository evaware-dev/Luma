package sweetie.evaware.benchmark.core

internal class BenchmarkRunner(
    private val backendName: String,
    private val beforeSubmit: () -> Unit,
    private val awaitGpu: () -> Unit,
    private val allocationMeter: AllocationMeter = AllocationMeter.create()
) {
    fun measure(
        group: String,
        name: String,
        unitsPerSample: Int,
        warmupSamples: Int = DEFAULT_WARMUP_SAMPLES,
        measurementSamples: Int = DEFAULT_MEASUREMENT_SAMPLES,
        submit: () -> Unit
    ) {
        require(unitsPerSample > 0) { "Units per sample must be positive" }
        require(warmupSamples >= 0) { "Warmup sample count must not be negative" }
        require(measurementSamples > 0) { "Measurement sample count must be positive" }

        repeat(warmupSamples) {
            beforeSubmit()
            submit()
            awaitGpu()
        }

        val submitTimings = LongArray(measurementSamples)
        val completeTimings = LongArray(measurementSamples)
        val allocations = if (allocationMeter.available) LongArray(measurementSamples) else null

        repeat(measurementSamples) { sample ->
            beforeSubmit()
            val allocatedBefore = allocationMeter.currentThreadBytes()
            val started = System.nanoTime()
            submit()
            val submitted = System.nanoTime()
            val allocatedAfter = allocationMeter.currentThreadBytes()
            awaitGpu()
            val completed = System.nanoTime()

            submitTimings[sample] = perUnit(submitted - started, unitsPerSample)
            completeTimings[sample] = perUnit(completed - started, unitsPerSample)
            if (allocations != null) {
                allocations[sample] = perUnit((allocatedAfter - allocatedBefore).coerceAtLeast(0L), unitsPerSample)
            }
        }

        val prefix = "[Benchmark][$backendName][$group] $name"
        println("$prefix submit ${BenchmarkStatistics.from(submitTimings).format("ns/op")}")
        println("$prefix complete ${BenchmarkStatistics.from(completeTimings).format("ns/op")}")
        if (allocations != null) {
            println("$prefix allocation ${BenchmarkStatistics.from(allocations).format("B/op")}")
        } else {
            println("$prefix allocation unavailable")
        }
    }

    private fun perUnit(value: Long, units: Int): Long = Math.round(value.toDouble() / units)

    private companion object {
        const val DEFAULT_WARMUP_SAMPLES = 12
        const val DEFAULT_MEASUREMENT_SAMPLES = 40
    }
}
