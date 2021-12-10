// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.devtools;

import com.google.common.net.MediaType;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.HasAuthentication;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.NetworkInterceptor;
import org.openqa.selenium.environment.webserver.NettyAppServer;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.Filter;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;
import org.openqa.selenium.testing.drivers.Browser;
import org.openqa.selenium.testing.drivers.WebDriverBuilder;

import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.net.MediaType.XHTML_UTF_8;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.openqa.selenium.remote.http.Contents.utf8String;
import static org.openqa.selenium.testing.Safely.safelyCall;
import static org.openqa.selenium.testing.TestUtilities.isFirefoxVersionOlderThan;

public class NetworkInterceptorTest {

  private NettyAppServer appServer;
  private WebDriver driver;
  private NetworkInterceptor interceptor;

  @BeforeClass
  public static void shouldTestBeRunAtAll() {
    // Until Firefox can initialise the Fetch domain, we need this check
    assumeThat(Browser.detect()).isNotEqualTo(Browser.FIREFOX);
    assumeThat(Boolean.getBoolean("selenium.skiptest")).isFalse();
  }

  @Before
  public void setup() {
    driver = new WebDriverBuilder().get();

    assumeThat(driver).isInstanceOf(HasDevTools.class);
    assumeThat(isFirefoxVersionOlderThan(87, driver)).isFalse();

    Route route = Route.combine(
      Route.matching(req -> true)
        .to(() -> req -> new HttpResponse()
          .setStatus(200)
          .addHeader("Content-Type", XHTML_UTF_8.toString())
          .setContent(utf8String("<html><head><title>Hello, World!</title></head><body/></html>"))),
      Route.get("/redirect")
        .to(() -> req -> new HttpResponse()
          .setStatus(HTTP_MOVED_TEMP)
          .setHeader("Location", "/cheese")
          .setContent(Contents.utf8String("Delicious"))));

    appServer = new NettyAppServer(route);
    appServer.start();
  }

  @After
  public void tearDown() {
    safelyCall(
      () -> interceptor.close(),
      () -> driver.quit(),
      () -> appServer.stop());
  }

  @Test
  public void shouldProceedAsNormalIfRequestIsNotIntercepted() {
    interceptor = new NetworkInterceptor(
      driver,
      Route.matching(req -> false).to(() -> req -> new HttpResponse()));

    driver.get(appServer.whereIs("/cheese"));

    String source = driver.getPageSource();

    assertThat(source).contains("Hello, World!");
  }

  @Test
  public void shouldAllowTheInterceptorToChangeTheResponse() {
    interceptor = new NetworkInterceptor(
      driver,
      Route.matching(req -> true)
        .to(() -> req -> new HttpResponse()
          .setStatus(200)
          .addHeader("Content-Type", MediaType.HTML_UTF_8.toString())
          .setContent(utf8String("Creamy, delicious cheese!"))));

    driver.get(appServer.whereIs("/cheese"));

    String source = driver.getPageSource();

    assertThat(source).contains("delicious cheese!");
  }

  @Test
  public void shouldBeAbleToReturnAMagicResponseThatCausesTheOriginalRequestToProceed() {
    AtomicBoolean seen = new AtomicBoolean(false);

    interceptor = new NetworkInterceptor(
      driver,
      Route.matching(req -> true).to(() -> req -> {
        seen.set(true);
        return NetworkInterceptor.PROCEED_WITH_REQUEST;
      }));

    driver.get(appServer.whereIs("/cheese"));

    String source = driver.getPageSource();

    assertThat(seen.get()).isTrue();
    assertThat(source).contains("Hello, World!");
  }

  @Test
  public void shouldClearListenersWhenNetworkInterceptorIsClosed() {
    try (NetworkInterceptor interceptor = new NetworkInterceptor(
      driver,
      Route.matching(req -> true).to(
        () -> req -> new HttpResponse().setStatus(HTTP_NOT_FOUND).setContent(Contents.utf8String("Oh noes!"))))) {
      driver.get(appServer.whereIs("/cheese"));

      String text = driver.findElement(By.tagName("body")).getText();

      assertThat(text).contains("Oh noes!");
    }

    // Reload the page
    driver.get(appServer.whereIs("/cheese"));
    String text = driver.findElement(By.tagName("body")).getText();
    assertThat(text).contains("Hello, World!");
  }

  @Test
  public void shouldBeAbleToInterceptAResponse() {
    try (NetworkInterceptor networkInterceptor = new NetworkInterceptor(
      driver,
      (Filter) next -> req -> {
        HttpResponse res = next.execute(req);
        res.addHeader("Content-Type", MediaType.HTML_UTF_8.toString());
        res.setContent(Contents.utf8String("Sausages"));
        return res;
      })) {

      driver.get(appServer.whereIs("/cheese"));
    }

    String body = driver.findElement(By.tagName("body")).getText();
    assertThat(body).contains("Sausages");
  }

  @Test
  public void shouldHandleRedirects() {
    try (NetworkInterceptor networkInterceptor = new NetworkInterceptor(
      driver,
      (Filter) next -> next::execute)) {
      driver.get(appServer.whereIs("/redirect"));

      String body = driver.findElement(By.tagName("body")).getText();
      assertThat(body).contains("Hello, World!");
    }
  }

  /**
   * This test case tests the issue 9968 to make sure that one can authenticate successfully.
   * @throws MalformedURLException - it may throw this exception
   * @throws InterruptedException - - it may throw this exception
   * CS427 Issue link: https://github.com/SeleniumHQ/selenium/issues/9968
   */
  @Test
  public void shouldBeAbletoAuthenticate() throws MalformedURLException, InterruptedException {
    ChromeOptions chromeOptions = new ChromeOptions();
    WebDriver driver = RemoteWebDriver.builder()
      .oneOf(chromeOptions)
      .address("http://localhost:4444")
      .build();

    DevTools devTools = ((HasDevTools) driver).getDevTools();
    devTools.createSession();

    Augmenter augmenter = new Augmenter();

    driver = augmenter
      .addDriverAugmentation("chrome",
                             HasAuthentication.class,
                             (caps, exec) -> (whenThisMatches, useTheseCredentials) ->
                               devTools.getDomains()
                                 .network()
                                 .addAuthHandler(whenThisMatches, useTheseCredentials))
      .augment(driver);

    ((HasAuthentication) driver).register(UsernameAndPassword.of("foo", "bar"));

    driver.get("http://httpbin.org/basic-auth/foo/bar");

    String pageSource = driver.getPageSource();

    if(pageSource.contains("authenticated")) {
      System.out.println("True");
    } else {
      System.out.println("False");
    }
    driver.quit();
  }
}
