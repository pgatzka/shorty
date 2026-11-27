package io.github.pgatzka.shorty.sorting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.sorting.domain.SortStep;
import io.github.pgatzka.shorty.sorting.domain.SortTrace;
import io.github.pgatzka.shorty.sorting.domain.StepType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SortingServiceTest {

    private final SortingService service = SortingFixtures.sortingService();

    @ParameterizedTest
    @EnumSource(SortAlgorithm.class)
    void sortsShuffledInput(SortAlgorithm algorithm) {
        for (int size : new int[] {1, 2, 3, 10, 57}) {
            List<Integer> input = service.shuffledInput(size, size * 31L);
            SortTrace trace = service.trace(algorithm, input);

            assertThat(trace.result()).isSorted().containsExactlyInAnyOrderElementsOf(input);
            assertThat(trace.initial()).isEqualTo(input);
            assertThat(trace.size()).isEqualTo(size);
            assertThat(replay(trace)).isEqualTo(trace.result());
        }
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm.class)
    void handlesSortedReversedAndDuplicateInput(SortAlgorithm algorithm) {
        List<Integer> sorted = IntStream.rangeClosed(1, 20).boxed().toList();
        List<Integer> reversed = sorted.reversed();
        List<Integer> duplicates = List.of(3, 1, 3, 2, 1, 3, 2);

        assertThat(service.trace(algorithm, sorted).result()).isEqualTo(sorted);
        assertThat(service.trace(algorithm, reversed).result()).isEqualTo(sorted);
        assertThat(service.trace(algorithm, duplicates).result()).containsExactly(1, 1, 2, 2, 3, 3, 3);
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm.class)
    void countersOnlyGrow(SortAlgorithm algorithm) {
        SortTrace trace = service.trace(algorithm, service.shuffledInput(40, 7));
        long comparisons = 0;
        long swaps = 0;
        long accesses = 0;
        for (SortStep step : trace.steps()) {
            assertThat(step.comparisons()).isGreaterThanOrEqualTo(comparisons);
            assertThat(step.swaps()).isGreaterThanOrEqualTo(swaps);
            assertThat(step.accesses()).isGreaterThanOrEqualTo(accesses);
            comparisons = step.comparisons();
            swaps = step.swaps();
            accesses = step.accesses();
        }
        assertThat(trace.comparisons()).isEqualTo(comparisons).isPositive();
        assertThat(trace.swaps()).isEqualTo(swaps);
        assertThat(trace.accesses()).isEqualTo(accesses).isPositive();
        assertThat(trace.stepCount()).isEqualTo(trace.steps().size());
    }

    @Test
    void bubbleSortOfSortedInputOnlyCompares() {
        SortTrace trace = service.trace(SortAlgorithm.BUBBLE, List.of(1, 2, 3, 4));
        assertThat(trace.steps())
                .extracting(SortStep::type)
                .containsOnly(StepType.COMPARE)
                .hasSize(3);
        assertThat(trace.swaps()).isZero();
    }

    @Test
    void mergeSortWrites() {
        SortTrace trace = service.trace(SortAlgorithm.MERGE, List.of(4, 3, 2, 1));
        assertThat(trace.steps()).extracting(SortStep::type).contains(StepType.WRITE);
        assertThat(trace.swaps()).isZero();
    }

    @Test
    void shuffledInputIsReproducible() {
        assertThat(service.shuffledInput(30, 42)).isEqualTo(service.shuffledInput(30, 42));
        assertThat(service.shuffledInput(30, 42)).isNotEqualTo(service.shuffledInput(30, 43));
        assertThat(service.shuffledInput(5, 1)).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertThatThrownBy(() -> service.shuffledInput(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingSorterIsReported() {
        SortingService onlyBubble = new SortingService(List.of(new BubbleSorter()));
        assertThatThrownBy(() -> onlyBubble.trace(SortAlgorithm.QUICK, List.of(2, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUICK");
    }

    @Test
    void emptyTraceHasZeroCounters() {
        SortTrace trace = new SortTrace(SortAlgorithm.BUBBLE, List.of(), List.of(), List.of());
        assertThat(trace.comparisons()).isZero();
        assertThat(trace.swaps()).isZero();
        assertThat(trace.accesses()).isZero();
        assertThat(trace.maxValue()).isZero();
    }

    @Test
    void algorithmsRotate() {
        assertThat(SortAlgorithm.BUBBLE.next()).isEqualTo(SortAlgorithm.QUICK);
        assertThat(SortAlgorithm.SELECTION.next()).isEqualTo(SortAlgorithm.BUBBLE);
        assertThat(SortAlgorithm.MERGE.displayName()).isEqualTo("Merge Sort");
        assertThat(SortAlgorithm.MERGE.bigO()).isEqualTo("O(n log n)");
    }

    private static List<Integer> replay(SortTrace trace) {
        List<Integer> values = new ArrayList<>(trace.initial());
        for (SortStep step : trace.steps()) {
            switch (step.type()) {
                case SWAP -> {
                    int tmp = values.get(step.first());
                    values.set(step.first(), values.get(step.second()));
                    values.set(step.second(), tmp);
                }
                case WRITE -> values.set(step.first(), step.second());
                case COMPARE -> {
                    // no change
                }
            }
        }
        return values;
    }
}
