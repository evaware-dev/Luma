package sweetie.evaware.luma.uniform

sealed class UniformHandle {
    internal var location = -1

    internal fun resolve(location: Int) {
        this.location = location
        invalidateCache()
    }

    protected open fun invalidateCache() = Unit
}

sealed class CachedUniformHandle : UniformHandle() {
    protected var initialized = false

    final override fun invalidateCache() {
        initialized = false
    }
}

sealed class FloatBitsUniform : CachedUniformHandle() {
    protected var firstBits = 0
    protected var secondBits = 0
    protected var thirdBits = 0
    protected var fourthBits = 0

    protected fun shouldUpload1(value: Float): Boolean {
        val nextBits = value.toRawBits()
        if (initialized && firstBits == nextBits) return false
        initialized = true
        firstBits = nextBits
        return true
    }

    protected fun shouldUpload2(first: Float, second: Float): Boolean {
        val nextFirstBits = first.toRawBits()
        val nextSecondBits = second.toRawBits()
        if (initialized && firstBits == nextFirstBits && secondBits == nextSecondBits) return false
        initialized = true
        firstBits = nextFirstBits
        secondBits = nextSecondBits
        return true
    }

    protected fun shouldUpload3(first: Float, second: Float, third: Float): Boolean {
        val nextFirstBits = first.toRawBits()
        val nextSecondBits = second.toRawBits()
        val nextThirdBits = third.toRawBits()
        if (initialized && firstBits == nextFirstBits && secondBits == nextSecondBits && thirdBits == nextThirdBits) return false
        initialized = true
        firstBits = nextFirstBits
        secondBits = nextSecondBits
        thirdBits = nextThirdBits
        return true
    }

    protected fun shouldUpload4(first: Float, second: Float, third: Float, fourth: Float): Boolean {
        val nextFirstBits = first.toRawBits()
        val nextSecondBits = second.toRawBits()
        val nextThirdBits = third.toRawBits()
        val nextFourthBits = fourth.toRawBits()
        if (
            initialized &&
            firstBits == nextFirstBits &&
            secondBits == nextSecondBits &&
            thirdBits == nextThirdBits &&
            fourthBits == nextFourthBits
        ) {
            return false
        }
        initialized = true
        firstBits = nextFirstBits
        secondBits = nextSecondBits
        thirdBits = nextThirdBits
        fourthBits = nextFourthBits
        return true
    }
}

class Float1Uniform internal constructor() : FloatBitsUniform() {
    internal fun shouldUpload(value: Float) = shouldUpload1(value)
}

class Float2Uniform internal constructor() : FloatBitsUniform() {
    internal fun shouldUpload(first: Float, second: Float) = shouldUpload2(first, second)
}

class Float3Uniform internal constructor() : FloatBitsUniform() {
    internal fun shouldUpload(first: Float, second: Float, third: Float) = shouldUpload3(first, second, third)
}

class Float4Uniform internal constructor() : FloatBitsUniform() {
    internal fun shouldUpload(first: Float, second: Float, third: Float, fourth: Float) =
        shouldUpload4(first, second, third, fourth)
}

class Int1Uniform internal constructor() : CachedUniformHandle() {
    private var lastValue = 0

    internal fun shouldUpload(value: Int): Boolean {
        if (initialized && lastValue == value) return false
        initialized = true
        lastValue = value
        return true
    }
}

class Mat4Uniform internal constructor() : UniformHandle() {
    private var lastProjectionVersion = Int.MIN_VALUE

    internal fun shouldUploadProjectionVersion(version: Int): Boolean {
        if (lastProjectionVersion == version) return false
        lastProjectionVersion = version
        return true
    }

    internal fun invalidateProjectionVersion() {
        lastProjectionVersion = Int.MIN_VALUE
    }

    override fun invalidateCache() {
        lastProjectionVersion = Int.MIN_VALUE
    }
}
