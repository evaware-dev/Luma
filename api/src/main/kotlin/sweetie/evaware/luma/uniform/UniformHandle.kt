package sweetie.evaware.luma.uniform

import org.joml.Matrix4f

sealed class UniformHandle {
    var isDirty = true
}

class Float1Uniform internal constructor() : UniformHandle() {
    var value = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
}

class Float2Uniform internal constructor() : UniformHandle() {
    var first = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
    var second = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
}

class Float3Uniform internal constructor() : UniformHandle() {
    var first = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
    var second = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
    var third = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
}

class Float4Uniform internal constructor() : UniformHandle() {
    var first = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
    var second = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
    var third = 0f
        set(v) {
            if (field != v) {
                field = v
                isDirty = true
            }
        }
    var fourth = 0f
        set(v) {
            if (field != v) {
                field = v
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
}

class Mat4Uniform internal constructor() : UniformHandle() {
    val value = Matrix4f()
    var projectionVersion = -1
}
