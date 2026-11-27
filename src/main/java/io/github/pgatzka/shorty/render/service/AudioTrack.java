package io.github.pgatzka.shorty.render.service;

/** A mono audio buffer that beeps are mixed into. */
final class AudioTrack {

    private static final double ATTACK_SECONDS = 0.003;

    private final int sampleRate;
    private final float[] samples;

    AudioTrack(int sampleRate, double durationSeconds) {
        this.sampleRate = sampleRate;
        this.samples = new float[(int) Math.ceil(durationSeconds * sampleRate)];
    }

    int length() {
        return samples.length;
    }

    /**
     * Mixes a short triangle-wave beep into the track. Parts beyond the end of the track are cut.
     */
    void addTone(double startSeconds, double frequency, double durationSeconds, double amplitude) {
        int start = (int) Math.round(startSeconds * sampleRate);
        int length = (int) Math.round(durationSeconds * sampleRate);
        int attack = Math.max(1, (int) Math.round(ATTACK_SECONDS * sampleRate));
        for (int n = 0; n < length; n++) {
            int index = start + n;
            if (index < 0 || index >= samples.length) {
                continue;
            }
            double phase = n * frequency / sampleRate % 1.0;
            double triangle = 4.0 * Math.abs(phase - 0.5) - 1.0;
            double envelope =
                    n < attack ? (double) n / attack : 1.0 - (double) (n - attack) / Math.max(1, length - attack);
            samples[index] += (float) (triangle * envelope * amplitude);
        }
    }

    /** 16-bit PCM with hard clipping. */
    short[] toPcm16() {
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            double clipped = Math.clamp(samples[i], -1.0, 1.0);
            pcm[i] = (short) Math.round(clipped * Short.MAX_VALUE);
        }
        return pcm;
    }
}
