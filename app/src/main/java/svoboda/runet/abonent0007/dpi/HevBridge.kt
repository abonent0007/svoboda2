package svoboda.runet.abonent0007.dpi

/**
 * JNI-мост к hev-socks5-tunnel.
 *
 * HEV принимает TUN fd при nativeStart() и закрывает его сам при штатном
 * завершении (после nativeStop() -> hev_socks5_tunnel_quit()). Не закрывать fd
 * на Kotlin-стороне.
 */
object HevBridge {
    init {
        System.loadLibrary("hev-socks5-tunnel")
        System.loadLibrary("svoboda-dpi")
    }

    /** Запустить tun2socks. Блокируется до завершения (в фоне внутри JNI). */
    @JvmStatic external fun nativeStart(config: String, tunFd: Int): Boolean

    /** Остановить tun2socks и дождаться завершения потока HEV. */
    @JvmStatic external fun nativeStop()

    @JvmStatic external fun nativeIsRunning(): Boolean

    @JvmStatic external fun nativeLastExitCode(): Int

    /** [tx_packets, tx_bytes, rx_packets, rx_bytes] */
    @JvmStatic external fun nativeStats(): LongArray
}
