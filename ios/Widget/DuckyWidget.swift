import SwiftUI
import WidgetKit

struct DuckProvider: TimelineProvider {
    func placeholder(in context: Context) -> DuckEntry {
        DuckEntry(date: Date(), mood: .normal, items: [], todos: [])
    }

    func getSnapshot(in context: Context, completion: @escaping (DuckEntry) -> Void) {
        completion(DuckEntry.make(at: Date()))
    }

    /// One entry now, plus one at every moment the widget should change by itself:
    /// when a reminder becomes due or stops being due, and when Ducky falls asleep or wakes up.
    func getTimeline(in context: Context, completion: @escaping (Timeline<DuckEntry>) -> Void) {
        let now = Date()
        let horizon = now.addingTimeInterval(24 * 60 * 60)
        var moments: Set<Date> = [now]
        for r in SharedStore.reminders {
            var t = r.occurrence(at: now).date
            for _ in 0..<4 where t <= horizon {
                if t > now { moments.insert(t) }
                let end = t.addingTimeInterval(Reminder.dueWindow + 1)
                if end > now && end <= horizon { moments.insert(end) }
                guard r.repeats != .once else { break }
                t = r.repeats.step(t, by: 1)
            }
        }
        let cal = Calendar.current
        for hour in [7, 23] {
            if let d = cal.nextDate(after: now, matching: DateComponents(hour: hour, minute: 0), matchingPolicy: .nextTime) {
                moments.insert(d)
            }
        }
        let entries = moments.sorted().prefix(40).map { DuckEntry.make(at: $0) }
        completion(Timeline(entries: Array(entries), policy: .after(horizon)))
    }
}

struct DuckyWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DuckyWidget", provider: DuckProvider()) { entry in
            DuckyWidgetEntryView(entry: entry)
        }
        .configurationDisplayName(L("widget_name"))
        .description(L("widget_desc"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge,
                            .accessoryRectangular, .accessoryInline, .accessoryCircular])
    }
}

struct DuckyWidgetEntryView: View {
    let entry: DuckEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        DuckWidgetView(entry: entry, family: family)
            .containerBackground(for: .widget) {
                if family == .systemSmall || family == .systemMedium || family == .systemLarge {
                    DuckTheme.sky
                } else {
                    Color.clear
                }
            }
            .widgetURL(URL(string: "ducky://open"))
    }
}

@main
struct DuckyWidgets: WidgetBundle {
    var body: some Widget {
        DuckyWidget()
        DuckLiveActivity()
    }
}
