import ActivityKit
import SwiftUI
import WidgetKit

struct DuckLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: DuckActivityAttributes.self) { context in
            LockScreenDuck(state: context.state)
                .activityBackgroundTint(Color(red: 0.43, green: 0.69, blue: 1).opacity(0.55))
                .activitySystemActionForegroundColor(DuckTheme.ink)
        } dynamicIsland: { context in
            let state = context.state
            let mood = DuckMood(rawValue: state.mood) ?? .normal
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    DuckImageView(mood: mood).frame(width: 70, height: 70)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(state.icon) \(state.text)").font(.headline).lineLimit(2)
                        if state.due {
                            Text(L("due_now")).font(.subheadline).foregroundStyle(.pink)
                        } else if let date = state.date {
                            Text(friendlyTime(date)).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            } compactLeading: {
                DuckImageView(mood: mood).frame(width: 26, height: 26)
            } compactTrailing: {
                if let date = state.date, !state.due {
                    Text(date, style: .time).font(.caption2).foregroundStyle(.yellow)
                } else {
                    Text(state.icon).font(.caption2)
                }
            } minimal: {
                DuckImageView(mood: mood).frame(width: 24, height: 24)
            }
            .widgetURL(URL(string: "ducky://open"))
        }
    }
}
