// Copyright 2015 The Project Buendia Authors
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not
// use this file except in compliance with the License.  You may obtain a copy
// of the License at: http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distrib-
// uted under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
// OR CONDITIONS OF ANY KIND, either express or implied.  See the License for
// specific language governing permissions and limitations under the License.

package org.projectbuendia.client;

import android.support.test.espresso.IdlingResource;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

//import static android.support.test.espresso.web.deps.guava.base.Preconditions.checkNotNull;


import android.util.Log;

/**
 * A way to ensure that the website to fully loaded before continuing.
 *
 */
public class WebViewIdlingResource extends WebChromeClient implements IdlingResource{
    private static final int FINISHED = 100;
    private final WebView webView;
    private ResourceCallback resourceCallback;

    public WebViewIdlingResource(WebView webView) {
        this.webView = webView;
        final WebView w = this.webView;
        final WebChromeClient that = this;
        //webView.setWebChromeClient(this);

        this.webView.post(new Runnable() {
            @Override
            public void run() {
                w.setWebChromeClient(that);
            }
        });


    }

    @Override
    public String getName() {
        return "WebViewIdlingResource";
    }

    /**
     * Idle if the progress is 100% or the window's title is not blank
     * @return
     */
    @Override
    public boolean isIdleNow() {
        resourceCallback.onTransitionToIdle();
        if (webView == null) return true;
        return webView.getProgress() == FINISHED && webView.getTitle() != null;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback resourceCallback) {
        this.resourceCallback = resourceCallback;
    }

    /**
     * Updates the progress
     *
     * @param view the webview
     * @param newProgress the current progress
     */
    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        Log.i(getName(), ""  + newProgress);
        if (newProgress == FINISHED && view.getTitle() != null && resourceCallback != null) {
            resourceCallback.onTransitionToIdle();
        }
    }

    /**
     * Called when the web view title is received
     *
     * @param view the webview
     * @param title the web title
     */
    @Override
    public void onReceivedTitle(WebView view, String title) {
        Log.i(getName(), title);
        if (webView.getProgress() == FINISHED && resourceCallback != null) {
            resourceCallback.onTransitionToIdle();
        }
    }
}