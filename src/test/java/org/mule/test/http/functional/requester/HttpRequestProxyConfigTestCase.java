

/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.http.functional.requester;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.allOf;
import static org.mule.functional.junit4.matchers.ThrowableMessageMatcher.hasMessage;
import static org.mule.test.http.AllureConstants.HttpFeature.HTTP_EXTENSION;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.extension.http.api.error.HttpRequestFailedException;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.test.runner.RunnerDelegateTo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collection;

import io.qameta.allure.Feature;

@Feature(HTTP_EXTENSION)
@RunnerDelegateTo(Parameterized.class)
public class HttpRequestProxyConfigTestCase extends MuleArtifactFunctionalTestCase {

  private static final Logger LOGGER = getLogger(HttpRequestProxyConfigTestCase.class);

  @Rule
  public DynamicPort proxyPort = new DynamicPort("proxyPort");

  @Rule
  public DynamicPort httpPort = new DynamicPort("httpPort");

  private Thread mockProxyAcceptor;
  private Latch latch = new Latch();
  private Latch proxyReadyLatch = new Latch();

  @Parameter()
  public String flowName;

  @Parameter(1)
  public ProxyType proxyType;

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {{"RefAnonymousProxy", ProxyType.ANONYMOUS}, {"InnerAnonymousProxy", ProxyType.ANONYMOUS},
        {"RefUserPassProxy", ProxyType.USER_PASS}, {"InnerUserPassProxy", ProxyType.USER_PASS}, {"RefNtlmProxy", ProxyType.NTLM},
        {"InnerNtlmProxy", ProxyType.NTLM}});
  }

  @Override
  protected String getConfigFile() {
    return "http-request-proxy-config.xml";
  }

  @Before
  public void startMockProxy() throws IOException, InterruptedException {
    mockProxyAcceptor = new MockProxy();
    mockProxyAcceptor.start();

    // Give time to the proxy thread to start up completely
    proxyReadyLatch.await();
  }

  @After
  public void stopMockProxy() throws Exception {
    mockProxyAcceptor.join(LOCK_TIMEOUT);
  }

  @Test
  public void testProxy() throws Exception {
    ensureRequestGoesThroughProxy(flowName);
  }

  private void ensureRequestGoesThroughProxy(String flowName) throws Exception {
    // Request should go through the proxy.
    flowRunner(flowName).withPayload(TEST_MESSAGE).runExpectingException(allOf(instanceOf(HttpRequestFailedException.class),
                                                                               hasMessage(containsString("Remotely closed"))));
    latch.await(1, SECONDS);
  }

  private enum ProxyType {
    ANONYMOUS, USER_PASS, NTLM
  }


  private class MockProxy extends Thread {

    @Override
    public void run() {
      ServerSocket serverSocket = null;
      try {
        ServerSocketChannel ssc = ServerSocketChannel.open();

        serverSocket = ssc.socket();
        serverSocket.bind(new InetSocketAddress(Integer.parseInt(proxyPort.getValue())));
        ssc.configureBlocking(false);

        proxyReadyLatch.countDown();
        SocketChannel sc = null;
        while (sc == null) {
          sc = ssc.accept();
          Thread.yield();
        }

        sc.close();

        latch.release();
      } catch (IOException e) {
        /* Ignore */
        LOGGER.error("Exception while configuring MockProxy", e);
      } finally {
        if (serverSocket != null) {
          try {
            serverSocket.close();
          } catch (IOException e) {
            /* Ignore */
            LOGGER.error("Exception while closing MockProxy", e);
          }
        }
      }
    }
  }
}
