package io.github.pgatzka.shorty.render.domain;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import java.util.Objects;

/**
 * What to render.
 *
 * @param algorithm sorting algorithm to visualize
 * @param style visual style
 * @param seed seed of the shuffled input, so a render is reproducible
 * @param channelHandle channel handle for the follow call-to-action; may be blank
 */
public record RenderRequest(SortAlgorithm algorithm, VisualStyle style, long seed, String channelHandle) {

    public RenderRequest {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(style, "style");
        channelHandle = channelHandle == null ? "" : channelHandle.strip();
    }
}
