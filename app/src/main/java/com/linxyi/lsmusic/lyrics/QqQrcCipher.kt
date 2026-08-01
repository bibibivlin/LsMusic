package com.linxyi.lsmusic.lyrics

/**
 * QQ QRC uses a legacy, bit-oriented DES variant whose byte ordering is not compatible with
 * JCE's DES implementation. The schedule is based on the interoperable implementations in
 * jitwxs/163MusicLyrics (Apache-2.0) and jixunmoe-go/qrc (MIT).
 *
 * This code only decodes public lyric payloads. It must not be used as general-purpose crypto.
 */
internal object QqQrcCipher {
    private val firstKey = "!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private val secondKey = "123ZXC!@".toByteArray(Charsets.US_ASCII)
    private val thirdKey = "!@#)(*$%".toByteArray(Charsets.US_ASCII)

    fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size % BLOCK_SIZE == 0) { "QRC 密文长度不是 8 字节的整数倍" }
        return encrypted.copyOf().also { result ->
            QqDes(firstKey, encrypt = false).transform(result)
            QqDes(secondKey, encrypt = true).transform(result)
            QqDes(thirdKey, encrypt = false).transform(result)
        }
    }

    private const val BLOCK_SIZE = 8
}

private class QqDes(key: ByteArray, encrypt: Boolean) {
    private val subKeys = LongArray(16)

    init {
        require(key.size == 8)
        setKey(key, encrypt)
    }

    fun transform(bytes: ByteArray) {
        require(bytes.size % 8 == 0)
        for (offset in bytes.indices step 8) {
            writeLittleEndian(transformBlock(readLittleEndian(bytes, offset)), bytes, offset)
        }
    }

    private fun setKey(bytes: ByteArray, encrypt: Boolean) {
        val permuted = mapBits(readLittleEndian(bytes, 0), KEY_PERMUTATION)
        var c = low32(permuted)
        var d = high32(permuted)
        KEY_ROUND_SHIFTS.forEachIndexed { round, shift ->
            c = rotateKeyHalf(c, shift)
            d = rotateKeyHalf(d, shift)
            subKeys[if (encrypt) round else 15 - round] = mapBits(combine32(d, c), KEY_COMPRESSION)
        }
    }

    private fun transformBlock(value: Long): Long {
        var state = mapBits(value, INITIAL_PERMUTATION)
        subKeys.forEach { key -> state = cryptRound(state, key) }
        return mapBits(swapHalves(state), INVERSE_INITIAL_PERMUTATION)
    }

    private fun cryptRound(state: Long, key: Long): Long {
        val right = high32(state)
        val left = low32(state)
        val expanded = mapBits(combine32(right, right), KEY_EXPANSION) xor key
        val substituted = sBoxTransform(expanded)
        val nextRight = map32Bits(substituted, P_BOX) xor left
        return combine32(nextRight, right)
    }

    private fun sBoxTransform(value: Long): Long {
        var result = 0L
        LARGE_STATE_SHIFTS.forEachIndexed { index, shift ->
            val sBoxIndex = ((value ushr shift) and 0x3fL).toInt()
            result = (result shl 4) or S_BOXES[index][sBoxIndex].toLong()
        }
        return result and UINT_MASK
    }

    private fun rotateKeyHalf(value: Long, shift: Int): Long {
        val shifted = (value shl shift) and UINT_MASK
        val wrapped = (value ushr (28 - shift)) and 0xfffffff0L
        return (shifted or wrapped) and UINT_MASK
    }

    private companion object {
        const val UINT_MASK = 0xffffffffL

        val KEY_ROUND_SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
        val LARGE_STATE_SHIFTS = intArrayOf(26, 20, 14, 8, 58, 52, 46, 40)

        val S_BOXES = arrayOf(
            intArrayOf(14, 0, 4, 15, 13, 7, 1, 4, 2, 14, 15, 2, 11, 13, 8, 1, 3, 10, 10, 6, 6, 12, 12, 11, 5, 9, 9, 5, 0, 3, 7, 8, 4, 15, 1, 12, 14, 8, 8, 2, 13, 4, 6, 9, 2, 1, 11, 7, 15, 5, 12, 11, 9, 3, 7, 14, 3, 10, 10, 0, 5, 6, 0, 13),
            intArrayOf(15, 3, 1, 13, 8, 4, 14, 7, 6, 15, 11, 2, 3, 8, 4, 15, 9, 12, 7, 0, 2, 1, 13, 10, 12, 6, 0, 9, 5, 11, 10, 5, 0, 13, 14, 8, 7, 10, 11, 1, 10, 3, 4, 15, 13, 4, 1, 2, 5, 11, 8, 6, 12, 7, 6, 12, 9, 0, 3, 5, 2, 14, 15, 9),
            intArrayOf(10, 13, 0, 7, 9, 0, 14, 9, 6, 3, 3, 4, 15, 6, 5, 10, 1, 2, 13, 8, 12, 5, 7, 14, 11, 12, 4, 11, 2, 15, 8, 1, 13, 1, 6, 10, 4, 13, 9, 0, 8, 6, 15, 9, 3, 8, 0, 7, 11, 4, 1, 15, 2, 14, 12, 3, 5, 11, 10, 5, 14, 2, 7, 12),
            intArrayOf(7, 13, 13, 8, 14, 11, 3, 5, 0, 6, 6, 15, 9, 0, 10, 3, 1, 4, 2, 7, 8, 2, 5, 12, 11, 1, 12, 10, 4, 14, 15, 9, 10, 3, 6, 15, 9, 0, 0, 6, 12, 10, 11, 10, 7, 13, 13, 8, 15, 9, 1, 4, 3, 5, 14, 11, 5, 12, 2, 7, 8, 2, 4, 14),
            intArrayOf(2, 14, 12, 11, 4, 2, 1, 12, 7, 4, 10, 7, 11, 13, 6, 1, 8, 5, 5, 0, 3, 15, 15, 10, 13, 3, 0, 9, 14, 8, 9, 6, 4, 11, 2, 8, 1, 12, 11, 7, 10, 1, 13, 14, 7, 2, 8, 13, 15, 6, 9, 15, 12, 0, 5, 9, 6, 10, 3, 4, 0, 5, 14, 3),
            intArrayOf(12, 10, 1, 15, 10, 4, 15, 2, 9, 7, 2, 12, 6, 9, 8, 5, 0, 6, 13, 1, 3, 13, 4, 14, 14, 0, 7, 11, 5, 3, 11, 8, 9, 4, 14, 3, 15, 2, 5, 12, 2, 9, 8, 5, 12, 15, 3, 10, 7, 11, 0, 14, 4, 1, 10, 7, 1, 6, 13, 0, 11, 8, 6, 13),
            intArrayOf(4, 13, 11, 0, 2, 11, 14, 7, 15, 4, 0, 9, 8, 1, 13, 10, 3, 14, 12, 3, 9, 5, 7, 12, 5, 2, 10, 15, 6, 8, 1, 6, 1, 6, 4, 11, 11, 13, 13, 8, 12, 1, 3, 4, 7, 10, 14, 7, 10, 9, 15, 5, 6, 0, 8, 15, 0, 14, 5, 2, 9, 3, 2, 12),
            intArrayOf(13, 1, 2, 15, 8, 13, 4, 8, 6, 10, 15, 3, 11, 7, 1, 4, 10, 12, 9, 5, 3, 6, 14, 11, 5, 0, 0, 14, 12, 9, 7, 2, 7, 2, 11, 1, 4, 14, 1, 7, 9, 4, 12, 10, 14, 8, 2, 13, 0, 15, 6, 12, 10, 9, 13, 0, 15, 3, 3, 5, 5, 6, 8, 11),
        )

        val P_BOX = intArrayOf(
            15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
            1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24,
        )

        val INITIAL_PERMUTATION = intArrayOf(
            57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
            61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
            56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
            60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
        )

        val INVERSE_INITIAL_PERMUTATION = intArrayOf(
            39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30,
            37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28,
            35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26,
            33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24,
        )

        val KEY_PERMUTATION = intArrayOf(
            56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
            58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38,
            30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36,
            28, 20, 12, 4, 27, 19, 11, 3,
        )

        val KEY_COMPRESSION = intArrayOf(
            13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
            25, 7, 15, 6, 26, 19, 12, 1, 45, 56, 35, 41, 51, 59, 34, 44,
            55, 49, 37, 52, 48, 53, 43, 60, 38, 57, 50, 46, 54, 40, 33, 36,
        )

        val KEY_EXPANSION = intArrayOf(
            31, 0, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8, 7, 8, 9, 10,
            11, 12, 11, 12, 13, 14, 15, 16, 15, 16, 17, 18, 19, 20, 19, 20,
            21, 22, 23, 24, 23, 24, 25, 26, 27, 28, 27, 28, 29, 30, 31, 0,
        )

        fun readLittleEndian(bytes: ByteArray, offset: Int): Long {
            var result = 0L
            repeat(8) { index ->
                result = result or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8))
            }
            return result
        }

        fun writeLittleEndian(value: Long, destination: ByteArray, offset: Int) {
            repeat(8) { index -> destination[offset + index] = (value ushr (index * 8)).toByte() }
        }

        fun mapBits(source: Long, table: IntArray): Long {
            val middle = table.size / 2
            var result = 0L
            table.forEachIndexed { index, sourceBit ->
                if (source and bitMask(sourceBit) == 0L) return@forEachIndexed
                val localIndex = if (index < middle) index else index - middle
                val destinationShift = 31 - localIndex + if (index < middle) 0 else 32
                result = result or (1L shl destinationShift)
            }
            return result
        }

        fun map32Bits(source: Long, table: IntArray): Long {
            var result = 0L
            table.forEachIndexed { index, sourceBit ->
                if (source and bitMask(sourceBit) != 0L) result = result or bitMask(index)
            }
            return result and UINT_MASK
        }

        fun bitMask(index: Int): Long = if (index < 32) {
            1L shl (31 - index)
        } else {
            1L shl (95 - index)
        }

        fun low32(value: Long): Long = value and UINT_MASK

        fun high32(value: Long): Long = (value ushr 32) and UINT_MASK

        fun combine32(high: Long, low: Long): Long =
            ((high and UINT_MASK) shl 32) or (low and UINT_MASK)

        fun swapHalves(value: Long): Long = combine32(low32(value), high32(value))
    }
}
