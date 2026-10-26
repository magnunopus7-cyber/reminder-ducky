import SwiftUI
import UIKit
import WidgetKit

/// What one moment of the widget shows.
struct DuckEntry: TimelineEntry {
    let date: Date
    let mood: DuckMood
    let items: [WidgetItem]
    let todos: [Todo]
}

struct WidgetItem: Identifiable, Hashable {
    let id: Int64
    let icon: String
    let text: String
    let date: Date
    let due: Bool
}

extension DuckEntry {
    static func make(at date: Date) -> DuckEntry {
        let list = SharedStore.upcoming(now: date)
        let items = list.prefix(6).map { WidgetItem(id: $0.reminder.id, icon: $0.reminder.icon, text: $0.reminder.text, date: $0.date, due: $0.due) }
        return DuckEntry(
            date: date,
            mood: DuckMood.at(date, due: items.first?.due == true),
            items: Array(items),
            todos: Array(SharedStore.todos.prefix(8))
        )
    }
}

enum DuckPicture {
    /// `lock` pictures are white with the shading kept in transparency, for the one-colour Lock Screen.
    static func fileName(_ mood: DuckMood, lock: Bool) -> String {
        (lock ? "duck_lock_" : "duck_") + mood.rawValue + ".png"
    }

    /// The duck as the app last drew it, or the bundled default duck.
    static func image(_ mood: DuckMood, lock: Bool = false) -> UIImage? {
        let name = fileName(mood, lock: lock)
        if let folder = SharedStore.pictureFolder,
           let img = UIImage(contentsOfFile: folder.appendingPathComponent(name).path) {
            return img
        }
        return UIImage(named: String(name.dropLast(4)))
    }
}

enum DuckTheme {
    static let skyTop = Color(red: Double(0x6F) / 255, green: Double(0xB1) / 255, blue: 1)
    static let skyBottom = Color(red: Double(0xBF) / 255, green: Double(0xDF) / 255, blue: 1)
    static let ink = Color(red: Double(0x1F) / 255, green: Double(0x2A) / 255, blue: Double(0x3D) / 255)
    static let inkSoft = Color(red: Double(0x66) / 255, green: Double(0x72) / 255, blue: Double(0x8A) / 255)
    static let page = Color(red: Double(0xEE) / 255, green: Double(0xF6) / 255, blue: 1)
    static let sky = LinearGradient(colors: [skyTop, skyBottom], startPoint: .top, endPoint: .bottom)
}

struct DuckImageView: View {
    let mood: DuckMood
    var lock = false
    var body: some View {
        if let img = DuckPicture.image(mood, lock: lock) {
            Image(uiImage: img).resizable().scaledToFit()
        } else {
            Text("🦆").font(.system(size: 60))
        }
    }
}

/// A white pill like the Android reminder bubble.
struct ReminderPill: View {
    let item: WidgetItem
    var compact = false

    var body: some View {
        HStack(spacing: 6) {
            Text(item.icon).font(compact ? .caption : .callout)
            VStack(alignment: .leading, spacing: 0) {
                Text(item.text)
                    .font(compact ? .caption.weight(.semibold) : .subheadline.weight(.semibold))
                    .foregroundStyle(DuckTheme.ink)
                    .lineLimit(1)
                Text(item.due ? L("due_now") : friendlyTime(item.date))
                    .font(.caption2)
                    .foregroundStyle(item.due ? Color.pink : DuckTheme.inkSoft)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if item.due {
                Button(intent: CompleteReminderIntent(id: item.id)) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(compact ? .body : .title3)
                        .foregroundStyle(.green)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, compact ? 5 : 7)
        .background(Capsule().fill(.white.opacity(0.92)))
    }
}

struct DuckWidgetView: View {
    let entry: DuckEntry
    let family: WidgetFamily

    var body: some View {
        switch family {
        case .systemSmall: small
        case .systemMedium: medium
        case .systemLarge, .systemExtraLarge: large
        case .accessoryRectangular: rectangular
        case .accessoryInline: inline
        case .accessoryCircular: circular
        @unknown default: small
        }
    }

    private var small: some View {
        VStack(spacing: 2) {
            DuckImageView(mood: entry.mood)
                .frame(maxHeight: .infinity)
            if let first = entry.items.first {
                ReminderPill(item: first, compact: true)
            } else {
                Text(L("widget_none")).font(.caption2.weight(.semibold)).foregroundStyle(DuckTheme.ink)
            }
        }
    }

    private var medium: some View {
        HStack(spacing: 8) {
            DuckImageView(mood: entry.mood).frame(width: 120)
            VStack(alignment: .leading, spacing: 5) {
                if entry.items.isEmpty {
                    Spacer()
                    Text(L("widget_none")).font(.subheadline.weight(.semibold)).foregroundStyle(DuckTheme.ink)
                    Spacer()
                } else {
                    ForEach(entry.items.prefix(3)) { ReminderPill(item: $0, compact: true) }
                    Spacer(minLength: 0)
                }
            }
        }
    }

    private var large: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .bottom) {
                DuckImageView(mood: entry.mood).frame(height: 120)
                VStack(alignment: .leading) {
                    Text(L("app_title")).font(.headline).foregroundStyle(DuckTheme.ink)
                    Text(L("tagline")).font(.caption).foregroundStyle(DuckTheme.ink.opacity(0.7))
                }
                Spacer()
            }
            if entry.items.isEmpty {
                Text(L("widget_none")).font(.subheadline.weight(.semibold)).foregroundStyle(DuckTheme.ink)
            } else {
                ForEach(entry.items.prefix(3)) { ReminderPill(item: $0) }
            }
            if !entry.todos.isEmpty {
                Text(L("todo_title")).font(.caption.weight(.bold)).foregroundStyle(DuckTheme.ink.opacity(0.8))
                ForEach(entry.todos.prefix(3)) { t in
                    Button(intent: ToggleTodoIntent(id: t.id)) {
                        HStack(spacing: 6) {
                            Image(systemName: t.done ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(t.done ? .green : DuckTheme.inkSoft)
                            Text(t.text).font(.caption).strikethrough(t.done)
                                .foregroundStyle(DuckTheme.ink).lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            Spacer(minLength: 0)
        }
    }

    private var rectangular: some View {
        HStack(spacing: 2) {
            // As tall as the widget allows: Apple fixes the Lock Screen widget size.
            DuckImageView(mood: entry.mood, lock: true)
                .frame(maxHeight: .infinity)
                .aspectRatio(1, contentMode: .fit)
                .padding(-4)
            VStack(alignment: .leading, spacing: 0) {
                if let first = entry.items.first {
                    Text(first.text).font(.headline).lineLimit(1)
                    Text(first.due ? L("due_now") : friendlyTime(first.date)).font(.caption).lineLimit(1)
                } else {
                    Text(L("widget_none")).font(.caption).lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var inline: some View {
        if let first = entry.items.first {
            Text("\(L("widget_name")) · \(first.text) · \(first.due ? L("due_now") : friendlyTime(first.date))")
        } else {
            Text(L("widget_name") + " · " + L("widget_none"))
        }
    }

    private var circular: some View {
        ZStack {
            AccessoryWidgetBackground()
            DuckImageView(mood: entry.mood, lock: true).padding(-2)
        }
    }
}
