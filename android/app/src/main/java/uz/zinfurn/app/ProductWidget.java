package uz.zinfurn.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bosh ekran widget'i: flash sale'dagi mahsulot (bo'lmasa eng yangisi).
 * Bosilganda app o'sha mahsulot sahifasida ochiladi (App Link → appUrlOpen → router.push).
 */
public class ProductWidget extends AppWidgetProvider {

    private static final String GRAPHQL_URL = "https://api.zinfurn.uz/graphql";
    private static final String API_BASE = "https://api.zinfurn.uz/";
    private static final String SITE_BASE = "https://zinfurn.uz";
    private static final int PRODUCT_LIMIT = 16;
    private static final int TIMEOUT_MS = 15000;
    private static final int IMAGE_MAX_SIDE = 400;
    private static final int SALE_PRICE_COLOR = 0xFFE53935;
    private static final int PRICE_COLOR = 0xFF1A1A1A;
    private static final String QUERY_FIELDS =
        "{list{_id propertyTitle propertyPrice propertyIsOnSale propertySalePrice propertySaleStartsAt propertySaleExpiresAt propertyImages}}}";
    /** App (WidgetBridgePlugin) yozadi: foydalanuvchi sevimlilari, vergul bilan */
    static final String PREFS = "zinfurn_widget";
    static final String KEY_FAVORITES = "favorite_ids";

    private static final String RATES_URL = "https://open.er-api.com/v6/latest/USD";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /** Sevimlilar o'zgarganda app chaqiradi — barcha widget'lar darhol yangilanadi */
    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ProductWidget.class));
        if (ids.length == 0) return;
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            RemoteViews views = buildViews(app);
            for (int id : ids) manager.updateAppWidget(id, views);
        });
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                RemoteViews views = buildViews(app);
                for (int id : ids) manager.updateAppWidget(id, views);
            } finally {
                pending.finish();
            }
        });
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_product);
        String link = SITE_BASE;
        try {
            String[] favorites = readFavorites(context);
            JSONArray list = favorites.length > 0 ? fetchProducts(favorites) : new JSONArray();
            boolean favorite = list.length() > 0;
            if (!favorite) list = fetchProducts(new String[0]);
            JSONObject product = pickProduct(list);
            if (product != null) {
                boolean sale = isSaleActive(product);
                String currency = currentCurrency();
                double rate = fetchRate(currency);
                double price = product.getDouble("propertyPrice");
                views.setTextViewText(R.id.widget_badge, context.getString(sale ? R.string.widget_sale : favorite ? R.string.widget_favorite : R.string.widget_new));
                views.setTextViewText(R.id.widget_title, product.getString("propertyTitle"));
                views.setTextViewText(R.id.widget_price, formatPrice(sale ? product.getDouble("propertySalePrice") : price, currency, rate));
                views.setTextColor(R.id.widget_price, sale ? SALE_PRICE_COLOR : PRICE_COLOR);
                views.setTextViewText(R.id.widget_old_price, sale ? formatPrice(price, currency, rate) : "");
                views.setInt(R.id.widget_old_price, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);

                JSONArray images = product.optJSONArray("propertyImages");
                Bitmap image = images != null && images.length() > 0 ? fetchImage(API_BASE + images.getString(0)) : null;
                if (image != null) views.setImageViewBitmap(R.id.widget_image, image);
                link = SITE_BASE + "/products/detail?id=" + product.getString("_id");
            }
        } catch (Exception ignored) {
            // Tarmoq yo'q bo'lsa — standart ko'rinish qoladi, keyingi yangilanishda qayta urinadi
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.getPackageName());
        PendingIntent click = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, click);
        return views;
    }

    private static String[] readFavorites(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAVORITES, "");
        return raw.isEmpty() ? new String[0] : raw.split(",");
    }

    /** Sevimlilar bo'lsa faqat ular (idList), aks holda eng yangi mahsulotlar */
    private static String buildQuery(String[] favorites) {
        StringBuilder search = new StringBuilder("{");
        if (favorites.length > 0) {
            search.append("idList:[");
            for (int i = 0; i < favorites.length; i++) {
                if (i > 0) search.append(',');
                search.append('"').append(favorites[i].replaceAll("[^0-9a-f]", "")).append('"');
            }
            search.append(']');
        }
        search.append('}');
        int limit = favorites.length > 0 ? favorites.length : PRODUCT_LIMIT;
        return "{getProperties(input:{page:1,limit:" + limit + ",sort:\"createdAt\",direction:DESC,search:" + search + "})"
            + QUERY_FIELDS;
    }

    private static JSONArray fetchProducts(String[] favorites) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(GRAPHQL_URL).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            byte[] body = new JSONObject().put("query", buildQuery(favorites)).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            try (InputStream in = conn.getInputStream()) {
                String json = new String(readAll(in), StandardCharsets.UTF_8);
                return new JSONObject(json).getJSONObject("data").getJSONObject("getProperties").getJSONArray("list");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static JSONObject pickProduct(JSONArray list) throws Exception {
        for (int i = 0; i < list.length(); i++) {
            if (isSaleActive(list.getJSONObject(i))) return list.getJSONObject(i);
        }
        return list.length() > 0 ? list.getJSONObject(0) : null;
    }

    private static boolean isSaleActive(JSONObject p) {
        if (!p.optBoolean("propertyIsOnSale") || p.isNull("propertySalePrice")) return false;
        long now = System.currentTimeMillis();
        Date start = parseDate(p.optString("propertySaleStartsAt", null));
        Date end = parseDate(p.optString("propertySaleExpiresAt", null));
        if (start != null && start.getTime() > now) return false;
        return end == null || end.getTime() >= now;
    }

    private static Date parseDate(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    // Widget'ga beriladigan bitmap hajmi cheklangan — kichraytirib olinadi
    private static Bitmap fetchImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            byte[] data;
            try (InputStream in = conn.getInputStream()) {
                data = readAll(in);
            } finally {
                conn.disconnect();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= IMAGE_MAX_SIDE) opts.inSampleSize *= 2;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    // Narx saytdagidek (zinfurn-next CurrencyContext): valyuta telefon mintaqasi/tilidan
    private static String currentCurrency() {
        Locale locale = Locale.getDefault();
        switch (locale.getCountry()) {
            case "UZ": return "UZS";
            case "KR": return "KRW";
            case "RU": return "RUB";
            case "AE": return "AED";
        }
        switch (locale.getLanguage()) {
            case "uz": return "UZS";
            case "ko": return "KRW";
            case "ru": return "RUB";
            case "ar": return "AED";
            default: return "USD";
        }
    }

    /** Kurs olinmasa saytdagi DEFAULT_RATES */
    private static double fallbackRate(String currency) {
        switch (currency) {
            case "UZS": return 12700;
            case "KRW": return 1350;
            case "RUB": return 90;
            case "AED": return 3.67;
            default: return 1;
        }
    }

    private static double fetchRate(String currency) {
        if (currency.equals("USD")) return 1;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(RATES_URL).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            try (InputStream in = conn.getInputStream()) {
                JSONObject rates = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8)).getJSONObject("rates");
                return rates.getDouble(currency);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return fallbackRate(currency);
        }
    }

    private static String formatPrice(double usd, String currency, double rate) {
        double converted = Math.round(usd * rate);
        switch (currency) {
            case "KRW": return "₩" + String.format(Locale.KOREA, "%,.0f", converted);
            case "RUB": return String.format(new Locale("ru", "RU"), "%,.0f", converted) + " ₽";
            case "AED": return String.format(Locale.US, "%,.0f", converted) + " د.إ";
            case "UZS":
                if (converted >= 1_000_000_000) return String.format(Locale.US, "%.1f mlrd so'm", converted / 1_000_000_000);
                if (converted >= 1_000_000) return String.format(Locale.US, "%.1f mln so'm", converted / 1_000_000);
                if (converted >= 1_000) return String.format(Locale.US, "%.0f ming so'm", converted / 1_000);
                return String.format(Locale.US, "%.0f so'm", converted);
            default:
                DecimalFormat usdFormat = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
                return "$" + usdFormat.format(usd);
        }
    }
}
