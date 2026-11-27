package io.github.pgatzka.shorty.youtube.service;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A fake Google HTTP endpoint: routes requests by method/URL to canned responses and records them. */
final class FakeGoogle extends MockHttpTransport {

    record Call(String method, String url, String body) {}

    private record Route(Predicate<Call> matches, Supplier<MockLowLevelHttpResponse> response) {}

    private final List<Route> routes = new ArrayList<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    FakeGoogle on(String method, String urlPart, int status, String json) {
        return on(method, urlPart, () -> json(status, json));
    }

    /** Responses are created per request because their body stream can only be read once. */
    FakeGoogle on(String method, String urlPart, Supplier<MockLowLevelHttpResponse> response) {
        routes.add(new Route(call -> call.method().equals(method) && call.url().contains(urlPart), response));
        return this;
    }

    static MockLowLevelHttpResponse json(int status, String json) {
        return new MockLowLevelHttpResponse()
                .setStatusCode(status)
                .setContentType("application/json; charset=UTF-8")
                .setContent(json);
    }

    List<Call> calls() {
        return calls;
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest(url) {
            @Override
            public LowLevelHttpResponse execute() throws IOException {
                Call call = new Call(method, url, getContentAsString());
                calls.add(call);
                return routes.stream()
                        .filter(route -> route.matches().test(call))
                        .findFirst()
                        .map(route -> route.response().get())
                        .orElseThrow(() -> new IOException("Unexpected request " + method + " " + url));
            }
        };
    }
}
