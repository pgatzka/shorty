package io.github.pgatzka.shorty.publishing.service;

import java.util.List;

/** Publishing properties used across tests. */
final class PropertiesFixture {

    private PropertiesFixture() {}

    static PublishingProperties properties() {
        return new PublishingProperties(
                3,
                List.of("{algorithm} sorting {bars} bars #shorts", "{algorithm} in {seconds}s, {bigO}"),
                List.of("{comparisons} comparisons, {swaps} swaps, {accesses} accesses. Follow {handle}."),
                List.of("sorting", "shorts"));
    }
}
