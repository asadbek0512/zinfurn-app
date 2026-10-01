package uz.zinfurn.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
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
    private static final String QUERY =
        "{getProperties(input:{page:1,limit:" + PRODUCT_LIMIT + ",sort:\"createdAt\",direction:DESC,search:{}})"
            + "{list{_id propertyTitle propertyPrice propertyIsOnSale propertySalePrice propertySaleStartsAt propertySaleExpiresAt propertyImages}}}";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

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
            JSONObject product = pickProduct(fetchProducts());
            if (product != null) {
                boolean sale = isSaleActive(product);
                double price = product.getDouble("propertyPrice");
                views.setTextViewText(R.id.widget_badge, context.getString(sale ? R.string.widget_sale : R.string.widget_new));
                views.setTextViewText(R.id.widget_title, product.getString("propertyTitle"));
                views.setTextViewText(R.id.widget_price, formatPrice(sale ? product.getDouble("propertySalePrice") : price));
                views.setTextColor(R.id.widget_price, sale ? SALE_PRICE_COLOR : PRICE_COLOR);
                views.setTextViewText(R.id.widget_old_price, sale ? formatPrice(price) : "");
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

    private static JSONArray fetchProducts() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(GRAPHQL_URL).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            byte[] body = new JSONObject().put("query", QUERY).toString().getBytes(StandardCharsets.UTF_8);
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

    private static String formatPrice(double value) {
        return "$" + Math.round(value);
    }
}
