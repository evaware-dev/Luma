package sweetie.evaware.luma.uniform

import org.joml.Matrix4f

sealed class UniformHandle {
    @JvmField
    internal var owner: UniformRegistry? = null

    var isDirty = true
        set(value) {
            field = value
            if (value) owner?.bumpRevision()
        }
}

class Float1Uniform internal constructor() : UniformHandle() {
    var value = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }

    fun set(v: Float) {
        if (value != v) {
            value = v
        }
    }
}

class Float2Uniform internal constructor() : UniformHandle() {
    private var _first = 0f
    private var _second = 0f

    var first: Float
        get() = _first
        set(v) {
            if (_first != v) {
                _first = v
                isDirty = true
            }
        }
    var second: Float
        get() = _second
        set(v) {
            if (_second != v) {
                _second = v
                isDirty = true
            }
        }

    fun set(first: Float, second: Float) {
        if (_first != first || _second != second) {
            _first = first
            _second = second
            isDirty = true
        }
    }
}

class Float3Uniform internal constructor() : UniformHandle() {
    private var _first = 0f
    private var _second = 0f
    private var _third = 0f

    var first: Float
        get() = _first
        set(v) {
            if (_first != v) {
                _first = v
                isDirty = true
            }
        }
    var second: Float
        get() = _second
        set(v) {
            if (_second != v) {
                _second = v
                isDirty = true
            }
        }
    var third: Float
        get() = _third
        set(v) {
            if (_third != v) {
                _third = v
                isDirty = true
            }
        }

    fun set(first: Float, second: Float, third: Float) {
        if (_first != first || _second != second || _third != third) {
            _first = first
            _second = second
            _third = third
            isDirty = true
        }
    }
}

class Float4Uniform internal constructor() : UniformHandle() {
    private var _first = 0f
    private var _second = 0f
    private var _third = 0f
    private var _fourth = 0f

    var first: Float
        get() = _first
        set(v) {
            if (_first != v) {
                _first = v
                isDirty = true
            }
        }
    var second: Float
        get() = _second
        set(v) {
            if (_second != v) {
                _second = v
                isDirty = true
            }
        }
    var third: Float
        get() = _third
        set(v) {
            if (_third != v) {
                _third = v
                isDirty = true
            }
        }
    var fourth: Float
        get() = _fourth
        set(v) {
            if (_fourth != v) {
                _fourth = v
                isDirty = true
            }
        }

    fun set(first: Float, second: Float, third: Float, fourth: Float) {
        if (_first != first || _second != second || _third != third || _fourth != fourth) {
            _first = first
            _second = second
            _third = third
            _fourth = fourth
            isDirty = true
        }
    }
}

class Int1Uniform internal constructor() : UniformHandle() {
    var value = 0
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }

    fun set(v: Int) {
        if (value != v) {
            value = v
        }
    }
}

class Mat4Uniform internal constructor() : UniformHandle() {
    val value = Matrix4f()
    var projectionVersion = -1
}
