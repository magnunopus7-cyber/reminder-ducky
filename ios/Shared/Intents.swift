import AppIntents
import Foundation

/// The ✅ button on the widget.
struct CompleteReminderIntent: AppIntent {
    static let title: LocalizedStringResource = "Mark reminder done"
    static let isDiscoverable = false

    @Parameter(title: "Reminder")
    var reminderID: String

    init() {}
    init(id: Int64) { reminderID = String(id) }

    func perform() async throws -> some IntentResult {
        if let id = Int64(reminderID) { SharedStore.complete(id: id) }
        return .result()
    }
}

/// Ticking a to-do on the widget.
struct ToggleTodoIntent: AppIntent {
    static let title: LocalizedStringResource = "Tick a to-do"
    static let isDiscoverable = false

    @Parameter(title: "To-do")
    var todoID: String

    init() {}
    init(id: Int64) { todoID = String(id) }

    func perform() async throws -> some IntentResult {
        if let id = Int64(todoID) { SharedStore.toggleTodo(id: id) }
        return .result()
    }
}
