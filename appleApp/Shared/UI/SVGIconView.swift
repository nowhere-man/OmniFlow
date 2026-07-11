import SwiftUI
import WebKit

struct SVGIconView: View {
    let key: String
    var size: CGFloat = 24
    var tint = "currentColor"

    var body: some View {
        SVGWebView(key: key, tint: tint)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

#if os(iOS)
private struct SVGWebView: UIViewRepresentable {
    let key: String
    let tint: String

    func makeCoordinator() -> SVGCoordinator { SVGCoordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView(frame: .zero)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.scrollView.isScrollEnabled = false
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) { context.coordinator.load(key, tint: tint, in: view) }
}
#else
private struct SVGWebView: NSViewRepresentable {
    let key: String
    let tint: String

    func makeCoordinator() -> SVGCoordinator { SVGCoordinator() }

    func makeNSView(context: Context) -> WKWebView {
        let view = WKWebView(frame: .zero)
        view.setValue(false, forKey: "drawsBackground")
        return view
    }

    func updateNSView(_ view: WKWebView, context: Context) { context.coordinator.load(key, tint: tint, in: view) }
}
#endif

private final class SVGCoordinator {
    private static var cache: [String: (URL, String)] = [:]
    private var renderedKey = ""

    func load(_ key: String, tint: String, in view: WKWebView) {
        let renderKey = "\(key)|\(tint)"
        guard renderKey != renderedKey else { return }
        renderedKey = renderKey
        guard let (url, svg) = Self.asset(key) else { return }
        let html = """
        <html><head><meta name="viewport" content="width=device-width"></head>
        <body style="margin:0;color:\(tint);background:transparent;width:100%;height:100%;display:flex">\(svg)</body></html>
        """
        view.loadHTMLString(html, baseURL: url.deletingLastPathComponent())
    }

    private static func asset(_ key: String) -> (URL, String)? {
        if let cached = cache[key] { return cached }
        guard let url = Bundle.main.url(forResource: key, withExtension: "svg", subdirectory: "icons")
                ?? Bundle.main.url(forResource: "category", withExtension: "svg", subdirectory: "icons"),
              let svg = try? String(contentsOf: url) else { return nil }
        let value = (url, svg)
        cache[key] = value
        return value
    }
}
