package uz.zinfurn.app;

import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    // Ochiq drawer/modal (MUI — Escape bilan) yoki chat/AI paneli (backdrop bosish) bo'lsa yopadi.
    // true qaytarsa — orqaga bosish shu bilan tugaydi, sahifadan chiqilmaydi.
    private static final String CLOSE_OVERLAY_JS =
        "(function(){" +
        "var m=document.querySelectorAll('.MuiModal-root:not(.MuiModal-hidden)');" +
        "if(m.length){m[m.length-1].dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));return true;}" +
        "var b=document.querySelector('.mob-chat-backdrop');" +
        "if(b){b.click();return true;}" +
        "return false;})()";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(IntentLinkPlugin.class);
        super.onCreate(savedInstanceState);

        // Capacitor App plugin'idan keyin qo'shiladi — shuning uchun birinchi bo'lib chaqiriladi
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = bridge.getWebView();
                webView.evaluateJavascript(CLOSE_OVERLAY_JS, closed -> {
                    if ("true".equals(closed)) return;
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        moveTaskToBack(true);
                    }
                });
            }
        });
    }
}
