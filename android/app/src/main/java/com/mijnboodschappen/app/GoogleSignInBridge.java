package com.mijnboodschappen.app;

import android.app.Activity;
import android.os.CancellationSignal;
import android.webkit.WebView;

import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

/**
 * Inloggen met Google in de Android-app.
 *
 * Google staat inloggen in een webview niet toe. Daarom vraagt de app via Android
 * (Credential Manager) om een Google-account. Het resultaat (een ID-token) gaat naar
 * de webpagina, die daarmee bij Firebase inlogt.
 *
 * Antwoord naar de app: window.__onGoogleSignIn(json)
 *   {ok:true, idToken, name, givenName, email}
 *   {ok:false, reason:"cancelled"|"no_account"|"error", message}
 */
class GoogleSignInBridge {
    private final Activity activity;
    private final WebView webView;
    private boolean busy = false;

    GoogleSignInBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    void signIn(final String webClientId) {
        activity.runOnUiThread(() -> {
            if (busy) return;
            if (webClientId == null || webClientId.trim().isEmpty()) {
                sendError("error", "Geen client-ID");
                return;
            }
            busy = true;
            try {
                CredentialManager manager = CredentialManager.create(activity);
                GetSignInWithGoogleOption option =
                        new GetSignInWithGoogleOption.Builder(webClientId.trim()).build();
                GetCredentialRequest request = new GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build();
                manager.getCredentialAsync(
                        activity,
                        request,
                        new CancellationSignal(),
                        ContextCompat.getMainExecutor(activity),
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override
                            public void onResult(GetCredentialResponse response) {
                                busy = false;
                                handleResult(response);
                            }

                            @Override
                            public void onError(GetCredentialException e) {
                                busy = false;
                                if (e instanceof GetCredentialCancellationException) {
                                    sendError("cancelled", "");
                                } else if (e instanceof NoCredentialException) {
                                    sendError("no_account", e.getMessage());
                                } else {
                                    sendError("error", e.getType() + ": " + e.getMessage());
                                }
                            }
                        });
            } catch (Throwable t) {
                busy = false;
                sendError("error", String.valueOf(t.getMessage()));
            }
        });
    }

    private void handleResult(GetCredentialResponse response) {
        try {
            Credential credential = response.getCredential();
            if (credential instanceof CustomCredential
                    && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
                JSONObject out = new JSONObject();
                out.put("ok", true);
                out.put("idToken", google.getIdToken());
                out.put("name", google.getDisplayName() == null ? "" : google.getDisplayName());
                out.put("givenName", google.getGivenName() == null ? "" : google.getGivenName());
                out.put("email", google.getId());
                callJs(out.toString());
            } else {
                sendError("error", "Onbekend soort inloggegevens");
            }
        } catch (Throwable t) {
            sendError("error", String.valueOf(t.getMessage()));
        }
    }

    private void sendError(String reason, String message) {
        try {
            JSONObject out = new JSONObject();
            out.put("ok", false);
            out.put("reason", reason);
            out.put("message", message == null ? "" : message);
            callJs(out.toString());
        } catch (Exception ignored) {
        }
    }

    private void callJs(final String json) {
        final String js = "window.__onGoogleSignIn&&window.__onGoogleSignIn(" + JSONObject.quote(json) + ")";
        activity.runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }
}
