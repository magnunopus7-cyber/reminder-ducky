import Foundation

/// The app speaks English or Korean, chosen inside the app (like on Android).
enum Lang: String, CaseIterable, Identifiable {
    case en, ko
    var id: String { rawValue }
    var label: String { self == .en ? "English" : "한국어" }

    static var current: Lang {
        if let saved = SharedStore.defaults.string(forKey: "lang"), let l = Lang(rawValue: saved) { return l }
        return Locale.preferredLanguages.first?.hasPrefix("ko") == true ? .ko : .en
    }
}

/// Looks up a phrase in the current language: `L("save")`.
func L(_ key: String) -> String {
    let pair = phrases[key] ?? (key, key)
    return Lang.current == .ko ? pair.1 : pair.0
}

private let phrases: [String: (String, String)] = [
    "app_title": ("Ducky 💛", "덕희 💛"),
    "tagline": ("Your little reminder buddy", "나의 작은 알림 친구"),
    "header_blurb": ("Lives on your Home Screen, keeps you on track and makes every day a little more joyful. Tap me!",
                     "홈 화면에 살면서 할 일을 챙겨 주고 하루를 조금 더 즐겁게 만들어 줘요. 저를 눌러 보세요!"),
    "your_reminders": ("Your reminders", "내 알림"),
    "add": ("+ Add", "+ 추가"),
    "empty": ("No reminders yet. Tap + Add.", "아직 알림이 없어요. + 추가를 눌러 보세요."),
    "hint_text": ("What to remind you", "알릴 내용"),
    "try_it": ("Try it: remind me in 1 minute", "테스트: 1분 뒤에 알려 줘"),
    "test_text": ("This is your test reminder. Quack!", "테스트 알림이에요. 꽥!"),
    "test_toast": ("Ducky will quack in about a minute. You can leave the app.", "1분쯤 뒤에 덕희가 꽥 하고 알려 줄 거예요. 앱을 나가도 괜찮아요."),
    "new_reminder": ("New reminder", "새 알림"),
    "edit_reminder": ("Edit reminder", "알림 수정"),
    "save": ("Save", "저장"),
    "cancel": ("Cancel", "취소"),
    "delete": ("Delete", "삭제"),
    "done": ("Done", "완료"),
    "done_button": ("✅ Done", "✅ 완료"),
    "snooze": ("Snooze 10 min", "10분 뒤에"),
    "snooze_button": ("😴 Snooze 10 min", "😴 10분 뒤에"),
    "due_now": ("Due now", "지금 할 시간"),
    "err_empty": ("Please type what Ducky should remind you about.", "덕희가 알려 줄 내용을 입력해 주세요."),
    "err_past": ("That time has already passed. Please pick a later time.", "이미 지난 시간이에요. 더 나중 시간을 골라 주세요."),
    "when": ("When", "언제"),
    "icon": ("Icon", "아이콘"),
    "repeat_once": ("Once", "한 번"),
    "repeat_daily": ("Every day", "매일"),
    "repeat_weekly": ("Every week", "매주"),
    "notif_title": ("%@ Ducky reminder", "%@ 덕희 알림"),
    "notif_off": ("Notifications are off, so Ducky can't quack. Tap here to turn them on in Settings.",
                  "알림이 꺼져 있어서 덕희가 꽥 할 수 없어요. 여기를 눌러 설정에서 켜 주세요."),
    "todo_title": ("To-do list", "할 일 목록"),
    "todo_hint": ("To do", "할 일"),
    "todo_empty": ("Nothing to do. Quack.", "할 일이 없어요. 꽥."),
    "todo_clear": ("Clear ticked items", "완료한 항목 지우기"),
    "customize_title": ("Customize Ducky", "덕희 꾸미기"),
    "skins_title": ("Collectible Ducky skins", "수집용 덕희 스킨"),
    "label_material": ("Material", "재질"),
    "label_colour": ("Colour", "색상"),
    "label_accessory": ("Accessory", "액세서리"),
    "label_language": ("Language", "언어"),
    "tap_quack": ("Quack when I tap Ducky", "덕희를 누르면 꽥 소리 내기"),
    "widget_title": ("Put Ducky on your Home Screen", "홈 화면에 덕희 놓기"),
    "widget_help": ("1. Touch and hold an empty spot on your Home Screen until the apps jiggle.\n2. Tap Edit (or +) at the top, then Add Widget.\n3. Search for Ducky, pick a size and tap Add Widget.\n\nYou can also add Ducky to your Lock Screen.",
                    "1. 홈 화면의 빈 곳을 길게 눌러 앱들이 흔들리게 해요.\n2. 위쪽의 편집(또는 +)을 누르고 위젯 추가를 눌러요.\n3. 덕희를 검색해서 크기를 고르고 위젯 추가를 눌러요.\n\n잠금 화면에도 덕희를 놓을 수 있어요."),
    "widget_next": ("Next", "다음"),
    "widget_none": ("No reminders. Quack!", "알림이 없어요. 꽥!"),
    "widget_name": ("Ducky", "덕희"),
    "widget_desc": ("Ducky and your next reminders.", "덕희와 다가오는 알림."),
    "today": ("Today", "오늘"),
    "tomorrow": ("Tomorrow", "내일"),
]
