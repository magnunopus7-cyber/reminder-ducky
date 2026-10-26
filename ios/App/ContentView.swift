import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var duck = DuckController()
    @AppStorage("lang", store: SharedStore.defaults) private var lang = ""
    @AppStorage("tapQuack", store: SharedStore.defaults) private var tapQuack = true
    @State private var reminders: [Reminder] = SharedStore.reminders
    @State private var todos: [Todo] = SharedStore.todos
    @State private var look = SharedStore.look
    @State private var editing: EditTarget?
    @State private var notificationsOff = false
    @State private var newTodo = ""
    @State private var toast: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                header
                if notificationsOff { notificationBanner }
                remindersCard
                todoCard
                customizeCard
                widgetCard
            }
            .padding(.bottom, 30)
        }
        .background(DuckTheme.page.ignoresSafeArea())
        .ignoresSafeArea(edges: .top)
        .sheet(item: $editing) { target in
            ReminderEditor(original: target.reminder) { saved in
                SharedStore.save(saved)
                refresh()
                Notifications.sync()
            }
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(.white).shadow(radius: 6))
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onAppear {
            if !ProcessInfo.processInfo.arguments.contains("-uiPreview") {
                Notifications.requestPermission { ok in notificationsOff = !ok }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .duckyReminderArrived)) { _ in
            duck.excite()
            refresh()
        }
        .onReceive(NotificationCenter.default.publisher(for: .duckyRemindersChanged)) { _ in refresh() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            refresh()
            Notifications.isAllowed { notificationsOff = !$0 }
        }
        .onChange(of: lang) { _, _ in
            Notifications.registerActions()
            Notifications.sync()
        }
        .onOpenURL { _ in refresh() }
    }

    private func refresh() {
        reminders = SharedStore.reminders
        todos = SharedStore.todos
    }

    private func showToast(_ text: String) {
        withAnimation { toast = text }
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { withAnimation { toast = nil } }
    }

    // MARK: Header

    private var header: some View {
        VStack(spacing: 4) {
            DuckView(controller: duck)
                .frame(width: 190, height: 190)
                .contentShape(Rectangle())
                .onTapGesture { duck.tap(quack: tapQuack) }
                .accessibilityLabel(Text(Lang.current == .ko ? "덕희예요. 눌러서 인사해 보세요." : "Ducky. Tap to say hi."))
            Text(L("app_title")).font(.system(size: 28, weight: .heavy, design: .rounded)).foregroundStyle(.white)
            Text(L("tagline")).font(.headline).foregroundStyle(.white.opacity(0.95))
            Text(L("header_blurb"))
                .font(.footnote).multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.9))
                .padding(.horizontal, 28)
        }
        .padding(.top, 50)
        .padding(.bottom, 20)
        .frame(maxWidth: .infinity)
        .background(DuckTheme.sky)
        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 28, bottomTrailingRadius: 28))
        .id(lang)
    }

    private var notificationBanner: some View {
        Button {
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        } label: {
            Text(L("notif_off"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(DuckTheme.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 18).fill(Color(red: 1, green: 0.91, blue: 0.94)))
        }
        .padding(.horizontal, 16)
    }

    // MARK: Reminders

    private var remindersCard: some View {
        FoldCard(title: L("your_reminders"), key: "reminders") {
            Button(L("add")) { editing = EditTarget(reminder: nil) }
                .font(.subheadline.weight(.bold))
                .buttonStyle(.borderedProminent)
                .tint(DuckTheme.skyTop)
                .clipShape(Capsule())
        } content: {
            TimelineView(.periodic(from: .now, by: 30)) { context in
                let list = SharedStore.upcoming(now: context.date)
                VStack(spacing: 8) {
                    if list.isEmpty {
                        Text(L("empty")).font(.subheadline).foregroundStyle(DuckTheme.inkSoft)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    ForEach(list) { item in
                        reminderRow(item.reminder, date: item.date, due: item.due)
                    }
                    Button(L("try_it")) {
                        let r = Reminder(id: Reminder.newId(), text: L("test_text"), time: Date().addingTimeInterval(60))
                        SharedStore.save(r)
                        refresh()
                        Notifications.sync()
                        showToast(L("test_toast"))
                    }
                    .font(.footnote.weight(.semibold))
                    .padding(.top, 4)
                }
                .id(reminders)
            }
        }
    }

    private func reminderRow(_ r: Reminder, date: Date, due: Bool) -> some View {
        HStack(spacing: 10) {
            Text(r.icon).font(.title2)
                .frame(width: 42, height: 42)
                .background(Circle().fill(Color(red: 1, green: 0.914, blue: 0.937)))
            VStack(alignment: .leading, spacing: 2) {
                Text(r.text).font(.body.weight(.semibold)).foregroundStyle(DuckTheme.ink).lineLimit(2)
                Text(due ? L("due_now") : friendlyTime(date) + (r.repeats == .once ? "" : " · " + r.repeats.label))
                    .font(.caption).foregroundStyle(due ? Color.pink : DuckTheme.inkSoft)
            }
            Spacer(minLength: 4)
            if due {
                Button(L("done_button")) {
                    SharedStore.complete(id: r.id)
                    refresh()
                    Notifications.sync()
                }
                .font(.caption.weight(.bold))
                .buttonStyle(.bordered)
                .tint(.green)
            }
            Button {
                SharedStore.delete(id: r.id)
                refresh()
                Notifications.sync()
            } label: {
                Image(systemName: "trash").foregroundStyle(DuckTheme.inkSoft)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(L("delete")))
        }
        .contentShape(Rectangle())
        .onTapGesture { editing = EditTarget(reminder: r) }
    }

    // MARK: To-do

    private var todoCard: some View {
        FoldCard(title: L("todo_title"), key: "todo") {
            EmptyView()
        } content: {
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    TextField(L("todo_hint"), text: $newTodo)
                        .submitLabel(.done)
                        .onSubmit(addTodo)
                        .padding(.horizontal, 16)
                        .frame(height: 48)
                        .background(Capsule().fill(DuckTheme.page))
                    Button(L("add"), action: addTodo)
                        .font(.subheadline.weight(.bold))
                        .frame(height: 48)
                        .padding(.horizontal, 16)
                        .background(Capsule().fill(DuckTheme.skyTop))
                        .foregroundStyle(.white)
                }
                if todos.isEmpty {
                    Text(L("todo_empty")).font(.subheadline).foregroundStyle(DuckTheme.inkSoft)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                ForEach(todos) { t in
                    HStack {
                        Button {
                            SharedStore.toggleTodo(id: t.id)
                            refresh()
                            SharedStore.reloadWidgets()
                        } label: {
                            HStack {
                                Image(systemName: t.done ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(t.done ? .green : DuckTheme.inkSoft)
                                    .font(.title3)
                                Text(t.text).strikethrough(t.done).foregroundStyle(DuckTheme.ink)
                                Spacer()
                            }
                        }
                        .buttonStyle(.plain)
                        Button {
                            SharedStore.todos = SharedStore.todos.filter { $0.id != t.id }
                            refresh()
                            SharedStore.reloadWidgets()
                        } label: {
                            Image(systemName: "trash").foregroundStyle(DuckTheme.inkSoft)
                        }
                        .buttonStyle(.plain)
                    }
                }
                if todos.contains(where: \.done) {
                    Button(L("todo_clear")) {
                        SharedStore.todos = SharedStore.todos.filter { !$0.done }
                        refresh()
                        SharedStore.reloadWidgets()
                    }
                    .font(.footnote.weight(.semibold))
                }
            }
        }
    }

    private func addTodo() {
        let text = newTodo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        SharedStore.todos = SharedStore.todos + [Todo(id: Reminder.newId(), text: text)]
        newTodo = ""
        refresh()
        SharedStore.reloadWidgets()
    }

    // MARK: Customize

    private func setLook(_ l: DuckLook) {
        look = l
        SharedStore.look = l
        duck.apply(l)
    }

    private var customizeCard: some View {
        VStack(spacing: 14) {
            FoldCard(title: L("skins_title"), key: "skins") {
                EmptyView()
            } content: {
                ThumbRow(items: DuckColor.allCases.map { ThumbItem(id: $0.rawValue, image: $0.thumb, label: $0.label) },
                         selected: look.color.rawValue) { id in
                    var l = look
                    l.color = DuckColor(rawValue: id) ?? .SUNNY
                    setLook(l)
                }
            }
            FoldCard(title: L("customize_title"), key: "customize") {
                EmptyView()
            } content: {
                VStack(alignment: .leading, spacing: 12) {
                    label(L("label_material"))
                    ThumbRow(items: DuckMaterial.allCases.map { ThumbItem(id: $0.rawValue, image: $0.thumb, label: $0.label) },
                             selected: look.material.rawValue) { id in
                        var l = look
                        l.material = DuckMaterial(rawValue: id) ?? .GLOSSY
                        setLook(l)
                    }
                    label(L("label_accessory"))
                    ThumbGrid(items: DuckAccessory.all.map { ThumbItem(id: $0.id, image: $0.thumb, label: $0.label) },
                              selected: look.accessory.id) { id in
                        var l = look
                        l.accessory = DuckAccessory.named(id)
                        setLook(l)
                    }
                    label(L("label_language"))
                    Picker(L("label_language"), selection: Binding(get: { Lang.current.rawValue }, set: { lang = $0 })) {
                        ForEach(Lang.allCases) { Text($0.label).tag($0.rawValue) }
                    }
                    .pickerStyle(.segmented)
                    Toggle(L("tap_quack"), isOn: $tapQuack)
                        .font(.subheadline)
                        .tint(DuckTheme.skyTop)
                }
            }
        }
    }

    private func label(_ text: String) -> some View {
        Text(text).font(.subheadline.weight(.bold)).foregroundStyle(DuckTheme.ink)
    }

    // MARK: Widget help

    private var widgetCard: some View {
        FoldCard(title: L("widget_title"), key: "widget") {
            EmptyView()
        } content: {
            Text(L("widget_help"))
                .font(.subheadline)
                .foregroundStyle(DuckTheme.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct EditTarget: Identifiable {
    let id = UUID()
    let reminder: Reminder?
}

/// A white card that folds open and closed, remembering its state.
struct FoldCard<Accessory: View, Content: View>: View {
    let title: String
    let key: String
    @ViewBuilder let accessory: () -> Accessory
    @ViewBuilder let content: () -> Content
    @AppStorage private var open: Bool

    init(title: String, key: String, @ViewBuilder accessory: @escaping () -> Accessory, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.key = key
        self.accessory = accessory
        self.content = content
        _open = AppStorage(wrappedValue: true, "open_" + key, store: SharedStore.defaults)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { open.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Text(title).font(.headline).foregroundStyle(DuckTheme.ink)
                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(DuckTheme.inkSoft)
                            .rotationEffect(.degrees(open ? 0 : -90))
                    }
                }
                .buttonStyle(.plain)
                Spacer()
                if open { accessory() }
            }
            if open { content() }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 24).fill(.white))
        .padding(.horizontal, 16)
    }
}

struct ThumbItem: Identifiable {
    let id: String
    let image: String
    let label: String
}

private func thumbImage(_ name: String) -> Image {
    if let ui = UIImage(named: name + ".webp") ?? UIImage(named: name) { return Image(uiImage: ui) }
    return Image(systemName: "questionmark.circle")
}

struct ThumbCell: View {
    let item: ThumbItem
    let selected: Bool
    var body: some View {
        VStack(spacing: 4) {
            thumbImage(item.image)
                .resizable().scaledToFit()
                .frame(width: 64, height: 64)
                .background(Circle().fill(DuckTheme.page))
                .overlay(Circle().stroke(selected ? DuckTheme.skyTop : .clear, lineWidth: 3))
            Text(item.label).font(.caption2.weight(selected ? .bold : .regular))
                .foregroundStyle(DuckTheme.ink).lineLimit(1)
        }
        .frame(width: 74)
    }
}

/// A sideways row of pictures, like the Android picture cards.
struct ThumbRow: View {
    let items: [ThumbItem]
    let selected: String
    let pick: (String) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(items) { item in
                    Button { pick(item.id) } label: { ThumbCell(item: item, selected: item.id == selected) }
                        .buttonStyle(.plain)
                }
            }
        }
    }
}

struct ThumbGrid: View {
    let items: [ThumbItem]
    let selected: String
    let pick: (String) -> Void
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 74), spacing: 4)], spacing: 8) {
            ForEach(items) { item in
                Button { pick(item.id) } label: { ThumbCell(item: item, selected: item.id == selected) }
                    .buttonStyle(.plain)
            }
        }
    }
}
