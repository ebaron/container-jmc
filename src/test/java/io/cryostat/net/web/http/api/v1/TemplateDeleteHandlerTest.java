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

import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.templates.LocalStorageTemplateService;
import io.cryostat.core.templates.Template;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateDeleteHandlerTest {

    TemplateDeleteHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock LocalStorageTemplateService templateService;
    @Mock NotificationFactory notificationFactory;
    @Mock Logger logger;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;

    @BeforeEach
    void setup() {
        lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.message(Mockito.any())).thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.build()).thenReturn(notification);
        this.handler =
                new TemplateDeleteHandler(
                        auth, credentialsManager, templateService, notificationFactory, logger);
    }

    @Test
    void shouldHandleDELETE() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.DELETE));
    }

    @Test
    void sholdHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/templates/:templateName"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(Set.of(ResourceAction.DELETE_TEMPLATE)));
    }

    @Test
    void shouldThrowIfServiceThrows() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("templateName")).thenReturn("FooTemplate");
        Template template =
                new Template("FooTemplate", "unit test template", "UnitTest", TemplateType.CUSTOM);
        Mockito.when(templateService.getTemplates()).thenReturn(List.of(template));
        Mockito.doThrow(IOException.class)
                .when(templateService)
                .deleteTemplate(Mockito.any(Template.class));

        Assertions.assertThrows(IOException.class, () -> handler.handleAuthenticated(ctx));
    }

    @Test
    void shouldThrowIfTemplateDoesNotExist() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("templateName")).thenReturn("FooTemplate");
        Mockito.when(templateService.getTemplates()).thenReturn(List.of());

        HttpException ex =
                Assertions.assertThrows(
                        HttpException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void shouldCallThroughToService() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.pathParam("templateName")).thenReturn("FooTemplate");
        Mockito.when(ctx.response()).thenReturn(resp);

        Template template =
                new Template("FooTemplate", "unit test template", "UnitTest", TemplateType.CUSTOM);
        Mockito.when(templateService.getTemplates()).thenReturn(List.of(template));

        handler.handleAuthenticated(ctx);

        Mockito.verify(templateService).deleteTemplate(template);
        Mockito.verify(ctx).response();
        Mockito.verify(resp).end();

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("TemplateDeleted");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("template", template));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }
}
