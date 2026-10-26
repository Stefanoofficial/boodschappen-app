package com.mijnboodschappen.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Betalen voor Pro via Google Play.
 *
 * De app (JavaScript) vraagt via window.AndroidApp om producten, aankopen en het betaalscherm.
 * Antwoorden gaan terug naar:
 *   window.__onBillingProducts(json)   prijzen en aanbiedingen (maand, jaar, proefperiode)
 *   window.__onBillingPurchases(json)  lopende abonnementen (met aankoopcode voor de servercontrole)
 *   window.__onBillingError(code, tekst)
 *
 * Belangrijk: de aankoop wordt NIET hier bevestigd. Dat doet de server (Cloudflare-worker)
 * pas nadat hij de aankoop bij Google heeft gecontroleerd. Zo kan niemand Pro nadoen.
 */
class BillingBridge {
    static final String PRODUCT_ID = "mijnboodschappen_pro";

    private final Activity activity;
    private final WebView webView;
    private BillingClient client;
    private ProductDetails details;
    private boolean connecting = false;
    private final List<Runnable> waiting = new ArrayList<>();

    BillingBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    private void ensureClient() {
        if (client != null) return;
        client = BillingClient.newBuilder(activity)
                .setListener((result, purchases) -> {
                    int code = result.getResponseCode();
                    if (code == BillingClient.BillingResponseCode.OK) {
                        sendPurchases(purchases, "buy");
                    } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        queryPurchases();
                    } else {
                        sendError(code, result.getDebugMessage());
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    /** Voert iets uit zodra de verbinding met Google Play klaar is. */
    private void whenReady(Runnable r) {
        ensureClient();
        if (client.isReady()) { r.run(); return; }
        waiting.add(r);
        if (connecting) return;
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                connecting = false;
                List<Runnable> todo = new ArrayList<>(waiting);
                waiting.clear();
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (Runnable t : todo) t.run();
                } else {
                    sendError(result.getResponseCode(), result.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connecting = false;
            }
        });
    }

    /** Prijzen en aanbiedingen ophalen. */
    void queryProducts() {
        activity.runOnUiThread(() -> whenReady(() -> {
            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build();
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(Collections.singletonList(product))
                    .build();
            client.queryProductDetailsAsync(params, (result, res) -> {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    sendError(result.getResponseCode(), result.getDebugMessage());
                    return;
                }
                List<ProductDetails> list = res == null ? null : res.getProductDetailsList();
                details = (list == null || list.isEmpty()) ? null : list.get(0);
                sendProducts();
            });
        }));
    }

    /** Lopende abonnementen ophalen (ook om een aankoop te herstellen op een nieuwe telefoon). */
    void queryPurchases() {
        activity.runOnUiThread(() -> whenReady(() -> {
            QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build();
            client.queryPurchasesAsync(params, (result, purchases) -> {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    sendPurchases(purchases, "restore");
                } else {
                    sendError(result.getResponseCode(), result.getDebugMessage());
                }
            });
        }));
    }

    /**
     * Het betaalscherm van Google openen.
     * @param basePlanId  "maand" of "jaar" (zoals ingesteld in Play Console)
     * @param accountId   versleutelde gebruikers-ID; de server controleert dat de aankoop bij dit account hoort
     */
    void buy(final String basePlanId, final String accountId) {
        activity.runOnUiThread(() -> whenReady(() -> {
            if (details == null) {
                sendError(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE, "Pro is nog niet beschikbaar in Google Play.");
                return;
            }
            ProductDetails.SubscriptionOfferDetails chosen = pickOffer(basePlanId);
            if (chosen == null) {
                sendError(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE, "Dit abonnement is niet gevonden.");
                return;
            }
            BillingFlowParams.ProductDetailsParams pdp = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(chosen.getOfferToken())
                    .build();
            BillingFlowParams.Builder b = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(pdp));
            if (accountId != null && !accountId.isEmpty()) b.setObfuscatedAccountId(accountId);
            BillingResult r = client.launchBillingFlow(activity, b.build());
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                sendError(r.getResponseCode(), r.getDebugMessage());
            }
        }));
    }

    /** Kiest bij een abonnement de aanbieding met gratis proefperiode als iemand daar nog recht op heeft. */
    private ProductDetails.SubscriptionOfferDetails pickOffer(String basePlanId) {
        List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
        if (offers == null) return null;
        ProductDetails.SubscriptionOfferDetails base = null, trial = null;
        for (ProductDetails.SubscriptionOfferDetails o : offers) {
            if (basePlanId != null && !basePlanId.equals(o.getBasePlanId())) continue;
            if (o.getOfferId() == null) base = o;          // gewone prijs
            else if (hasFreePhase(o) && trial == null) trial = o; // Google toont alleen aanbiedingen waar je recht op hebt
        }
        return trial != null ? trial : base;
    }

    private static boolean hasFreePhase(ProductDetails.SubscriptionOfferDetails o) {
        try {
            for (ProductDetails.PricingPhase p : o.getPricingPhases().getPricingPhaseList()) {
                if (p.getPriceAmountMicros() == 0) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    /** Opent het abonnementenoverzicht van Google Play (opzeggen, betaalmethode wijzigen). */
    void manage() {
        activity.runOnUiThread(() -> {
            try {
                String url = "https://play.google.com/store/account/subscriptions?sku=" + PRODUCT_ID
                        + "&package=" + activity.getPackageName();
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                sendError(-100, "Google Play kon niet worden geopend.");
            }
        });
    }

    void destroy() {
        try { if (client != null) client.endConnection(); } catch (Exception ignored) { }
        client = null;
    }

    // ---------- Antwoorden naar de app ----------

    private void sendProducts() {
        try {
            JSONArray plans = new JSONArray();
            if (details != null && details.getSubscriptionOfferDetails() != null) {
                for (ProductDetails.SubscriptionOfferDetails o : details.getSubscriptionOfferDetails()) {
                    JSONObject j = new JSONObject();
                    j.put("basePlanId", o.getBasePlanId());
                    j.put("offerId", o.getOfferId() == null ? "" : o.getOfferId());
                    JSONArray phases = new JSONArray();
                    for (ProductDetails.PricingPhase p : o.getPricingPhases().getPricingPhaseList()) {
                        JSONObject pj = new JSONObject();
                        pj.put("price", p.getFormattedPrice());
                        pj.put("micros", p.getPriceAmountMicros());
                        pj.put("currency", p.getPriceCurrencyCode());
                        pj.put("period", p.getBillingPeriod());
                        pj.put("cycles", p.getBillingCycleCount());
                        phases.put(pj);
                    }
                    j.put("phases", phases);
                    plans.put(j);
                }
            }
            JSONObject out = new JSONObject();
            out.put("available", details != null);
            out.put("plans", plans);
            callJs("__onBillingProducts", out.toString());
        } catch (Exception e) {
            sendError(-101, "Prijzen konden niet worden gelezen.");
        }
    }

    private void sendPurchases(List<Purchase> purchases, String reason) {
        try {
            JSONArray arr = new JSONArray();
            if (purchases != null) {
                for (Purchase p : purchases) {
                    JSONObject j = new JSONObject();
                    j.put("products", new JSONArray(p.getProducts()));
                    j.put("token", p.getPurchaseToken());
                    j.put("state", p.getPurchaseState() == Purchase.PurchaseState.PURCHASED ? "purchased"
                            : (p.getPurchaseState() == Purchase.PurchaseState.PENDING ? "pending" : "unknown"));
                    j.put("acknowledged", p.isAcknowledged());
                    j.put("autoRenewing", p.isAutoRenewing());
                    arr.put(j);
                }
            }
            JSONObject out = new JSONObject();
            out.put("reason", reason);
            out.put("purchases", arr);
            callJs("__onBillingPurchases", out.toString());
        } catch (Exception e) {
            sendError(-102, "Aankopen konden niet worden gelezen.");
        }
    }

    private void sendError(int code, String message) {
        try {
            JSONObject out = new JSONObject();
            out.put("code", code);
            out.put("message", message == null ? "" : message);
            callJs("__onBillingError", out.toString());
        } catch (Exception ignored) { }
    }

    private void callJs(final String fn, final String json) {
        final String js = "window." + fn + "&&window." + fn + "(" + JSONObject.quote(json) + ")";
        activity.runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }
}
