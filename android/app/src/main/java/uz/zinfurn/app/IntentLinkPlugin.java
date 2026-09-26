package uz.zinfurn.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.net.URISyntaxException;

/**
 * WebView `intent://` havolalarini tushunmaydi (AR tugmasi Scene Viewer'ni shu bilan ochadi).
 * Havolani Android Intent'ga aylantirib ochamiz, ilova bo'lmasa browser_fallback_url'ga o'tamiz.
 */
@CapacitorPlugin(name = "IntentLink")
public class IntentLinkPlugin extends Plugin {

    private static final String INTENT_SCHEME = "intent";
    private static final String FALLBACK_URL_EXTRA = "browser_fallback_url";

    @Override
    public Boolean shouldOverrideLoad(Uri url) {
        if (!INTENT_SCHEME.equals(url.getScheme())) return null;

        Intent intent;
        try {
            intent = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return true;
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        intent.setSelector(null);

        try {
            getActivity().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            String fallbackUrl = intent.getStringExtra(FALLBACK_URL_EXTRA);
            if (fallbackUrl != null) {
                try {
                    getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
                } catch (ActivityNotFoundException ignored) {
                    // Brauzer ham yo'q — hech narsa qilmaymiz
                }
            }
        }
        return true;
    }
}
