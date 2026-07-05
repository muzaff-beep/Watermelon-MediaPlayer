package com.watermelon.subtitle.hash

import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * OpenSubtitles file-hash algorithm (Manifest §6.1). The hash is the 64-bit sum of:
 *   - the file size, plus
 *   - every little-endian unsigned 64-bit word in the first 64 KB, plus
 *   - every little-endian unsigned 64-bit word in the last 64 KB.
 *
 * Arithmetic is performed mod 2^64 (natural [Long] overflow). The result is rendered as a
 * zero-padded 16-char lowercase hex string, matching the reference implementation/test
 * vectors.
 */
object OpenSubtitlesHasher {

    private const val CHUNK_SIZE = 64 * 1024 // 64 KB

    fun hash(file: File): String {
        val fileSize = file.length()
        require(fileSize >= CHUNK_SIZE) {
            "File must be at least $CHUNK_SIZE bytes for OpenSubtitles hashing"
        }
        RandomAccessFile(file, "r").use { raf ->
            return hash(raf.channel, fileSize)
        }
    }

    /**
     * Same algorithm as [hash], but reading through an already-open [FileDescriptor] and an
     * externally-known [fileSize] — used for `content://` URIs (e.g. MediaStore videos under
     * scoped storage) where there is no plain [File] path to construct a [RandomAccessFile]
     * from directly. Callers typically obtain the descriptor via
     * `ContentResolver.openFileDescriptor(uri, "r")` and are responsible for closing it
     * (this function only closes the [FileInputStream]/channel it wraps around the
     * descriptor, not the descriptor itself).
     */
    fun hash(fd: FileDescriptor, fileSize: Long): String {
        require(fileSize >= CHUNK_SIZE) {
            "File must be at least $CHUNK_SIZE bytes for OpenSubtitles hashing"
        }
        FileInputStream(fd).channel.use { channel ->
            return hash(channel, fileSize)
        }
    }

    private fun hash(channel: FileChannel, fileSize: Long): String {
        var hash = fileSize
        hash += sumChunk(channel, 0L)
        hash += sumChunk(channel, fileSize - CHUNK_SIZE)
        return toHex(hash)
    }

    /** Sum the little-endian uint64 words of the 64 KB chunk starting at [offset]. */
    private fun sumChunk(channel: FileChannel, offset: Long): Long {
        val buffer = ByteBuffer.allocate(CHUNK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(offset)
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer)
            if (read < 0) break
        }
        buffer.flip()
        var sum = 0L
        val words = CHUNK_SIZE / 8
        for (i in 0 until words) {
            sum += buffer.long // wraps mod 2^64, which is the intended behaviour
        }
        return sum
    }

    private fun toHex(value: Long): String =
        String.format("%016x", value)
}
