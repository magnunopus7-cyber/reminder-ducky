import Foundation

// The same materials, colours and accessories as the Android app (DuckLook.kt).
// Codes and colours feed the shared shader, duck.frag.

enum DuckMaterial: String, CaseIterable, Identifiable {
    case GLOSSY, METALLIC, CRYSTAL, FLUFFY
    var id: String { rawValue }
    var code: Double { Double(Self.allCases.firstIndex(of: self)!) }
    var thumb: String { "thumb_mat_" + rawValue.lowercased() }
    var label: String {
        switch self {
        case .GLOSSY: return Lang.current == .ko ? "유광" : "Glossy"
        case .METALLIC: return Lang.current == .ko ? "메탈" : "Metallic"
        case .CRYSTAL: return Lang.current == .ko ? "크리스털" : "Crystal"
        case .FLUFFY: return Lang.current == .ko ? "복슬복슬" : "Fluffy"
        }
    }
}

enum DuckColor: String, CaseIterable, Identifiable {
    case SUNNY, BLOSSOM, SKY, MINT, LAVENDER, SNOW, GOLD, SILVER
    var id: String { rawValue }
    var thumb: String { "thumb_col_" + rawValue.lowercased() }
    var body: Int {
        switch self {
        case .SUNNY: return 0xFFD01A
        case .BLOSSOM: return 0xFFA6BA
        case .SKY: return 0x6B9EF2
        case .MINT: return 0x8FE0BE
        case .LAVENDER: return 0xC2A6FF
        case .SNOW: return 0xF2F5FF
        case .GOLD: return 0xFFB838
        case .SILVER: return 0xEBEBF2
        }
    }
    var label: String {
        let names: [DuckColor: (String, String)] = [
            .SUNNY: ("Sunny", "햇살"), .BLOSSOM: ("Blossom", "벚꽃"), .SKY: ("Sky", "하늘"), .MINT: ("Mint", "민트"),
            .LAVENDER: ("Lavender", "라벤더"), .SNOW: ("Snow", "눈"), .GOLD: ("Gold", "골드"), .SILVER: ("Silver", "실버"),
        ]
        let n = names[self]!
        return Lang.current == .ko ? n.1 : n.0
    }
}

struct DuckAccessory: Identifiable, Hashable {
    let id: String
    let en: String
    let ko: String
    let code: Int
    let a: Int
    let b: Int
    var thumb: String { "thumb_acc_" + id.lowercased() }
    var label: String { Lang.current == .ko ? ko : en }

    static let none = DuckAccessory(id: "NONE", en: "None", ko: "없음", code: 0, a: 0xFFFFFF, b: 0xFFFFFF)

    static let all: [DuckAccessory] = [
        none,
        DuckAccessory(id: "FLOWER", en: "Flower", ko: "꽃", code: 1, a: 0xFFEBF2, b: 0xFFD24D),
        DuckAccessory(id: "BEANIE", en: "Beanie", ko: "비니", code: 2, a: 0x4D80E6, b: 0x99C2FF),
        DuckAccessory(id: "PARTY", en: "Party hat", ko: "파티 모자", code: 3, a: 0xFF73A6, b: 0x73CCFF),
        DuckAccessory(id: "NIGHTCAP", en: "Nightcap", ko: "수면모자", code: 4, a: 0x6FA8F0, b: 0xFFFFFF),
        DuckAccessory(id: "SPROUT", en: "Sprout", ko: "새싹", code: 5, a: 0x6CC24A, b: 0x4E9F3A),
        DuckAccessory(id: "SHADES", en: "Heart shades", ko: "하트 선글라스", code: 6, a: 0xFF8FB0, b: 0x2B2530),
        DuckAccessory(id: "HEADPHONES", en: "Headphones", ko: "헤드폰", code: 7, a: 0x4A90E2, b: 0xF2F6FF),
        DuckAccessory(id: "CAPE", en: "Cape", ko: "망토", code: 8, a: 0xF0483E, b: 0xE23B32),
        DuckAccessory(id: "CAP", en: "Cap", ko: "모자", code: 9, a: 0x4A90E2, b: 0xFFD45A),
        DuckAccessory(id: "FROG", en: "Froggy", ko: "개구리", code: 10, a: 0x6CC24A, b: 0xFFFFFF),
        DuckAccessory(id: "BEAR", en: "Teddy", ko: "곰돌이", code: 11, a: 0xF5E9D0, b: 0xF2C14E),
        DuckAccessory(id: "SUNHAT", en: "Sunhat", ko: "밀짚모자", code: 12, a: 0xE8C88A, b: 0xFFD24D),
        DuckAccessory(id: "BANDANA", en: "Bandana", ko: "반다나", code: 13, a: 0xF06A3A, b: 0xFFFFFF),
        DuckAccessory(id: "SNORKEL", en: "Snorkel", ko: "스노클", code: 14, a: 0xFF7FB3, b: 0x8FB8F0),
        DuckAccessory(id: "BACKPACK", en: "Backpack", ko: "책가방", code: 15, a: 0x4A90E2, b: 0x8FC1FF),
        DuckAccessory(id: "SAILOR", en: "Sailor", ko: "세일러", code: 16, a: 0x4A90E2, b: 0xFFD45A),
        DuckAccessory(id: "DUCKLING", en: "Duckling friend", ko: "아기 오리", code: 17, a: 0xFFD01A, b: 0xFF801F),
        DuckAccessory(id: "HEART", en: "Love", ko: "사랑해", code: 18, a: 0xFF8FB0, b: 0xFFFFFF),
        DuckAccessory(id: "BATH", en: "Bubble bath", ko: "거품 목욕", code: 19, a: 0xA8D8F0, b: 0xFFFFFF),
        DuckAccessory(id: "BOOKWORM", en: "Bookworm", ko: "책벌레", code: 20, a: 0x4A90E2, b: 0x8A6A45),
        DuckAccessory(id: "CLOUD", en: "Cloud nap", ko: "구름 낮잠", code: 21, a: 0xFFFFFF, b: 0xFFFFFF),
        DuckAccessory(id: "PARTY_BLUE", en: "Blue party hat", ko: "파란 고깔모자", code: 3, a: 0x4A90E2, b: 0xBFE0FF),
    ]

    static func named(_ id: String?) -> DuckAccessory { all.first { $0.id == id } ?? none }
}

struct DuckLook: Equatable {
    var material: DuckMaterial = .GLOSSY
    var color: DuckColor = .SUNNY
    var accessory: DuckAccessory = .none

    var beak: Int { material == .METALLIC ? 0xFF731F : 0xFF801F }

    /// Changes whenever the duck would look different (used to refresh widget pictures).
    var key: String { "\(material.rawValue)-\(color.rawValue)-\(accessory.id)-v1" }

    /// The look as a JavaScript object for duck.html.
    var js: String {
        "{mat:\(Int(material.code)),body:\(color.body),head:\(color.body),beak:\(beak),acc:\(accessory.code),accA:\(accessory.a),accB:\(accessory.b)}"
    }
}
