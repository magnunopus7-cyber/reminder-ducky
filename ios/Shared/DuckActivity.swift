import ActivityKit
import Foundation
import SwiftUI

/// Ducky on the Lock Screen and in the Dynamic Island, in full colour.
/// iPhone ends a Live Activity after about 8 to 12 hours; the app starts it again when opened.
struct DuckActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var mood: String
        var icon: String
        var text: String
        var date: Date?
        var due: Bool
    }
}

extension DuckActivityAttributes.ContentState {
    static func now() -> Self {
        let next = SharedStore.upcoming().first
        let mood = DuckMood.at(Date(), due: next?.due == true)
        return Self(mood: mood.rawValue, icon: next?.reminder.icon ?? "🦆",
                    text: next?.reminder.text ?? L("widget_none"), date: next?.date, due: next?.due == true)
    }
}

/// The full-colour banner at the bottom of the Lock Screen.
struct LockScreenDuck: View {
    let state: DuckActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: 10) {
            DuckImageView(mood: DuckMood(rawValue: state.mood) ?? .normal)
                .frame(width: 104, height: 104)
                .padding(.vertical, -10)
            VStack(alignment: .leading, spacing: 3) {
                Text(L("app_title")).font(.caption.weight(.bold)).foregroundStyle(DuckTheme.ink.opacity(0.7))
                Text("\(state.icon) \(state.text)")
                    .font(.headline).foregroundStyle(DuckTheme.ink).lineLimit(2)
                if state.due {
                    Text(L("due_now")).font(.subheadline.weight(.semibold)).foregroundStyle(.pink)
                } else if let date = state.date {
                    Text(friendlyTime(date)).font(.subheadline).foregroundStyle(DuckTheme.inkSoft)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }
}
