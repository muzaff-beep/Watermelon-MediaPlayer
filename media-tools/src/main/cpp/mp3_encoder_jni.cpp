// JNI bridge between Mp3Encoder.kt and libmp3lame.
//
// NOT COMPILED/VERIFIED in this environment (no NDK toolchain available in this
// sandbox — see media-tools/src/main/cpp/lame/README.md). The lame_*() call
// signatures below match the long-stable classic liblame public API
// (lame.h, unchanged across 3.99.x/3.100), but this file has not been built
// against real headers here — verify against the actual vendored lame.h
// once it's dropped in per the README before trusting this to compile as-is.

#include <jni.h>
#include <cstdint>
#include <cstring>
#include <android/log.h>
#include "lame.h"

#define LOG_TAG "Mp3EncoderJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Returns an opaque handle (the lame_global_flags*) cast to jlong, or 0 on failure.
JNIEXPORT jlong JNICALL
Java_com_watermelon_mediatools_engine_Mp3Encoder_nativeInit(
        JNIEnv *env, jobject /* this */,
        jint sampleRateHz, jint numChannels, jint bitrateKbps) {
    lame_global_flags *gfp = lame_init();
    if (gfp == nullptr) {
        LOGE("lame_init() failed");
        return 0;
    }

    lame_set_in_samplerate(gfp, sampleRateHz);
    lame_set_num_channels(gfp, numChannels);
    lame_set_brate(gfp, bitrateKbps);
    lame_set_quality(gfp, 2); // 0=best/slowest, 9=worst/fastest; 2 is a common "high quality" default

    if (lame_init_params(gfp) < 0) {
        LOGE("lame_init_params() failed");
        lame_close(gfp);
        return 0;
    }

    return reinterpret_cast<jlong>(gfp);
}

// pcmShorts: interleaved 16-bit PCM samples (if stereo: L,R,L,R,...).
// Returns number of bytes written into outMp3Buffer, or negative lame error code.
JNIEXPORT jint JNICALL
Java_com_watermelon_mediatools_engine_Mp3Encoder_nativeEncodeChunk(
        JNIEnv *env, jobject /* this */,
        jlong handle, jshortArray pcmShorts, jint numSamplesPerChannel,
        jbyteArray outMp3Buffer) {
    auto *gfp = reinterpret_cast<lame_global_flags *>(handle);
    if (gfp == nullptr) return -1;

    jshort *pcm = env->GetShortArrayElements(pcmShorts, nullptr);
    jbyte *outBuf = env->GetByteArrayElements(outMp3Buffer, nullptr);
    jsize outCapacity = env->GetArrayLength(outMp3Buffer);

    // Assumes interleaved stereo input via lame_encode_buffer_interleaved.
    // Mono callers should still pack samples the same way; lame handles
    // channel count internally based on nativeInit's numChannels.
    int written = lame_encode_buffer_interleaved(
            gfp,
            reinterpret_cast<short int *>(pcm),
            numSamplesPerChannel,
            reinterpret_cast<unsigned char *>(outBuf),
            outCapacity
    );

    env->ReleaseShortArrayElements(pcmShorts, pcm, JNI_ABORT);
    env->ReleaseByteArrayElements(outMp3Buffer, outBuf, written > 0 ? 0 : JNI_ABORT);

    return written;
}

// Flushes any buffered frames at end-of-stream. Same output-buffer contract as encodeChunk.
JNIEXPORT jint JNICALL
Java_com_watermelon_mediatools_engine_Mp3Encoder_nativeFlush(
        JNIEnv *env, jobject /* this */,
        jlong handle, jbyteArray outMp3Buffer) {
    auto *gfp = reinterpret_cast<lame_global_flags *>(handle);
    if (gfp == nullptr) return -1;

    jbyte *outBuf = env->GetByteArrayElements(outMp3Buffer, nullptr);
    jsize outCapacity = env->GetArrayLength(outMp3Buffer);

    int written = lame_encode_flush(gfp, reinterpret_cast<unsigned char *>(outBuf), outCapacity);

    env->ReleaseByteArrayElements(outMp3Buffer, outBuf, written > 0 ? 0 : JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_com_watermelon_mediatools_engine_Mp3Encoder_nativeClose(
        JNIEnv *env, jobject /* this */, jlong handle) {
    auto *gfp = reinterpret_cast<lame_global_flags *>(handle);
    if (gfp != nullptr) {
        lame_close(gfp);
    }
}

} // extern "C"
