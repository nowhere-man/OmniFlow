import SwiftUI
import WidgetKit

private let addTransactionURL = URL(string: "omniflow://add")!
private let widgetAppGroup = "group.com.omniflow.shared"

struct QuickBookkeepingEntry: TimelineEntry {
    let date: Date
}

struct QuickBookkeepingProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickBookkeepingEntry {
        QuickBookkeepingEntry(date: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (QuickBookkeepingEntry) -> Void) {
        completion(QuickBookkeepingEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickBookkeepingEntry>) -> Void) {
        completion(Timeline(entries: [QuickBookkeepingEntry(date: Date())], policy: .never))
    }
}

struct QuickBookkeepingWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("themeColor", store: UserDefaults(suiteName: widgetAppGroup)) private var themeColor = "LAVENDER"

    var body: some View {
        widgetBackground {
            if family == .accessoryCircular {
                Image(systemName: "plus")
                    .font(.title2.weight(.bold))
                    .widgetAccentable()
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 32, weight: .semibold))
                        .foregroundStyle(accentColor)
                    Spacer(minLength: 0)
                    Text("快速记账")
                        .font(.headline)
                    Text("点击记录一笔收支")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .padding()
            }
        }
        .widgetURL(addTransactionURL)
    }

    private var accentColor: Color {
        let hex: UInt32
        switch (themeColor, colorScheme) {
        case ("MIST_BLUE", .dark): hex = 0x9CC3E5
        case ("MIST_BLUE", _): hex = 0x52779A
        case ("SAGE", .dark): hex = 0x9BC8A8
        case ("SAGE", _): hex = 0x4F765B
        case ("SOFT_CORAL", .dark): hex = 0xE7AAA4
        case ("SOFT_CORAL", _): hex = 0xA95850
        case ("WARM_AMBER", .dark): hex = 0xD8B778
        case ("WARM_AMBER", _): hex = 0x8A6532
        case ("GRAPHITE", .dark): hex = 0xF5F5F5
        case ("GRAPHITE", _): hex = 0x171717
        case (_, .dark): hex = 0xC2B5E5
        default: hex = 0x75679D
        }
        return Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    @ViewBuilder
    private func widgetBackground<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            content().containerBackground(.fill.tertiary, for: .widget)
        } else {
            content().background(.ultraThinMaterial)
        }
    }
}

@main
struct QuickBookkeepingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "QuickBookkeepingWidget", provider: QuickBookkeepingProvider()) { _ in
            QuickBookkeepingWidgetView()
        }
        .configurationDisplayName("快速记账")
        .description("从桌面或锁屏直接打开 OmniFlow 记账页面。")
        .supportedFamilies([.systemSmall, .accessoryCircular])
    }
}
