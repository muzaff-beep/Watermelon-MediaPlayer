package com.watermelon.app

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.watermelon.common.model.VhsTier

/**
 * Builds the VHS RenderEffect using an AGSL shader (API 33+). The shader composites,
 * by tier:
 *   - Scanlines  (all tiers): darkened horizontal lines, animated drift
 *   - Jitter     (A,B): horizontal row offset that wobbles over time
 *   - Tracking   (A): a moving band of strong horizontal tearing + chroma shift
 *
 * [intensity] 0..1 scales the strength of every element. [time] advances the animation
 * (pass elapsed seconds). The effect is only meant to run during FF/FR.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object VhsShader {

    private val AGSL = """
        uniform shader inputShader;
        uniform float2 resolution;
        uniform float  intensity;
        uniform float  time;
        uniform float  enableJitter;
        uniform float  enableTracking;

        // cheap hash noise
        float hash(float2 p) {
            return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 coord) {
            float2 uv = coord / resolution;

            // ── Jitter: offset each row horizontally by a time-varying noise ──
            float jitter = 0.0;
            if (enableJitter > 0.5) {
                float n = hash(float2(floor(uv.y * resolution.y * 0.5), floor(time * 30.0)));
                jitter = (n - 0.5) * intensity * 0.03;
            }

            // ── Tracking distortion: a moving horizontal band of strong tearing ──
            float trackShift = 0.0;
            float band = 0.0;
            if (enableTracking > 0.5) {
                float bandPos = fract(time * 0.4);
                float d = abs(uv.y - bandPos);
                band = smoothstep(0.06, 0.0, d);
                trackShift = band * intensity * 0.08 * (hash(float2(time, uv.y)) - 0.5) * 2.0;
            }

            float xOff = jitter + trackShift;

            // ── Chroma separation (stronger inside the tracking band) ──
            float chroma = intensity * 0.004 + band * intensity * 0.02;
            half4 cr = inputShader.eval(float2((uv.x + xOff + chroma) * resolution.x, coord.y));
            half4 cg = inputShader.eval(float2((uv.x + xOff)          * resolution.x, coord.y));
            half4 cb = inputShader.eval(float2((uv.x + xOff - chroma) * resolution.x, coord.y));
            half4 color = half4(cr.r, cg.g, cb.b, cg.a);

            // ── Scanlines: darken every other line, slow vertical drift ──
            float scan = sin((uv.y * resolution.y + time * 60.0) * 3.14159) * 0.5 + 0.5;
            color.rgb *= (1.0 - intensity * 0.25 * scan);

            // ── Faint noise grain ──
            float g = hash(uv * resolution + time);
            color.rgb += (g - 0.5) * intensity * 0.06;

            // slight desaturation for the washed-out look
            float luma = dot(color.rgb, half3(0.299, 0.587, 0.114));
            color.rgb = mix(color.rgb, half3(luma), intensity * 0.2);

            return color;
        }
    """.trimIndent()

    private val shader by lazy { RuntimeShader(AGSL) }

    /**
     * Build a RenderEffect for the given tier/intensity/time, or null if width/height
     * are not yet known.
     */
    fun build(tier: VhsTier, intensity: Float, time: Float, width: Float, height: Float): RenderEffect? {
        if (width <= 0f || height <= 0f) {
            com.watermelon.common.util.FileLogger.w("VHS", "shader build skipped — no surface size (w=$width h=$height)")
            return null
        }
        return runCatching {
            shader.setFloatUniform("resolution", width, height)
            shader.setFloatUniform("intensity", intensity.coerceIn(0f, 1f))
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("enableJitter", if (tier.hasJitter) 1f else 0f)
            shader.setFloatUniform("enableTracking", if (tier.hasTracking) 1f else 0f)
            RenderEffect.createRuntimeShaderEffect(shader, "inputShader")
        }.onFailure {
            com.watermelon.common.util.FileLogger.e("VHS", "shader build failed", it)
        }.getOrNull()
    }
}
