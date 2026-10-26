import ActivityKit
import Foundation

/// Starts, refreshes and stops the Live Activity that shows Ducky on the Lock Screen.
enum LiveDuck {
    /// The in-app switch (ContentView keeps it under the same key).
    static var isOn: Bool { SharedStore.defaults.object(forKey: "liveDuck") as? Bool ?? true }

    static var isAllowed: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }

    /// Shows the current next reminder, starting the Live Activity if it isn't running.
    static func refresh() {
        let state = DuckActivityAttributes.ContentState.now()
        let running = Activity<DuckActivityAttributes>.activities.filter { $0.activityState == .active }
        guard isOn, isAllowed else {
            for a in running { Task { await a.end(nil, dismissalPolicy: .immediate) } }
            return
        }
        let content = ActivityContent(state: state, staleDate: nil)
        if let current = running.first {
            Task { await current.update(content) }
            for extra in running.dropFirst() { Task { await extra.end(nil, dismissalPolicy: .immediate) } }
        } else {
            _ = try? Activity.request(attributes: DuckActivityAttributes(), content: content)
        }
    }
}
