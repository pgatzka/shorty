package io.github.pgatzka.shorty.render.service;

/**
 * Frame layout of a video: intro, sorting, sweep and outro phases.
 *
 * @param fps frames per second
 * @param stepsPerFrame sort steps shown per frame (may be fractional)
 * @param stepCount total sort steps
 * @param introFrames frames before sorting
 * @param sortFrames frames while sorting
 * @param sweepFrames frames of the final sweep
 * @param outroFrames frames of the call-to-action card
 */
public record Timeline(
        int fps,
        double stepsPerFrame,
        int stepCount,
        int introFrames,
        int sortFrames,
        int sweepFrames,
        int outroFrames) {

    public static Timeline of(RenderProperties properties, int stepCount) {
        int fps = properties.fps();
        double stepsPerFrame = (double) properties.stepsPerSecond() / fps;
        int sortFrames = Math.max(1, (int) Math.ceil(stepCount / stepsPerFrame));
        return new Timeline(
                fps,
                stepsPerFrame,
                stepCount,
                frames(properties.introSeconds(), fps),
                sortFrames,
                Math.max(1, frames(properties.sweepSeconds(), fps)),
                frames(properties.outroSeconds(), fps));
    }

    private static int frames(double seconds, int fps) {
        return (int) Math.round(seconds * fps);
    }

    public int totalFrames() {
        return introFrames + sortFrames + sweepFrames + outroFrames;
    }

    public double durationSeconds() {
        return (double) totalFrames() / fps;
    }

    public double introSeconds() {
        return (double) introFrames / fps;
    }

    public double sortSeconds() {
        return (double) sortFrames / fps;
    }

    public double sweepSeconds() {
        return (double) sweepFrames / fps;
    }

    /** First step index shown in the given sort frame. */
    public int firstStep(int sortFrame) {
        return Math.min(stepCount, (int) Math.floor(sortFrame * stepsPerFrame));
    }

    /** Step index after the last one shown in the given sort frame. */
    public int endStep(int sortFrame) {
        if (sortFrame >= sortFrames - 1) {
            return stepCount;
        }
        return firstStep(sortFrame + 1);
    }
}
