package sweetie.evaware.luma.wrapper.uniform

sealed class UniformHandle {
    internal var location = -1

    internal fun resolve(location: Int) {
        this.location = location
        invalidateCache()
    }

    protected open fun invalidateCache() = Unit
}

class Float1Uniform internal constructor() : UniformHandle() {
    private var initialized = false
    private var valueBits = 0

    internal fun shouldUpload(value: Float): Boolean {
        val nextBits = value.toRawBits()
        if (initialized && valueBits == nextBits) return false
        initialized = true
        valueBits = nextBits
        return true
    }

    override fun invalidateCache() {
        initialized = false
    }
}

class Float2Uniform internal constructor() : UniformHandle() {
    private var initialized = false
    private var firstBits = 0
    private var secondBits = 0

    internal fun shouldUpload(first: Float, second: Float): Boolean {
        val nextFirstBits = first.toRawBits()
        val nextSecondBits = second.toRawBits()
        if (initialized && firstBits == nextFirstBits && secondBits == nextSecondBits) return false
        initialized = true
        firstBits = nextFirstBits
        secondBits = nextSecondBits
        return true
    }

    override fun invalidateCache() {
        initialized = false
    }
}

class Float3Uniform internal constructor() : UniformHandle() {
    private var initialized = false
    private var firstBits = 0
    private var secondBits = 0
    private var thirdBits = 0

    internal fun shouldUpload(first: Float, second: Float, third: Float): Boolean {
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

    override fun invalidateCache() {
        initialized = false
    }
}

class Float4Uniform internal constructor() : UniformHandle() {
    private var initialized = false
    private var firstBits = 0
    private var secondBits = 0
    private var thirdBits = 0
    private var fourthBits = 0

    internal fun shouldUpload(first: Float, second: Float, third: Float, fourth: Float): Boolean {
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

    override fun invalidateCache() {
        initialized = false
    }
}

class Int1Uniform internal constructor() : UniformHandle() {
    private var initialized = false
    private var lastValue = 0

    internal fun shouldUpload(value: Int): Boolean {
        if (initialized && lastValue == value) return false
        initialized = true
        lastValue = value
        return true
    }

    override fun invalidateCache() {
        initialized = false
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
