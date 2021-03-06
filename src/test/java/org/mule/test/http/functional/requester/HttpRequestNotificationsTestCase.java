/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.http.functional.requester;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.notification.AbstractServerNotification.getActionName;
import static org.mule.runtime.api.notification.ConnectorMessageNotification.MESSAGE_REQUEST_BEGIN;
import static org.mule.runtime.api.notification.ConnectorMessageNotification.MESSAGE_REQUEST_END;
import static org.mule.runtime.core.api.context.notification.ServerNotificationManager.createDefaultNotificationManager;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.test.http.functional.TestConnectorMessageNotificationListener.register;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.context.MuleContextBuilder;
import org.mule.test.http.functional.TestConnectorMessageNotificationListener;
import org.mule.test.http.functional.matcher.HttpMessageAttributesMatchers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.qameta.allure.Issue;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("MULE-13774: Add notifications to HTTP request")
@Issue("MULE-13774")
public class HttpRequestNotificationsTestCase extends AbstractHttpRequestTestCase {

  @Override
  protected String getConfigFile() {
    return "http-request-notifications-config.xml";
  }

  @Override
  protected void configureMuleContext(MuleContextBuilder contextBuilder) {
    contextBuilder.setNotificationManager(register(createDefaultNotificationManager()));
    super.configureMuleContext(contextBuilder);
  }

  @Test
  public void receiveNotification() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    TestConnectorMessageNotificationListener listener =
        new TestConnectorMessageNotificationListener(latch, "http://localhost:" + httpPort.getValue() + "/basePath/requestPath");
    muleContext.getNotificationManager().addListener(listener);

    Message response = flowRunner("requestFlow").withPayload(TEST_MESSAGE).run().getMessage();

    latch.await(1000, TimeUnit.MILLISECONDS);

    assertThat(listener.getNotificationActionNames(),
               contains(getActionName(MESSAGE_REQUEST_BEGIN), getActionName(MESSAGE_REQUEST_END)));

    // End event should have appended http.status and http.reason as inbound properties
    Message message = listener.getNotifications(getActionName(MESSAGE_REQUEST_END)).get(0).getEvent().getMessage();
    // For now, check the response, since we no longer have control over the MuleEvent generated, only the Message
    assertThat((HttpResponseAttributes) response.getAttributes().getValue(),
               HttpMessageAttributesMatchers.hasStatusCode(OK.getStatusCode()));
    assertThat((HttpResponseAttributes) response.getAttributes().getValue(),
               HttpMessageAttributesMatchers.hasReasonPhrase(OK.getReasonPhrase()));

    Message requestMessage = listener.getNotifications(getActionName(MESSAGE_REQUEST_BEGIN)).get(0).getEvent().getMessage();
    assertThat(requestMessage, equalTo(message));
  }

}
