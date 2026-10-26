import SwiftUI
import WidgetKit

/// Used only by the cloud build: started with `-uiPreview`, the app fills in sample
/// reminders and saves pictures of the widgets so they can be checked without an iPhone.
enum PreviewMode {
    static var isOn: Bool { ProcessInfo.processInfo.arguments.contains("-uiPreview") }

    @MainActor
    static func run() {
        guard isOn else { return }
        if ProcessInfo.processInfo.arguments.contains("-ko") { SharedStore.defaults.set("ko", forKey: "lang") }
        let now = Date()
        SharedStore.reminders = [
            Reminder(id: 1, text: Lang.current == .ko ? "비타민 먹기" : "Take vitamins", time: now.addingTimeInterval(-120), icon: "💊"),
            Reminder(id: 2, text: Lang.current == .ko ? "엄마한테 전화" : "Call Mum", time: now.addingTimeInterval(90 * 60), icon: "📞"),
            Reminder(id: 3, text: Lang.current == .ko ? "물 마시기" : "Drink water", time: now.addingTimeInterval(26 * 3600), repeats: .daily, icon: "💧"),
        ]
        SharedStore.todos = [
            Todo(id: 1, text: Lang.current == .ko ? "인터뷰 녹취 정리" : "Tidy interview notes", done: true),
            Todo(id: 2, text: Lang.current == .ko ? "장보기" : "Groceries"),
        ]
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let entry = DuckEntry.make(at: now)
        let sizes: [(WidgetFamily, CGSize, String)] = [
            (.systemSmall, CGSize(width: 170, height: 170), "small"),
            (.systemMedium, CGSize(width: 364, height: 170), "medium"),
            (.systemLarge, CGSize(width: 364, height: 382), "large"),
            (.accessoryRectangular, CGSize(width: 172, height: 76), "lock_rect"),
            (.accessoryCircular, CGSize(width: 76, height: 76), "lock_circle"),
        ]
        for (family, size, name) in sizes {
            let view = DuckWidgetView(entry: entry, family: family)
                .padding(name.hasPrefix("lock") ? 4 : 16)
                .frame(width: size.width, height: size.height)
                .background(DuckTheme.sky)
                .clipShape(RoundedRectangle(cornerRadius: 22))
            let renderer = ImageRenderer(content: view)
            renderer.scale = 3
            if let data = renderer.uiImage?.pngData() {
                try? data.write(to: docs.appendingPathComponent("widget_\(name).png"))
            }
        }
    }
}
