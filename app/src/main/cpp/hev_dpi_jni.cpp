// Own JNI bridge for hev-socks5-tunnel (tun2socks) used by DpiEngine.
// MIT-licensed own code; does NOT include or copy upstream JNI wrappers.
// HEV owns the tun fd after nativeStart succeeds and closes it on hev_socks5_tunnel_quit().

#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <cstring>

#include <hev-main.h>

#define TAG "SvobodaDpi"

namespace {

std::thread g_hev_thread;
std::atomic<bool> g_hev_running{false};
std::atomic<int> g_hev_rc{0};

using FnMain = int (*)(const unsigned char *, unsigned int, int);

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_svoboda_runet_abonent0007_dpi_HevBridge_nativeStart(
        JNIEnv *env, jclass unused, jstring jconfig, jint tun_fd) {
    if (tun_fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "nativeStart: invalid tun_fd=%d", tun_fd);
        return JNI_FALSE;
    }
    if (g_hev_running.load()) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "nativeStart called while HEV already running");
        return JNI_TRUE;
    }

    const char *cfg = env->GetStringUTFChars(jconfig, nullptr);
    if (!cfg) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "nativeStart: null config");
        return JNI_FALSE;
    }
    std::string config(cfg);
    env->ReleaseStringUTFChars(jconfig, cfg);

    g_hev_rc = 0;
    g_hev_running = true;
    try {
        g_hev_thread = std::thread([config, tun_fd]() {
            int rc = hev_socks5_tunnel_main_from_str(
                    reinterpret_cast<const unsigned char *>(config.c_str()),
                    static_cast<unsigned int>(config.size()),
                    static_cast<int>(tun_fd));
            g_hev_rc = rc;
            g_hev_running = false;
            __android_log_print(ANDROID_LOG_INFO, TAG, "HEV loop exited rc=%d", rc);
        });
    } catch (...) {
        g_hev_running = false;
        __android_log_print(ANDROID_LOG_ERROR, TAG, "nativeStart: thread creation failed");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_svoboda_runet_abonent0007_dpi_HevBridge_nativeStop(
        JNIEnv *env, jclass unused) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "nativeStop: requesting HEV quit");
    hev_socks5_tunnel_quit();

    if (g_hev_thread.joinable()) {
        g_hev_thread.join();
        __android_log_print(ANDROID_LOG_INFO, TAG, "nativeStop: HEV thread joined, rc=%d", g_hev_rc.load());
    } else {
        __android_log_print(ANDROID_LOG_INFO, TAG, "nativeStop: no HEV thread running");
    }
    // HEV owns and closes tun_fd on normal exit; do NOT close it here.
}

extern "C" JNIEXPORT jboolean JNICALL
Java_svoboda_runet_abonent0007_dpi_HevBridge_nativeIsRunning(
        JNIEnv *env, jclass unused) {
    return g_hev_running.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_svoboda_runet_abonent0007_dpi_HevBridge_nativeLastExitCode(
        JNIEnv *env, jclass unused) {
    return static_cast<jint>(g_hev_rc.load());
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_svoboda_runet_abonent0007_dpi_HevBridge_nativeStats(
        JNIEnv *env, jclass unused) {
    jlongArray result = env->NewLongArray(4);
    size_t tx_packets = 0, tx_bytes = 0, rx_packets = 0, rx_bytes = 0;
    hev_socks5_tunnel_stats(&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);
    const jlong data[4] = {
            static_cast<jlong>(tx_packets),
            static_cast<jlong>(tx_bytes),
            static_cast<jlong>(rx_packets),
            static_cast<jlong>(rx_bytes),
    };
    env->SetLongArrayRegion(result, 0, 4, data);
    return result;
}
