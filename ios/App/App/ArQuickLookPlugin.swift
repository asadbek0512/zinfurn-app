import ARKit
import Capacitor
import QuickLook

/**
 * WKWebView `<a rel="ar">` (AR Quick Look) ni qo'llamaydi — faqat Safari'da ishlaydi.
 * Sayt GLB'ni USDZ'ga aylantirib base64 qilib beradi, biz faylga yozib AR Quick Look'da ochamiz.
 */
@objc(ArQuickLookPlugin)
public class ArQuickLookPlugin: CAPPlugin, CAPBridgedPlugin, QLPreviewControllerDataSource {
    public let identifier = "ArQuickLookPlugin"
    public let jsName = "ArQuickLook"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "open", returnType: CAPPluginReturnPromise)
    ]

    private static let fileName = "zinfurn-ar.usdz"
    private var previewItem: ARQuickLookPreviewItem?

    @objc func isAvailable(_ call: CAPPluginCall) {
        call.resolve(["available": ARWorldTrackingConfiguration.isSupported])
    }

    @objc func open(_ call: CAPPluginCall) {
        guard let base64 = call.getString("data"), let data = Data(base64Encoded: base64) else {
            call.reject("USDZ data missing")
            return
        }
        let fileUrl = FileManager.default.temporaryDirectory.appendingPathComponent(Self.fileName)
        do {
            try data.write(to: fileUrl, options: .atomic)
        } catch {
            call.reject("USDZ write failed", nil, error)
            return
        }

        previewItem = ARQuickLookPreviewItem(fileAt: fileUrl)

        DispatchQueue.main.async {
            let preview = QLPreviewController()
            preview.dataSource = self
            self.bridge?.viewController?.present(preview, animated: true)
            call.resolve()
        }
    }

    public func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
        previewItem == nil ? 0 : 1
    }

    public func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
        previewItem ?? ARQuickLookPreviewItem(fileAt: FileManager.default.temporaryDirectory)
    }
}
