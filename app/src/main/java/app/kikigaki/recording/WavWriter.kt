package app.kikigaki.recording

import java.io.File
import java.io.RandomAccessFile

class WavWriter(private val file: File, private val sampleRate: Int) {
    private var dataSize = 0
    private val channels = 1
    private val bitsPerSample = 16

    init {
        // 事前にヘッダ領域(44バイト)を確保して PCM を追記できるようにする
        RandomAccessFile(file, "rw").use { raf ->
            if (raf.length() == 0L) raf.write(ByteArray(44))
        }
    }

    fun write(pcm: ByteArray, offset: Int, length: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(pcm, offset, length)
            dataSize += length
        }
    }

    fun close() {
        writeHeader()
    }

    private fun writeHeader() {
        val totalDataBytes = dataSize
        val totalFileSize = 36 + totalDataBytes
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength((44 + totalDataBytes).toLong())
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            writeInt4(header, 4, totalFileSize)
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            writeInt4(header, 16, 16)
            writeInt2(header, 20, 1)
            writeInt2(header, 22, channels)
            writeInt4(header, 24, sampleRate)
            writeInt4(header, 28, byteRate)
            writeInt2(header, 32, blockAlign)
            writeInt2(header, 34, bitsPerSample)
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            writeInt4(header, 40, totalDataBytes)
            raf.seek(0)
            raf.write(header)
        }
    }

    private fun writeInt4(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xff).toByte()
        b[offset + 1] = ((value shr 8) and 0xff).toByte()
        b[offset + 2] = ((value shr 16) and 0xff).toByte()
        b[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun writeInt2(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xff).toByte()
        b[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
}
