import SwiftUI
import UIKit
import WidgetKit

// Bosh ekran widget'i: flash sale'dagi mahsulot (bo'lmasa eng yangisi).
// Bosilganda app o'sha mahsulot sahifasida ochiladi (appUrlOpen → router.push).

private enum Config {
    static let graphqlURL = URL(string: "https://api.zinfurn.uz/graphql")!
    static let apiBase = "https://api.zinfurn.uz/"
    static let siteBase = "https://zinfurn.uz"
    static let productLimit = 16
    static let refreshMinutes = 60
    static let imageMaxSide: CGFloat = 400
    static let requestTimeout: TimeInterval = 15
    static let brand = Color(red: 0.84, green: 0.37, blue: 0.16)
    /** App (WidgetBridgePlugin) yozadi — foydalanuvchi sevimlilari */
    static let appGroup = "group.uz.zinfurn.app"
    static let favoritesKey = "favorite_ids"
    static let fields = "{list{_id propertyTitle propertyPrice propertyIsOnSale propertySalePrice propertySaleStartsAt propertySaleExpiresAt propertyImages}}}"
}

private struct GraphQLResponse: Decodable {
    struct DataBox: Decodable { let getProperties: Properties }
    struct Properties: Decodable { let list: [Product] }
    let data: DataBox?
}

struct Product: Decodable {
    let _id: String
    let propertyTitle: String
    let propertyPrice: Double
    let propertyIsOnSale: Bool?
    let propertySalePrice: Double?
    let propertySaleStartsAt: String?
    let propertySaleExpiresAt: String?
    let propertyImages: [String]

    var isSaleActive: Bool {
        guard propertyIsOnSale == true, propertySalePrice != nil else { return false }
        let now = Date()
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let s = propertySaleStartsAt.flatMap(iso.date(from:)), s > now { return false }
        if let e = propertySaleExpiresAt.flatMap(iso.date(from:)), e < now { return false }
        return true
    }
}

// Narx saytdagidek ko'rsatiladi (zinfurn-next CurrencyContext bilan bir xil format):
// valyuta telefon mintaqasi/tilidan, kurs sayt ishlatadigan API'dan olinadi
private enum WidgetCurrency {
    case usd, krw, uzs, rub, aed

    static let ratesURL = URL(string: "https://open.er-api.com/v6/latest/USD")!
    private static let byRegion: [String: WidgetCurrency] = ["UZ": .uzs, "KR": .krw, "RU": .rub, "AE": .aed]
    private static let byLanguage: [String: WidgetCurrency] = ["uz": .uzs, "ko": .krw, "ru": .rub, "ar": .aed]

    static var current: WidgetCurrency {
        if let region = Locale.current.regionCode, let c = byRegion[region] { return c }
        if let language = Locale.current.languageCode, let c = byLanguage[language] { return c }
        return .usd
    }

    var code: String {
        switch self {
        case .usd: return "USD"
        case .krw: return "KRW"
        case .uzs: return "UZS"
        case .rub: return "RUB"
        case .aed: return "AED"
        }
    }

    /// Kurs olinmasa saytdagi DEFAULT_RATES
    var fallbackRate: Double {
        switch self {
        case .usd: return 1
        case .krw: return 1350
        case .uzs: return 12700
        case .rub: return 90
        case .aed: return 3.67
        }
    }

    func format(_ usd: Double, rate: Double) -> String {
        let converted = (usd * rate).rounded()
        switch self {
        case .krw: return "₩" + Self.grouped(converted, locale: "ko_KR")
        case .rub: return Self.grouped(converted, locale: "ru_RU") + " ₽"
        case .aed: return Self.grouped(converted, locale: "en_US") + " د.إ"
        case .uzs:
            if converted >= 1_000_000_000 { return String(format: "%.1f mlrd so'm", converted / 1_000_000_000) }
            if converted >= 1_000_000 { return String(format: "%.1f mln so'm", converted / 1_000_000) }
            if converted >= 1_000 { return String(format: "%.0f ming so'm", converted / 1_000) }
            return String(format: "%.0f so'm", converted)
        case .usd: return "$" + Self.grouped(usd, locale: "en_US", fractionDigits: 2)
        }
    }

    private static func grouped(_ value: Double, locale: String, fractionDigits: Int = 0) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: locale)
        f.maximumFractionDigits = fractionDigits
        return f.string(from: NSNumber(value: value)) ?? String(Int(value))
    }
}

private struct RatesResponse: Decodable { let rates: [String: Double] }

struct ProductEntry: TimelineEntry {
    let date: Date
    let id: String?
    let title: String
    let price: String?
    let oldPrice: String?
    let onSale: Bool
    var favorite = false
    let image: UIImage?

    static let placeholder = ProductEntry(date: Date(), id: nil, title: "Zinfurn", price: nil, oldPrice: nil, onSale: false, image: nil)
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> ProductEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (ProductEntry) -> Void) {
        if context.isPreview { return completion(.placeholder) }
        Task { completion(await loadEntry()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ProductEntry>) -> Void) {
        Task {
            let entry = await loadEntry()
            let next = Calendar.current.date(byAdding: .minute, value: Config.refreshMinutes, to: Date()) ?? Date()
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }

    private func loadEntry() async -> ProductEntry {
        let favoriteIds = UserDefaults(suiteName: Config.appGroup)?.stringArray(forKey: Config.favoritesKey) ?? []
        let favorite = favoriteIds.isEmpty ? nil : await fetchProduct(ids: favoriteIds)
        let fallback = favorite == nil ? await fetchProduct(ids: []) : nil
        guard let product = favorite ?? fallback else { return .placeholder }
        let sale = product.isSaleActive
        let currency = WidgetCurrency.current
        let rate = await fetchRate(currency)
        let format = { (usd: Double) in currency.format(usd, rate: rate) }
        return ProductEntry(
            date: Date(),
            id: product._id,
            title: product.propertyTitle,
            price: (sale ? product.propertySalePrice : product.propertyPrice).map(format),
            oldPrice: sale ? format(product.propertyPrice) : nil,
            onSale: sale,
            favorite: favorite != nil,
            image: await fetchImage(product.propertyImages.first)
        )
    }

    /// ids bo'sh bo'lmasa faqat sevimlilar (idList), aks holda eng yangilari
    private func fetchProduct(ids: [String]) async -> Product? {
        let hex = CharacterSet(charactersIn: "0123456789abcdef").inverted
        let safeIds = ids.map { "\"\($0.components(separatedBy: hex).joined())\"" }
        let search = ids.isEmpty ? "{}" : "{idList:[\(safeIds.joined(separator: ","))]}"
        let limit = ids.isEmpty ? Config.productLimit : ids.count
        let query = "{getProperties(input:{page:1,limit:\(limit),sort:\"createdAt\",direction:DESC,search:\(search)})\(Config.fields)"
        var request = URLRequest(url: Config.graphqlURL, timeoutInterval: Config.requestTimeout)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["query": query])
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let list = (try? JSONDecoder().decode(GraphQLResponse.self, from: data))?.data?.getProperties.list
        else { return nil }
        return list.first(where: { $0.isSaleActive }) ?? list.first
    }

    private func fetchRate(_ currency: WidgetCurrency) async -> Double {
        if currency == .usd { return 1 }
        let request = URLRequest(url: WidgetCurrency.ratesURL, timeoutInterval: Config.requestTimeout)
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let rate = (try? JSONDecoder().decode(RatesResponse.self, from: data))?.rates[currency.code]
        else { return currency.fallbackRate }
        return rate
    }

    // Widget xotirasi cheklangan — rasm kichraytirib olinadi
    private func fetchImage(_ path: String?) async -> UIImage? {
        guard let path, let url = URL(string: Config.apiBase + path),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let scale = min(1, Config.imageMaxSide / max(image.size.width, image.size.height))
        return image.preparingThumbnail(of: CGSize(width: image.size.width * scale, height: image.size.height * scale)) ?? image
    }
}

struct ZinfurnWidgetView: View {
    let entry: ProductEntry
    @Environment(\.widgetFamily) private var family

    private var link: URL? {
        guard let id = entry.id else { return URL(string: Config.siteBase) }
        return URL(string: "\(Config.siteBase)/products/detail?id=\(id)")
    }

    var body: some View {
        content
            .widgetURL(link)
            .widgetBackground()
    }

    @ViewBuilder private var content: some View {
        if family == .systemMedium {
            HStack(spacing: 12) {
                productImage.frame(maxWidth: .infinity, maxHeight: .infinity)
                info.frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                productImage.frame(maxWidth: .infinity, maxHeight: .infinity)
                info
            }
        }
    }

    private var productImage: some View {
        ZStack {
            Color(white: 0.96)
            if let image = entry.image {
                Image(uiImage: image).resizable().scaledToFit()
            } else {
                Image(systemName: "bed.double").font(.largeTitle).foregroundColor(Config.brand)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private var info: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(entry.onSale ? "FLASH SALE" : entry.favorite ? "YOUR FAVORITE" : "NEW IN")
                .font(.system(size: 10, weight: .heavy))
                .foregroundColor(Config.brand)
            Text(entry.title)
                .font(.system(size: 12, weight: .semibold))
                .lineLimit(family == .systemMedium ? 3 : 1)
            HStack(spacing: 4) {
                if let price = entry.price {
                    Text(price).font(.system(size: 13, weight: .bold))
                        .foregroundColor(entry.onSale ? .red : .primary)
                }
                if let old = entry.oldPrice {
                    Text(old).font(.system(size: 10)).strikethrough().foregroundColor(.secondary)
                }
            }
        }
    }
}

private extension View {
    @ViewBuilder func widgetBackground() -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(Color(UIColor.systemBackground), for: .widget)
        } else {
            padding().background(Color(UIColor.systemBackground))
        }
    }
}

@main
struct ZinfurnWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ZinfurnWidget", provider: Provider()) { entry in
            ZinfurnWidgetView(entry: entry)
        }
        .configurationDisplayName("Zinfurn")
        .description("Flash sale and new furniture")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
