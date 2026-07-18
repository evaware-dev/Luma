package sweetie.evaware.luma.texture

internal data class AtlasAllocation(val x: Int, val y: Int)

internal interface AtlasAllocator {
    fun allocate(width: Int, height: Int, atlasSize: TextureAtlasSize): AtlasAllocation?
}

internal class ShelfAtlasAllocator(private val padding: Int) : AtlasAllocator {
    private var cursorX = 0
    private var cursorY = 0
    private var rowHeight = 0

    override fun allocate(width: Int, height: Int, atlasSize: TextureAtlasSize): AtlasAllocation? {
        val paddedWidth = width + padding
        val paddedHeight = height + padding
        if (paddedWidth > atlasSize.width || paddedHeight > atlasSize.height) return null

        var nextX = cursorX
        var nextY = cursorY
        var nextRowHeight = rowHeight
        if (nextX + paddedWidth > atlasSize.width) {
            nextX = 0
            nextY += nextRowHeight
            nextRowHeight = 0
        }
        if (nextY + paddedHeight > atlasSize.height) return null

        val allocation = AtlasAllocation(nextX, nextY)
        cursorX = nextX + paddedWidth
        cursorY = nextY
        rowHeight = maxOf(nextRowHeight, paddedHeight)
        return allocation
    }
}
