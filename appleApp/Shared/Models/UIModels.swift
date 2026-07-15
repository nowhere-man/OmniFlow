import Foundation

struct LedgerUI: Identifiable, Hashable {
    let id: String
    var name: String
    var coverKey: String?
}

struct AccountUI: Identifiable, Hashable {
    let id: String
    var name: String
    var balanceMinor: Int64
    var type: String
    var iconKey: String
    var cardNumber: String?
    var note: String?
    var includeInTotalAssets: Bool
}

let bundledIconKeys = [
    "banknote", "wallet-cards", "wallet", "landmark", "shopping-bag", "utensils",
    "bus", "wrench", "film", "heart-pulse", "plane", "car", "house", "smartphone",
    "shirt", "chart-line", "briefcase-business", "trophy", "gift", "play", "category",
]

struct CategoryIconOptionUI: Identifiable, Hashable {
    let key: String
    let label: String
    var id: String { key }
}

private let flatCategoryIconOptions = [
    CategoryIconOptionUI(key: "utensils", label: "餐饮"), CategoryIconOptionUI(key: "hot-beverage", label: "饮品"),
    CategoryIconOptionUI(key: "bread", label: "面包"), CategoryIconOptionUI(key: "pizza", label: "披萨"),
    CategoryIconOptionUI(key: "hamburger", label: "快餐"), CategoryIconOptionUI(key: "cake", label: "甜点"),
    CategoryIconOptionUI(key: "groceries", label: "买菜"), CategoryIconOptionUI(key: "bus", label: "公交"),
    CategoryIconOptionUI(key: "bicycle", label: "骑行"), CategoryIconOptionUI(key: "train", label: "火车"),
    CategoryIconOptionUI(key: "taxi", label: "打车"), CategoryIconOptionUI(key: "fuel", label: "加油"),
    CategoryIconOptionUI(key: "parking", label: "停车"), CategoryIconOptionUI(key: "shopping-bag", label: "购物"),
    CategoryIconOptionUI(key: "shirt", label: "服饰"), CategoryIconOptionUI(key: "beauty", label: "美妆"),
    CategoryIconOptionUI(key: "house", label: "住房"), CategoryIconOptionUI(key: "furniture", label: "家居"),
    CategoryIconOptionUI(key: "electricity", label: "电费"), CategoryIconOptionUI(key: "water", label: "水费"),
    CategoryIconOptionUI(key: "internet", label: "网络"), CategoryIconOptionUI(key: "phone", label: "话费"),
    CategoryIconOptionUI(key: "smartphone", label: "数码"), CategoryIconOptionUI(key: "education", label: "教育"),
    CategoryIconOptionUI(key: "books", label: "书籍"), CategoryIconOptionUI(key: "baby", label: "育儿"),
    CategoryIconOptionUI(key: "pet", label: "宠物"), CategoryIconOptionUI(key: "film", label: "电影"),
    CategoryIconOptionUI(key: "game", label: "游戏"), CategoryIconOptionUI(key: "music", label: "音乐"),
    CategoryIconOptionUI(key: "sports", label: "运动"), CategoryIconOptionUI(key: "fitness", label: "健身"),
    CategoryIconOptionUI(key: "camera", label: "摄影"), CategoryIconOptionUI(key: "party", label: "聚会"),
    CategoryIconOptionUI(key: "wine", label: "酒水"), CategoryIconOptionUI(key: "plane", label: "旅行"),
    CategoryIconOptionUI(key: "car", label: "汽车"), CategoryIconOptionUI(key: "heart-pulse", label: "医疗"),
    CategoryIconOptionUI(key: "medicine", label: "药品"), CategoryIconOptionUI(key: "insurance", label: "保险"),
    CategoryIconOptionUI(key: "wrench", label: "维修"), CategoryIconOptionUI(key: "donation", label: "公益"),
    CategoryIconOptionUI(key: "tax", label: "税费"), CategoryIconOptionUI(key: "refund", label: "退款"),
    CategoryIconOptionUI(key: "banknote", label: "工资"), CategoryIconOptionUI(key: "chart-line", label: "理财"),
    CategoryIconOptionUI(key: "briefcase-business", label: "工作"), CategoryIconOptionUI(key: "office", label: "办公"),
    CategoryIconOptionUI(key: "trophy", label: "奖金"), CategoryIconOptionUI(key: "gift", label: "礼物"),
]

let categoryIconOptions = flatCategoryIconOptions + flatCategoryIconOptions.map {
    CategoryIconOptionUI(key: "fluent-\($0.key)", label: "彩·\($0.label)")
}

private let categoryIconKeys = Set(categoryIconOptions.map(\.key))

func categoryIconAssetKey(_ key: String?) -> String {
    key.flatMap { categoryIconKeys.contains($0) ? $0 : nil } ?? "category"
}

struct CategoryUI: Identifiable, Hashable {
    let id: String
    var name: String
    var type: EntryType
    var parentID: String?
    var iconKey: String?
}

struct TagUI: Identifiable, Hashable { let id: String; var name: String }
struct RuleUI: Identifiable, Hashable {
    let id: String
    var name: String
    var conditionType: String
    var conditionValue: String
    var actionType: String
    var actionValue: String
    var priority: Int

    var conditionDisplayText: String {
        let value: String
        switch conditionType {
        case "TRANSACTION_TYPE": value = EntryType(rawValue: conditionValue)?.label ?? conditionValue
        case "TRANSACTION_SOURCE": value = transactionSourceDisplayName(conditionValue)
        default: value = conditionValue
        }
        return "\(conditionLabel)：\(value)"
    }

    var actionDisplayText: String {
        switch actionType {
        case "SET_CATEGORY": return "设置分类"
        case "SET_EXCLUDED": return "不计入收支"
        case "EXCLUDE": return "排除不入账"
        default: return "应用规则"
        }
    }

    private var conditionLabel: String {
        switch conditionType {
        case "NOTE_CONTAINS": return "备注包含"
        case "TRANSACTION_TYPE": return "收支类型"
        case "TRANSACTION_SOURCE": return "来源平台"
        default: return "匹配条件"
        }
    }
}
struct ReminderUI: Identifiable, Hashable {
    let id: String
    var name: String
    var type: String
    var amountMinor: Int64?
    var scheduleKind: String
    var paused: Bool
    var dayOfMonth: Int?
    var daysAfter: Int?
    var dayOfWeek: Int?
    var month: Int?

    var scheduleDisplayName: String {
        switch scheduleKind.components(separatedBy: ".").last {
        case "FIXED_REPAYMENT_DAY": return "每月 \(dayOfMonth ?? 1) 日"
        case "DAYS_AFTER_STATEMENT": return "账单日后 \(daysAfter ?? 0) 天"
        case "DAILY": return "每天"
        case "WEEKLY": return "每周星期 \(dayOfWeek ?? 1)"
        case "MONTHLY": return "每月 \(dayOfMonth ?? 1) 日"
        case "YEARLY": return "每年 \(month ?? 1) 月 \(dayOfMonth ?? 1) 日"
        default: return "自定义周期"
        }
    }
}
struct ImportItemUI: Identifiable, Hashable {
    let id: String
    var note: String
    var amountMinor: Int64
    var date: Date
    var source: String
    var type: EntryType?
    var categoryID: String?
    var accountID: String?
    var tags: [String]
    var excluded: Bool
    var skipped: Bool
    var duplicate: String

    var sourceDisplayName: String { transactionSourceDisplayName(source) }
    var duplicateDisplayName: String? {
        switch duplicate.components(separatedBy: ".").last {
        case "CONFIRMED": return "已确认重复"
        case "SUSPECTED": return "可能重复"
        default: return nil
        }
    }
}
struct CalendarDayUI: Identifiable, Hashable {
    let id: String
    var date: Date
    var expenseMinor: Int64
    var incomeMinor: Int64
    var displayAmountMinor: Int64?
    var displayIsIncome: Bool
}
struct AnalyticsRankingUI: Identifiable, Hashable {
    var transaction: TransactionUI
    var id: String { transaction.id }
    var primaryName: String { transaction.primaryCategoryName }
    var secondaryName: String? { transaction.categoryName == transaction.primaryCategoryName ? nil : transaction.categoryName }
    var iconKey: String? { transaction.categoryIconKey }
    var amount: Int64 { transaction.amountMinor }
    var date: Date { transaction.date }
    var note: String { transaction.note }
    var displayName: String { secondaryName.map { "\(primaryName) - \($0)" } ?? primaryName }
}
struct AnalyticsChartPointUI: Identifiable, Hashable {
    var start: Date
    var label: String
    var expenseMinor: Int64
    var incomeMinor: Int64
    var id: Date { start }
}
struct TagAnalysisUI: Identifiable, Hashable {
    let id: String
    var name: String
    var amountMinor: Int64
    var transactionCount: Int
}
enum AnalyticsGranularityUI: String, Hashable {
    case day = "DAY"
    case week = "WEEK"
    case month = "MONTH"
}
struct CategoryShareUI: Identifiable, Hashable { let id: String; var name: String; var iconKey: String?; var amount: Int64 }
struct CategoryBreakdownUI: Identifiable, Hashable {
    let id: String
    var name: String
    var iconKey: String?
    var amount: Int64
    var secondary: [CategoryShareUI]
}
struct BackupUI: Identifiable, Hashable { let id: String; var createdAt: String }

enum SearchStatus: Equatable {
    case idle
    case loading
    case loaded
    case failed(String)
}

enum DateDetailStatus: Equatable {
    case idle
    case loading
    case loaded
    case failed(String)
}

enum AnalyticsStatus: Equatable {
    case idle
    case loading
    case loaded
    case failed(String)
}

struct StatementMonthUI: Identifiable, Hashable {
    var month: Int
    var expenseMinor: Int64
    var incomeMinor: Int64
    var id: Int { month }
}
struct StatementTableUI: Identifiable, Hashable {
    var year: Int
    var months: [StatementMonthUI]
    var expenseMinor: Int64
    var incomeMinor: Int64
    var id: Int { year }
}

struct TransactionUI: Identifiable, Hashable {
    let id: String
    var ledgerID: String
    var ledgerName: String
    var accountID: String
    var accountName: String
    var categoryID: String
    var categoryName: String
    var primaryCategoryName: String
    var categoryIconKey: String?
    var amountMinor: Int64
    var type: EntryType
    var date: Date
    var note: String
    var excluded: Bool
    var source: String?
    var tagNames: [String] = []
    var categoryDisplayName: String

    var sourceDisplayName: String? {
        source.map(transactionSourceDisplayName)
    }
}

struct TransactionSourceOptionUI: Identifiable, Hashable {
    let id: String
    let label: String
}

let transactionSourceOptions = [
    TransactionSourceOptionUI(id: "MANUAL", label: "手动记录"),
    TransactionSourceOptionUI(id: "ALIPAY", label: "支付宝"),
    TransactionSourceOptionUI(id: "WECHAT", label: "微信"),
    TransactionSourceOptionUI(id: "JD", label: "京东"),
    TransactionSourceOptionUI(id: "MEITUAN", label: "美团"),
    TransactionSourceOptionUI(id: "CCB", label: "建设银行"),
]

func transactionSourceDisplayName(_ source: String) -> String {
    transactionSourceOptions.first { $0.id == source }?.label ?? source
}

struct TransactionRecordDetailUI {
    var primaryCategoryName: String
    var secondaryCategoryName: String?
    var tagNames: [String]
}

enum TransactionDisplayMode: String, CaseIterable, Identifiable {
    case list = "LIST"
    case card = "CARD"

    var id: String { rawValue }
    var label: String { self == .list ? "列表" : "卡片" }
}

enum AppleImportFormat: String, CaseIterable, Identifiable {
    case alipay = "ALIPAY"
    case wechat = "WECHAT"
    case jd = "JD"
    case meituan = "MEITUAN"
    case ccb = "CCB"
    case qingzi = "QINGZI"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .alipay: return "支付宝"
        case .wechat: return "微信"
        case .jd: return "京东"
        case .meituan: return "美团"
        case .ccb: return "建设银行"
        case .qingzi: return "青子记账"
        }
    }
}

enum EntryType: String, CaseIterable, Identifiable {
    case expense = "EXPENSE"
    case income = "INCOME"

    var id: String { rawValue }
    var label: String { self == .expense ? "支出" : "收入" }
}

enum MainDestination: String, CaseIterable, Identifiable {
    case home, analytics, transaction, search, more

    var id: String { rawValue }
    var title: String {
        switch self {
        case .home: return "首页"
        case .analytics: return "统计"
        case .transaction: return "记账"
        case .search: return "搜索"
        case .more: return "更多"
        }
    }
    var systemImage: String {
        switch self {
        case .home: return "house"
        case .analytics: return "chart.bar"
        case .transaction: return "plus.circle"
        case .search: return "magnifyingglass"
        case .more: return "ellipsis.circle"
        }
    }
}

extension Int64 {
    var rmb: String {
        let value = Decimal(self) / 100
        return value.formatted(.currency(code: "CNY"))
    }

}

extension String {
    var signedMoneyMinor: Int64? {
        guard let value = Decimal(string: trimmingCharacters(in: .whitespacesAndNewlines)) else { return nil }
        let scaled = NSDecimalNumber(decimal: value * 100)
        let minor = scaled.int64Value
        guard scaled.compare(NSDecimalNumber(value: minor)) == .orderedSame else { return nil }
        return minor
    }

    var moneyMinor: Int64? {
        guard let minor = signedMoneyMinor, minor >= 0 else { return nil }
        return minor
    }
}
