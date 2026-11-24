package sweetie.evaware.luma.backend.gl

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import sweetie.evaware.luma.api.SamplerState
import sweetie.evaware.luma.api.TextureFormat
import sweetie.evaware.luma.api.TextureHandle
import sweetie.evaware.luma.texture.RgbaTransferBuffer

class GlTexture @JvmOverloads constructor(
    val textureId: Int,
    override val width: Int,
    override val height: Int,
    internal var mipmap: Boolean = false,
    internal var samplerId: Int = 0,
    private val ownsTexture: Boolean = true,
    override val format: TextureFormat = TextureFormat.RGBA8,
    internal var samplerState: SamplerState = SamplerState.LINEAR_CLAMP,
    private var ownsSampler: Boolean = samplerId != 0
) : TextureHandle {
    private var closed = false

    internal fun requireOpen() = check(!closed) { "Texture is closed" }

    internal fun replaceSampler(sampler: SamplerState, newSamplerId: Int, newOwnsSampler: Boolean) {
        if (ownsSampler && samplerId != 0 && samplerId != newSamplerId) {
            GL33.glDeleteSamplers(samplerId)
        }
        samplerState = sampler
        samplerId = newSamplerId
        ownsSampler = newOwnsSampler
    }

    fun update(x: Int, y: Int, image: BufferedImage) {
        requireOpen()
        validateRegion(x, y, image.width, image.height)
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val pixelStore = capturePixelStore(pixelStoreState.get())
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            setRequiredPixelStore()
            updateBound(x, y, image, transfer.get())
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            restorePixelStore(pixelStore)
        }
    }

    fun update(x: Int, y: Int, width: Int, height: Int, data: ByteBuffer) {
        requireOpen()
        validateRawRegion(x, y, width, height, data)
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val pixelStore = capturePixelStore(pixelStoreState.get())
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            setRequiredPixelStore()
            updateBoundRaw(x, y, width, height, data)
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            restorePixelStore(pixelStore)
        }
    }

    fun bind(unit: Int) {
        requireOpen()
        val activeUnit = GL13.GL_TEXTURE0 + unit
        GL13.glActiveTexture(activeUnit)
        bindCurrentUnit()
        GL33.glBindSampler(unit, samplerId)
    }

    internal fun bindCurrentUnit() {
        requireOpen()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
    }

    internal fun generateMipmapsBound() {
        requireOpen()
        mipmap = true
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 1000)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, -1000)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 1000)
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
    }

    internal fun generateMipmapsUnbound() {
        requireOpen()
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            generateMipmapsBound()
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
        }
    }

    internal fun initializeBound(image: BufferedImage, transfer: RgbaTransferBuffer) {
        configureParameters()
        val buffer = transfer.write(image, format)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            format.glInternalFormat,
            image.width,
            image.height,
            0,
            format.glPixelFormat,
            format.glPixelType,
            buffer
        )
        if (mipmap) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
    }

    internal fun initializeBoundRaw(data: ByteBuffer?) {
        if (data != null) {
            val requiredBytes = format.byteSize(width, height)
            require(data.remaining() >= requiredBytes) {
                "Texture data has ${data.remaining()} bytes, expected at least $requiredBytes bytes for $format ${width}x${height}"
            }
        }
        configureParameters()
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            format.glInternalFormat,
            width,
            height,
            0,
            format.glPixelFormat,
            format.glPixelType,
            data
        )
        if (mipmap && data != null) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
    }

    internal fun updateBound(x: Int, y: Int, image: BufferedImage, transfer: RgbaTransferBuffer) {
        requireOpen()
        validateRegion(x, y, image.width, image.height)
        val buffer = transfer.write(image, format)
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            x,
            y,
            image.width,
            image.height,
            format.glPixelFormat,
            format.glPixelType,
            buffer
        )
        if (mipmap) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
    }

    internal fun updateBoundRaw(x: Int, y: Int, width: Int, height: Int, data: ByteBuffer) {
        requireOpen()
        validateRawRegion(x, y, width, height, data)
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            x,
            y,
            width,
            height,
            format.glPixelFormat,
            format.glPixelType,
            data
        )
        if (mipmap) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
    }

    private fun configureParameters() {
        if (!mipmap) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 0)
        }
        GL11.glTexParameteri(
            GL11.GL_TEXTURE_2D,
            GL11.GL_TEXTURE_MIN_FILTER,
            if (mipmap) GL11.GL_LINEAR_MIPMAP_LINEAR else GL11.GL_LINEAR
        )
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0f)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
    }

    private fun validateRegion(x: Int, y: Int, regionWidth: Int, regionHeight: Int) {
        require(x >= 0 && y >= 0 && regionWidth > 0 && regionHeight > 0) {
            "Texture update region must have non-negative origin and positive size"
        }
        require(x.toLong() + regionWidth <= width && y.toLong() + regionHeight <= height) {
            "Texture update region [$x, $y, ${regionWidth}x${regionHeight}] exceeds texture [${width}x${height}]"
        }
    }

    private fun validateRawRegion(x: Int, y: Int, regionWidth: Int, regionHeight: Int, data: ByteBuffer) {
        validateRegion(x, y, regionWidth, regionHeight)
        val requiredBytes = format.byteSize(regionWidth, regionHeight)
        require(data.remaining() >= requiredBytes) {
            "Texture data has ${data.remaining()} bytes, expected at least $requiredBytes bytes for $format ${regionWidth}x${regionHeight}"
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (ownsSampler && samplerId != 0) GL33.glDeleteSamplers(samplerId)
        if (ownsTexture) GL11.glDeleteTextures(textureId)
    }

    companion object {
        internal val TextureFormat.glInternalFormat: Int
            get() = when (this) {
                TextureFormat.RGBA8 -> GL11.GL_RGBA8
                TextureFormat.RG8 -> GL30.GL_RG8
                TextureFormat.R8 -> GL30.GL_R8
                TextureFormat.RGBA16F -> GL30.GL_RGBA16F
            }

        internal val TextureFormat.glPixelFormat: Int
            get() = when (this) {
                TextureFormat.RGBA8, TextureFormat.RGBA16F -> GL11.GL_RGBA
                TextureFormat.RG8 -> GL30.GL_RG
                TextureFormat.R8 -> GL11.GL_RED
            }

        internal val TextureFormat.glPixelType: Int
            get() = when (this) {
                TextureFormat.RGBA8, TextureFormat.RG8, TextureFormat.R8 -> GL11.GL_UNSIGNED_BYTE
                TextureFormat.RGBA16F -> GL30.GL_HALF_FLOAT
            }

        @JvmOverloads
        fun borrow(
            textureId: Int,
            width: Int,
            height: Int,
            sampler: SamplerState = SamplerState.NEAREST_CLAMP
        ): GlTexture {
            val samplerId = GlSamplerCache.createOwnedSampler(sampler, mipmap = false)
            return GlTexture(
                textureId = textureId,
                width = width,
                height = height,
                mipmap = false,
                samplerId = samplerId,
                ownsTexture = false,
                format = TextureFormat.RGBA8,
                samplerState = sampler,
                ownsSampler = true
            )
        }

        @JvmOverloads
        fun create(
            image: BufferedImage,
            mipmap: Boolean,
            format: TextureFormat = TextureFormat.RGBA8,
            sampler: SamplerState = SamplerState.LINEAR_CLAMP,
            samplerId: Int = 0,
            ownsSampler: Boolean = samplerId == 0
        ): GlTexture {
            require(format.supportsBufferedImageUpload) {
                "BufferedImage upload is not supported for format $format; upload raw ByteBuffer data instead"
            }
            val resolvedSamplerId = if (samplerId != 0) samplerId else GlSamplerCache.createOwnedSampler(sampler, mipmap)
            val resolvedOwnsSampler = if (samplerId != 0) ownsSampler else true
            val textureId = GL11.glGenTextures()
            val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            val pixelStore = capturePixelStore(pixelStoreState.get())

            val texture = GlTexture(
                textureId = textureId,
                width = image.width,
                height = image.height,
                mipmap = mipmap,
                samplerId = resolvedSamplerId,
                ownsTexture = true,
                format = format,
                samplerState = sampler,
                ownsSampler = resolvedOwnsSampler
            )
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
                setRequiredPixelStore()
                texture.initializeBound(image, transfer.get())
                return texture
            } catch (failure: Throwable) {
                texture.close()
                throw failure
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                restorePixelStore(pixelStore)
            }
        }

        @JvmOverloads
        fun createRaw(
            width: Int,
            height: Int,
            data: ByteBuffer?,
            format: TextureFormat = TextureFormat.RGBA8,
            mipmap: Boolean = false,
            sampler: SamplerState = SamplerState.LINEAR_CLAMP,
            samplerId: Int = 0,
            ownsSampler: Boolean = samplerId == 0
        ): GlTexture {
            require(width > 0 && height > 0) { "Texture dimensions must be positive: ${width}x${height}" }
            if (data != null) {
                val requiredBytes = format.byteSize(width, height)
                require(data.remaining() >= requiredBytes) {
                    "Texture data has ${data.remaining()} bytes, expected at least $requiredBytes bytes for $format ${width}x${height}"
                }
            }
            val resolvedSamplerId = if (samplerId != 0) samplerId else GlSamplerCache.createOwnedSampler(sampler, mipmap)
            val resolvedOwnsSampler = if (samplerId != 0) ownsSampler else true
            val textureId = GL11.glGenTextures()
            val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            val pixelStore = capturePixelStore(pixelStoreState.get())

            val texture = GlTexture(
                textureId = textureId,
                width = width,
                height = height,
                mipmap = mipmap,
                samplerId = resolvedSamplerId,
                ownsTexture = true,
                format = format,
                samplerState = sampler,
                ownsSampler = resolvedOwnsSampler
            )
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
                setRequiredPixelStore()
                texture.initializeBoundRaw(data)
                return texture
            } catch (failure: Throwable) {
                texture.close()
                throw failure
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
                restorePixelStore(pixelStore)
            }
        }

        fun closeTransferBuffer() {
            transfer.get().close()
            transfer.remove()
            pixelStoreState.remove()
        }

        private fun setRequiredPixelStore() {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        }

        private fun capturePixelStore(state: IntArray): IntArray {
            state[0] = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
            state[1] = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)
            state[2] = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS)
            state[3] = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)
            return state
        }

        private fun restorePixelStore(state: IntArray) {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, state[0])
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, state[1])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, state[2])
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, state[3])
        }

        private val transfer = ThreadLocal.withInitial(::RgbaTransferBuffer)
        private val pixelStoreState = ThreadLocal.withInitial { IntArray(4) }
    }
}
