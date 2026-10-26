import SwiftUI

struct ReminderEditor: View {
    let original: Reminder?
    let onSave: (Reminder) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var text: String
    @State private var time: Date
    @State private var repeats: Repeats
    @State private var icon: String
    @State private var error: String?

    init(original: Reminder?, onSave: @escaping (Reminder) -> Void) {
        self.original = original
        self.onSave = onSave
        _text = State(initialValue: original?.text ?? "")
        _time = State(initialValue: original?.time ?? Date().addingTimeInterval(30 * 60))
        _repeats = State(initialValue: original?.repeats ?? .once)
        _icon = State(initialValue: original?.icon ?? Reminder.defaultIcon)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(L("hint_text"), text: $text, axis: .vertical)
                }
                Section(L("icon")) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Reminder.icons, id: \.self) { i in
                                Button { icon = i } label: {
                                    Text(i).font(.title2)
                                        .frame(width: 44, height: 44)
                                        .background(Circle().fill(i == icon ? DuckTheme.skyBottom : Color.clear))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                Section(L("when")) {
                    DatePicker(L("when"), selection: $time)
                        .labelsHidden()
                        .datePickerStyle(.graphical)
                    Picker(L("when"), selection: $repeats) {
                        ForEach(Repeats.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                if let error {
                    Section { Text(error).foregroundStyle(.pink) }
                }
            }
            .environment(\.locale, Locale(identifier: Lang.current == .ko ? "ko_KR" : "en_US"))
            .navigationTitle(original == nil ? L("new_reminder") : L("edit_reminder"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(L("save"), action: save).bold() }
            }
        }
    }

    private func save() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { error = L("err_empty"); return }
        // Seconds are dropped so the reminder goes off right on the minute.
        let cal = Calendar.current
        let whole = cal.date(from: cal.dateComponents([.year, .month, .day, .hour, .minute], from: time)) ?? time
        guard repeats != .once || whole > Date() else { error = L("err_past"); return }
        var r = original ?? Reminder(id: Reminder.newId(), text: trimmed, time: whole)
        r.text = trimmed
        r.time = whole
        r.repeats = repeats
        r.icon = icon
        r.lastDone = nil
        onSave(r)
        dismiss()
    }
}
