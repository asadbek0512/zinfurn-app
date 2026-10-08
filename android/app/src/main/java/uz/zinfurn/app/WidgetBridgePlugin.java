package uz.zinfurn.app;

import android.content.Context;
import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONException;

/** Sayt foydalanuvchi sevimlilarini beradi — bosh ekran widget'i ulardan ko'rsatadi */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    @PluginMethod
    public void setFavorites(PluginCall call) {
        JSArray ids = call.getArray("ids", new JSArray());
        StringBuilder joined = new StringBuilder();
        try {
            for (int i = 0; i < ids.length(); i++) {
                if (i > 0) joined.append(',');
                joined.append(ids.getString(i));
            }
        } catch (JSONException e) {
            call.reject("ids must be strings");
            return;
        }
        Context context = getContext();
        context.getSharedPreferences(ProductWidget.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ProductWidget.KEY_FAVORITES, joined.toString())
            .apply();
        ProductWidget.refreshAll(context);
        call.resolve();
    }
}
