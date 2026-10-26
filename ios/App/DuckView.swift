import SwiftUI
import WebKit

/// Runs the live 3D duck (duck.html + the Android app's duck.frag) in a web view,
/// and saves still pictures of it for the Home Screen widget.
final class DuckController: NSObject, ObservableObject, WKNavigationDelegate {
    let webView: WKWebView
    private var loaded = false
    private var look = SharedStore.look

    override init() {
        let config = WKWebViewConfiguration()
        webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.isUserInteractionEnabled = false
        webView.navigationDelegate = self
        if let html = Self.page() { webView.loadHTMLString(html, baseURL: nil) }
    }

    private static func page() -> String? {
        guard let htmlURL = Bundle.main.url(forResource: "duck", withExtension: "html"),
              let fragURL = Bundle.main.url(forResource: "duck", withExtension: "frag"),
              let html = try? String(contentsOf: htmlURL, encoding: .utf8),
              let frag = try? String(contentsOf: fragURL, encoding: .utf8),
              let json = try? JSONEncoder().encode(frag),
              let literal = String(data: json, encoding: .utf8) else { return nil }
        return html.replacingOccurrences(of: "/*FRAG*/\"\"", with: literal)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loaded = true
        apply(look)
    }

    func apply(_ newLook: DuckLook) {
        look = newLook
        guard loaded else { return }
        webView.evaluateJavaScript("duck.setLook(\(newLook.js))")
        savePicturesIfNeeded()
    }

    func tap(quack: Bool) {
        guard loaded else { return }
        webView.evaluateJavaScript("duck.squash(); duck.react('happy', 2.5)")
        if quack { Quack.play() }
    }

    func excite() {
        guard loaded else { return }
        webView.evaluateJavaScript("duck.react('excited', 6)")
    }

    /// The widget can't run 3D, so it shows pictures of Ducky in each mood.
    /// They are redrawn only when the look changes.
    private func savePicturesIfNeeded() {
        guard let folder = SharedStore.pictureFolder else { return }
        let key = look.key
        guard SharedStore.defaults.string(forKey: "pictureKey") != key else { return }
        let pictures = DuckMood.allCases.flatMap { mood in [false, true].map { (mood, $0) } }
        var remaining = pictures.count
        var allSaved = true
        for (mood, lock) in pictures {
            webView.evaluateJavaScript("duck.snapshot('\(mood.rawValue)', 360, \(lock))") { result, _ in
                if let url = result as? String, let comma = url.firstIndex(of: ","),
                   let data = Data(base64Encoded: String(url[url.index(after: comma)...])) {
                    do {
                        try data.write(to: folder.appendingPathComponent(DuckPicture.fileName(mood, lock: lock)), options: .atomic)
                    } catch {
                        allSaved = false
                    }
                } else {
                    allSaved = false
                }
                remaining -= 1
                if remaining == 0 {
                    if allSaved { SharedStore.defaults.set(key, forKey: "pictureKey") }
                    SharedStore.reloadWidgets()
                    LiveDuck.refresh()
                }
            }
        }
    }
}

struct DuckView: UIViewRepresentable {
    let controller: DuckController
    func makeUIView(context: Context) -> WKWebView { controller.webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
