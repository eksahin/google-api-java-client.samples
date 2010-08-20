/*
 * Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.sample.latitude;

import com.google.api.client.auth.oauth.OAuthAuthorizeTemporaryTokenUrl;
import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.auth.oauth.OAuthParameters;
import com.google.api.client.googleapis.auth.oauth.GoogleOAuthGetAccessToken;
import com.google.api.client.googleapis.auth.oauth.GoogleOAuthGetTemporaryToken;
import com.google.api.client.http.HttpTransport;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.net.URI;

/**
 * Implements OAuth authentication.
 *
 * @author Yaniv Inbar
 */
public class Auth {

  private static final String APP_NAME = "Google Latitude API Java Client";

  private static OAuthHmacSigner signer;

  private static OAuthCredentialsResponse credentials;

  /**
   * For details regarding authentication and authorization for GoogleLatitude,see<ahref="http://code.google.com/apis/latitude/v1/using_rest.html#auth">Authentication
   * and authorization</a>.
   */
  static void authorize(HttpTransport transport) throws Exception {
    // callback server
    LoginCallbackServer callbackServer = null;
    String verifier = null;
    String tempToken = null;
    try {
      callbackServer = new LoginCallbackServer();
      callbackServer.start();
      // temporary token
      GoogleOAuthGetTemporaryToken temporaryToken =
          new GoogleOAuthGetTemporaryToken();
      signer = new OAuthHmacSigner();
      signer.clientSharedSecret = ClientCredentials.ENTER_CLIENT_SHARED_SECRET;
      temporaryToken.signer = signer;
      temporaryToken.consumerKey = ClientCredentials.ENTER_DOMAIN;
      temporaryToken.scope = "https://www.googleapis.com/auth/latitude";
      temporaryToken.displayName = APP_NAME;
      temporaryToken.callback = callbackServer.getCallbackUrl();
      OAuthCredentialsResponse tempCredentials = temporaryToken.execute();
      signer.tokenSharedSecret = tempCredentials.tokenSecret;
      // authorization URL -- see
      // http://code.google.com/apis/latitude/v1/using_rest.html#auth
      OAuthAuthorizeTemporaryTokenUrl authorizeUrl =
          new OAuthAuthorizeTemporaryTokenUrl(
              "https://www.google.com/latitude/apps/OAuthAuthorizeToken");
      // (required) The domain used to identify your application.
      authorizeUrl.put("domain", ClientCredentials.ENTER_DOMAIN);
      // (optional) The range of locations you want to access. Can be either
      // current or all. If this parameter is omitted, current is assumed.
      authorizeUrl.put("location", "all");
      // (optional) The finest granularity of locations you want to access. Can
      // be either city or best. If this parameter is omitted, city is assumed.
      authorizeUrl.put("granularity", "best");
      authorizeUrl.temporaryToken = tempToken = tempCredentials.token;
      String authorizationUrl = authorizeUrl.build();
      // launch in browser
      boolean browsed = false;
      if (Desktop.isDesktopSupported()) {
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Action.BROWSE)) {
          desktop.browse(URI.create(authorizationUrl));
          browsed = true;
        }
      }
      if (!browsed) {
        String browser = "google-chrome";
        Runtime.getRuntime().exec(new String[] {browser, authorizationUrl});
      }
      verifier = callbackServer.waitForVerifier(tempToken);
    } finally {
      if (callbackServer != null) {
        callbackServer.stop();
      }
    }
    GoogleOAuthGetAccessToken accessToken = new GoogleOAuthGetAccessToken();
    accessToken.temporaryToken = tempToken;
    accessToken.signer = signer;
    accessToken.consumerKey = ClientCredentials.ENTER_DOMAIN;
    accessToken.verifier = verifier;
    credentials = accessToken.execute();
    signer.tokenSharedSecret = credentials.tokenSecret;
    createOAuthParameters().signRequestsUsingAuthorizationHeader(transport);
  }

  static void revoke() {
    if (credentials != null) {
      try {
        GoogleOAuthGetAccessToken.revokeAccessToken(createOAuthParameters());
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
    }
  }

  private static OAuthParameters createOAuthParameters() {
    OAuthParameters authorizer = new OAuthParameters();
    authorizer.consumerKey = ClientCredentials.ENTER_DOMAIN;
    authorizer.signer = signer;
    authorizer.token = credentials.token;
    return authorizer;
  }
}