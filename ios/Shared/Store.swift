import Foundation
#if canImport(WidgetKit)
import WidgetKit
#endif

enum Repeats: String, Codable, CaseIterable, Identifiable {
    case once = "NONE", daily = "DAILY", weekly = "WEEKLY"
    var id: String { rawValue }
    var label: String {
        switch self {
        case .once: return L("repeat_once")
        case .daily: return L("repeat_daily")
        case .weekly: return L("repeat_weekly")
        }
    }

    func step(_ date: Date, by n: Int) -> Date {
        let cal = Calendar.current
        return cal.date(byAdding: self == .weekly ? .weekOfYear : .day, value: n, to: date) ?? date
    }
}

struct Reminder: Codable, Identifiable, Hashable {
    var id: Int64
    var text: String
    var time: Date
    var repeats: Repeats = .once
    var icon: String = Reminder.defaultIcon
    /// When a repeating reminder was last marked done.
    var lastDone: Date?

    static let defaultIcon = "🔔"
    static let icons = ["🔔", "💊", "💧", "📞", "📅", "🏃", "🍽️", "📚", "⭐"]
    static func newId() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// How long a reminder stays "due" (Ducky excited, Done button) after it goes off.
    static let dueWindow: TimeInterval = 60 * 60

    /// The occurrence to show at [now]: the one that is due, or else the next one.
    func occurrence(at now: Date = Date()) -> (date: Date, due: Bool) {
        if repeats == .once { return (time, time <= now) }
        var next = time
        var guardCount = 0
        while next <= now && guardCount < 5000 { next = repeats.step(next, by: 1); guardCount += 1 }
        let prev = repeats.step(next, by: -1)
        if prev >= time, now.timeIntervalSince(prev) < Reminder.dueWindow, (lastDone ?? .distantPast) < prev {
            return (prev, true)
        }
        return (next, false)
    }
}

struct Upcoming: Identifiable {
    let reminder: Reminder
    let date: Date
    let due: Bool
    var id: Int64 { reminder.id }
}

struct Todo: Codable, Identifiable, Hashable {
    var id: Int64
    var text: String
    var done: Bool = false
}

/// Everything the app and the widget share, kept in the App Group so both can read it.
enum SharedStore {
    static let groupID = "group.app.reminderducky"
    static let defaults: UserDefaults = UserDefaults(suiteName: groupID) ?? .standard

    /// Folder for the widget's duck pictures. Without the App Group (unsigned test builds)
    /// the widget falls back to the pictures bundled with it.
    static var pictureFolder: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)
    }

    // MARK: Reminders

    static var reminders: [Reminder] {
        get { decode("reminders") ?? [] }
        set { encode(newValue, "reminders") }
    }

    static var todos: [Todo] {
        get { decode("todos") ?? [] }
        set { encode(newValue, "todos") }
    }

    static func save(_ r: Reminder) {
        var list = reminders.filter { $0.id != r.id }
        list.append(r)
        reminders = list
    }

    static func delete(id: Int64) {
        reminders = reminders.filter { $0.id != id }
    }

    /// Done: a one-off reminder goes away, a repeating one waits for its next time.
    static func complete(id: Int64, now: Date = Date()) {
        guard var r = reminders.first(where: { $0.id == id }) else { return }
        if r.repeats == .once {
            delete(id: id)
        } else {
            r.lastDone = now
            save(r)
        }
    }

    /// Reminders in the order they come up, due ones first.
    static func upcoming(now: Date = Date()) -> [Upcoming] {
        reminders.map { r in
            let o = r.occurrence(at: now)
            return Upcoming(reminder: r, date: o.date, due: o.due)
        }
        .sorted { a, b in a.due != b.due ? a.due : a.date < b.date }
    }

    static func toggleTodo(id: Int64) {
        todos = todos.map { t in t.id == id ? Todo(id: t.id, text: t.text, done: !t.done) : t }
    }

    // MARK: Look

    static var look: DuckLook {
        get {
            DuckLook(
                material: DuckMaterial(rawValue: defaults.string(forKey: "material") ?? "") ?? .GLOSSY,
                color: DuckColor(rawValue: defaults.string(forKey: "color") ?? "") ?? .SUNNY,
                accessory: DuckAccessory.named(defaults.string(forKey: "accessory"))
            )
        }
        set {
            defaults.set(newValue.material.rawValue, forKey: "material")
            defaults.set(newValue.color.rawValue, forKey: "color")
            defaults.set(newValue.accessory.id, forKey: "accessory")
        }
    }

    static func reloadWidgets() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    private static func decode<T: Decodable>(_ key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private static func encode<T: Encodable>(_ value: T, _ key: String) {
        defaults.set(try? JSONEncoder().encode(value), forKey: key)
    }
}

/// Ducky's face: sleepy at night, excited while a reminder is due.
enum DuckMood: String, CaseIterable {
    case normal, sleepy, excited

    static func at(_ date: Date, due: Bool) -> DuckMood {
        if due { return .excited }
        let hour = Calendar.current.component(.hour, from: date)
        return hour >= 23 || hour < 7 ? .sleepy : .normal
    }
}

func friendlyTime(_ date: Date, now: Date = Date()) -> String {
    let cal = Calendar.current
    let f = DateFormatter()
    f.locale = Locale(identifier: Lang.current == .ko ? "ko_KR" : "en_US")
    f.setLocalizedDateFormatFromTemplate(Lang.current == .ko ? "a h:mm" : "h:mm a")
    let time = f.string(from: date)
    if cal.isDate(date, inSameDayAs: now) { return time }
    if let tomorrow = cal.date(byAdding: .day, value: 1, to: now), cal.isDate(date, inSameDayAs: tomorrow) {
        return L("tomorrow") + " " + time
    }
    let d = DateFormatter()
    d.locale = f.locale
    d.setLocalizedDateFormatFromTemplate(Lang.current == .ko ? "MMMd E" : "E MMM d")
    return d.string(from: date) + " " + time
}
