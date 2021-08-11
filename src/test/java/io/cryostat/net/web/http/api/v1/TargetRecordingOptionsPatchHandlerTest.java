/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net.web.http.api.v1;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.core.RecordingOptionsCustomizer;
import io.cryostat.core.RecordingOptionsCustomizer.OptionKey;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetRecordingOptionsPatchHandlerTest {

    TargetRecordingOptionsPatchHandler handler;
    @Mock AuthManager auth;
    @Mock RecordingOptionsCustomizer customizer;
    @Mock TargetConnectionManager connectionManager;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock RecordingOptionsBuilder builder;
    @Mock IConstrainedMap<String> recordingOptions;
    @Mock JFRConnection jfrConnection;
    @Mock Gson gson;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingOptionsPatchHandler(
                        auth, customizer, connectionManager, recordingOptionsBuilderFactory, gson);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.PATCH));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/targets/:targetId/recordingOptions"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(Set.of(ResourceAction.READ_TARGET, ResourceAction.UPDATE_TARGET)));
    }

    @Test
    void shouldSetRecordingOptions() throws Exception {
        Map<String, String> originalValues =
                Map.of("toDisk", "true", "maxAge", "50", "maxSize", "32");
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(recordingOptions);
        Mockito.when(recordingOptions.get("toDisk")).thenReturn(originalValues.get("toDisk"));
        Mockito.when(recordingOptions.get("maxAge")).thenReturn(originalValues.get("maxAge"));
        Mockito.when(recordingOptions.get("maxSize")).thenReturn(originalValues.get("maxSize"));

        MultiMap requestAttrs = MultiMap.caseInsensitiveMultiMap();
        requestAttrs.addAll(originalValues);

        Mockito.when(
                        connectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Map answer(InvocationOnMock args) throws Throwable {
                                TargetConnectionManager.ConnectedTask ct =
                                        (TargetConnectionManager.ConnectedTask)
                                                args.getArguments()[1];
                                return (Map) ct.execute(jfrConnection);
                            }
                        });

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("foo:9091");
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(requestAttrs);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        IFlightRecorderService service = Mockito.mock(IFlightRecorderService.class);
        Mockito.when(jfrConnection.getService()).thenReturn(service);

        handler.handleAuthenticated(ctx);

        for (var entry : requestAttrs.entries()) {
            var key = OptionKey.fromOptionName(entry.getKey());
            Mockito.verify(customizer).set(key.get(), entry.getValue());
        }
    }

    @Test
    void shouldUnsetRecordingOptions() throws Exception {
        Map<String, String> originalValues =
                Map.of("toDisk", "true", "maxAge", "50", "maxSize", "32");
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(recordingOptions);
        Mockito.when(recordingOptions.get("toDisk")).thenReturn(originalValues.get("toDisk"));
        Mockito.when(recordingOptions.get("maxAge")).thenReturn(originalValues.get("maxAge"));
        Mockito.when(recordingOptions.get("maxSize")).thenReturn(originalValues.get("maxSize"));

        MultiMap requestAttrs = MultiMap.caseInsensitiveMultiMap();
        requestAttrs.addAll(Map.of("toDisk", "unset", "maxAge", "unset", "maxSize", "unset"));

        Mockito.when(
                        connectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Map answer(InvocationOnMock args) throws Throwable {
                                TargetConnectionManager.ConnectedTask ct =
                                        (TargetConnectionManager.ConnectedTask)
                                                args.getArguments()[1];
                                return (Map) ct.execute(jfrConnection);
                            }
                        });

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("foo:9091");
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(requestAttrs);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        IFlightRecorderService service = Mockito.mock(IFlightRecorderService.class);
        Mockito.when(jfrConnection.getService()).thenReturn(service);

        handler.handleAuthenticated(ctx);

        for (var entry : requestAttrs.entries()) {
            var key = OptionKey.fromOptionName(entry.getKey());
            Mockito.verify(customizer).unset(key.get());
        }
    }

    @ParameterizedTest
    @MethodSource("getRequestMaps")
    void shouldThrowInvalidOptionException(Map<String, String> values) throws Exception {
        MultiMap requestAttrs = MultiMap.caseInsensitiveMultiMap();
        requestAttrs.addAll(values);

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.formAttributes()).thenReturn(requestAttrs);
        HttpStatusException ex =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    private static Stream<Map<String, String>> getRequestMaps() {
        return Stream.of(
                Map.of("toDisk", ""),
                Map.of("toDisk", "5"),
                Map.of("toDisk", "T"),
                Map.of("toDisk", "false1"),
                Map.of("maxAge", ""),
                Map.of("maxAge", "true"),
                Map.of("maxAge", "1e3"),
                Map.of("maxAge", "5s"),
                Map.of("maxSize", ""),
                Map.of("maxSize", "0.5"),
                Map.of("maxSize", "s1"));
    }
}
