import Foundation
import UserNotifications

/// Turns the saved reminders into iPhone notifications that quack.
enum Notifications {
    static let category = "REMINDER"
    static let doneAction = "DONE"
    static let snoozeAction = "SNOOZE"
    static let snoozeMinutes = 10
    private static let prefix = "r-"

    static func setUp(delegate: UNUserNotificationCenterDelegate) {
        let center = UNUserNotificationCenter.current()
        center.delegate = delegate
        registerActions()
    }

    /// The Done and Snooze buttons, in the current language.
    static func registerActions() {
        let done = UNNotificationAction(identifier: doneAction, title: L("done"), options: [])
        let snooze = UNNotificationAction(identifier: snoozeAction, title: L("snooze"), options: [])
        let cat = UNNotificationCategory(identifier: category, actions: [done, snooze], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([cat])
    }

    static func requestPermission(_ done: @escaping (Bool) -> Void = { _ in }) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            DispatchQueue.main.async { done(ok) }
        }
    }

    static func isAllowed(_ done: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { s in
            DispatchQueue.main.async { done(s.authorizationStatus == .authorized || s.authorizationStatus == .provisional) }
        }
    }

    /// Replaces all scheduled reminder notifications with the saved list.
    static func sync() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { pending in
            let old = pending.map(\.identifier).filter { $0.hasPrefix(prefix) }
            center.removePendingNotificationRequests(withIdentifiers: old)
            let now = Date()
            for r in SharedStore.reminders {
                if let req = request(for: r, now: now) { center.add(req) }
            }
        }
        SharedStore.reloadWidgets()
        LiveDuck.refresh()
    }

    private static func content(for r: Reminder) -> UNMutableNotificationContent {
        let c = UNMutableNotificationContent()
        c.title = String(format: L("notif_title"), r.icon)
        c.body = r.text
        c.sound = UNNotificationSound(named: UNNotificationSoundName("alarm_quack.caf"))
        c.categoryIdentifier = category
        c.userInfo = ["id": NSNumber(value: r.id)]
        return c
    }

    private static func request(for r: Reminder, now: Date) -> UNNotificationRequest? {
        let cal = Calendar.current
        let trigger: UNCalendarNotificationTrigger
        switch r.repeats {
        case .once:
            guard r.time > now else { return nil }
            let comps = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: r.time)
            trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        case .daily:
            trigger = UNCalendarNotificationTrigger(dateMatching: cal.dateComponents([.hour, .minute], from: r.time), repeats: true)
        case .weekly:
            trigger = UNCalendarNotificationTrigger(dateMatching: cal.dateComponents([.weekday, .hour, .minute], from: r.time), repeats: true)
        }
        return UNNotificationRequest(identifier: prefix + String(r.id), content: content(for: r), trigger: trigger)
    }

    /// Quacks again in 10 minutes. A one-off reminder moves to the new time.
    static func snooze(id: Int64) {
        guard var r = SharedStore.reminders.first(where: { $0.id == id }) else { return }
        let later = Date().addingTimeInterval(TimeInterval(snoozeMinutes * 60))
        if r.repeats == .once {
            r.time = later
            SharedStore.save(r)
            sync()
        } else {
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(snoozeMinutes * 60), repeats: false)
            let req = UNNotificationRequest(identifier: "snooze-\(id)", content: content(for: r), trigger: trigger)
            UNUserNotificationCenter.current().add(req)
        }
    }
}
