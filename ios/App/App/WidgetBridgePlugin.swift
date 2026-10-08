import Capacitor
import WidgetKit

/** Sayt foydalanuvchi sevimlilarini beradi — widget App Group orqali o'qiydi (pullik team'da) */
@objc(WidgetBridgePlugin)
public class WidgetBridgePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "WidgetBridgePlugin"
    public let jsName = "WidgetBridge"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "setFavorites", returnType: CAPPluginReturnPromise)
    ]

    static let appGroup = "group.uz.zinfurn.app"
    static let favoritesKey = "favorite_ids"

    @objc func setFavorites(_ call: CAPPluginCall) {
        let ids = (call.getArray("ids") ?? []).compactMap { $0 as? String }
        // App Group yo'q bo'lsa (bepul team) suite baribir ochiladi, lekin widget ko'rmaydi — zarari yo'q
        UserDefaults(suiteName: Self.appGroup)?.set(ids, forKey: Self.favoritesKey)
        WidgetCenter.shared.reloadAllTimelines()
        call.resolve()
    }
}
