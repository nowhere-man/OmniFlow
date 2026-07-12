import SwiftUI
import WebKit

#if os(iOS)
import UIKit
private typealias PlatformImage = UIImage
#else
import AppKit
private typealias PlatformImage = NSImage
#endif

struct SVGIconView: View {
    let key: String
    var size: CGFloat = 24
    var tint = "currentColor"

    @Environment(\.colorScheme) private var colorScheme
    @State private var image: PlatformImage?

    var body: some View {
        if !key.hasPrefix("fluent-"), let png = SVGPNGCache.image(key) {
            platformTemplateImage(png)
                .frame(width: size, height: size)
                .accessibilityHidden(true)
        } else {
            Group {
                if let image {
                    platformImage(image)
                } else {
                    Color.clear
                }
            }
            .frame(width: size, height: size)
            .task(id: renderKey) {
                let rendered = await SVGSnapshotRenderer.shared.image(
                    key: key,
                    tint: resolvedTint,
                    pointSize: max(size, 24)
                )
                if !Task.isCancelled { image = rendered }
            }
            .accessibilityHidden(true)
        }
    }

    private var resolvedTint: String {
        tint == "currentColor" ? (colorScheme == .dark ? "#FFFFFF" : "#111111") : tint
    }

    private var renderKey: String { "\(key)|\(resolvedTint)|\(Int(size.rounded()))" }

    @ViewBuilder
    private func platformImage(_ image: PlatformImage) -> some View {
        #if os(iOS)
        Image(uiImage: image).resizable().scaledToFit()
        #else
        Image(nsImage: image).resizable().scaledToFit()
        #endif
    }

    @ViewBuilder
    private func platformTemplateImage(_ image: PlatformImage) -> some View {
        #if os(iOS)
        Image(uiImage: image).renderingMode(.template).resizable().scaledToFit().foregroundStyle(Color(cssHex: resolvedTint))
        #else
        Image(nsImage: image).renderingMode(.template).resizable().scaledToFit().foregroundStyle(Color(cssHex: resolvedTint))
        #endif
    }
}

private enum SVGPNGCache {
    private static let cache = NSCache<NSString, PlatformImage>()

    static func image(_ key: String) -> PlatformImage? {
        if let cached = cache.object(forKey: key as NSString) { return cached }
        guard let url = Bundle.main.url(forResource: key, withExtension: "png", subdirectory: "icons") else { return nil }
        #if os(iOS)
        guard let image = UIImage(contentsOfFile: url.path) else { return nil }
        #else
        guard let image = NSImage(contentsOf: url) else { return nil }
        #endif
        cache.setObject(image, forKey: key as NSString)
        return image
    }
}

private extension Color {
    init(cssHex: String) {
        let value = UInt64(cssHex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

@MainActor
private final class SVGSnapshotRenderer: NSObject, WKNavigationDelegate {
    static let shared = SVGSnapshotRenderer()

    private struct Request {
        let cacheKey: String
        let html: String
        let baseURL: URL
        let pixelSize: CGFloat
    }

    private let cache = NSCache<NSString, PlatformImage>()
    private var pending: [String: [CheckedContinuation<PlatformImage?, Never>]] = [:]
    private var queue: [Request] = []
    private var current: Request?
    private lazy var webView: WKWebView = {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = self
        #if os(iOS)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.scrollView.isScrollEnabled = false
        view.isUserInteractionEnabled = false
        #else
        view.setValue(false, forKey: "drawsBackground")
        #endif
        return view
    }()

    func image(key: String, tint: String, pointSize: CGFloat) async -> PlatformImage? {
        let pixelSize = max(48, ceil(pointSize * 2))
        let cacheKey = "\(key)|\(tint)|\(Int(pixelSize))"
        if let cached = cache.object(forKey: cacheKey as NSString) { return cached }
        guard let asset = Self.asset(key) else { return nil }
        let html = """
        <html><head><meta name="viewport" content="width=device-width"></head>
        <body style="margin:0;color:\(tint);background:transparent;width:100%;height:100%;display:flex">
        <style>svg{width:100%;height:100%;display:block}</style>\(asset.svg)
        </body></html>
        """
        return await withCheckedContinuation { continuation in
            if pending[cacheKey] != nil {
                pending[cacheKey]?.append(continuation)
                return
            }
            pending[cacheKey] = [continuation]
            queue.append(Request(
                cacheKey: cacheKey,
                html: html,
                baseURL: asset.url.deletingLastPathComponent(),
                pixelSize: pixelSize
            ))
            renderNextIfNeeded()
        }
    }

    nonisolated func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        Task { @MainActor [weak self] in self?.snapshotCurrent() }
    }

    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor [weak self] in self?.finish(nil) }
    }

    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor [weak self] in self?.finish(nil) }
    }

    private func snapshotCurrent() {
        guard let current else { return }
        let snapshot = WKSnapshotConfiguration()
        snapshot.rect = CGRect(x: 0, y: 0, width: current.pixelSize, height: current.pixelSize)
        webView.takeSnapshot(with: snapshot) { [weak self] image, _ in
            Task { @MainActor in self?.finish(image) }
        }
    }

    private func renderNextIfNeeded() {
        guard current == nil, !queue.isEmpty else { return }
        current = queue.removeFirst()
        guard let current else { return }
        webView.frame = CGRect(x: 0, y: 0, width: current.pixelSize, height: current.pixelSize)
        webView.loadHTMLString(current.html, baseURL: current.baseURL)
    }

    private func finish(_ image: PlatformImage?) {
        guard let current else { return }
        if let image { cache.setObject(image, forKey: current.cacheKey as NSString) }
        pending.removeValue(forKey: current.cacheKey)?.forEach { $0.resume(returning: image) }
        self.current = nil
        renderNextIfNeeded()
    }

    private static var assets: [String: (url: URL, svg: String)] = [:]

    private static func asset(_ key: String) -> (url: URL, svg: String)? {
        if let cached = assets[key] { return cached }
        guard let url = Bundle.main.url(forResource: key, withExtension: "svg", subdirectory: "icons")
                ?? Bundle.main.url(forResource: "category", withExtension: "svg", subdirectory: "icons"),
              let svg = try? String(contentsOf: url) else { return nil }
        let asset = (url, svg)
        assets[key] = asset
        return asset
    }
}
