package sweetie.evaware.benchmark.core

internal class BenchmarkStatistics private constructor(
    private val average: Double,
    private val median: Long,
    private val minimum: Long,
    private val maximum: Long,
    private val p05: Long,
    private val p95: Long
) {
    fun format(unit: String): String {
        return "avg=${"%.2f".format(average)} $unit median=$median $unit min=$minimum $unit " +
            "max=$maximum $unit p05=$p05 $unit p95=$p95 $unit spread=${p95 - p05} $unit"
    }

    companion object {
        fun from(values: LongArray): BenchmarkStatistics {
            values.sort()
            return BenchmarkStatistics(
                average = values.average(),
                median = percentile(values, 0.50),
                minimum = values.first(),
                maximum = values.last(),
                p05 = percentile(values, 0.05),
                p95 = percentile(values, 0.95)
            )
        }

        private fun percentile(values: LongArray, percentile: Double): Long {
            val index = Math.round((values.size - 1) * percentile).toInt().coerceIn(values.indices)
            return values[index]
        }
    }
}
