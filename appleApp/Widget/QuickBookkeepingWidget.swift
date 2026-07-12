import SwiftUI
import WidgetKit

private let addTransactionURL = URL(string: "omniflow://add")!

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
                        .foregroundStyle(Color(red: 0.46, green: 0.40, blue: 0.62))
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
