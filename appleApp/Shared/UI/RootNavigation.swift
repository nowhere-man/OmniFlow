import SwiftUI

#if os(iOS)
struct PhoneRootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        TabView(selection: $store.destination) {
            NavigationStack { HomeView() }
                .tabItem { Label("首页", systemImage: "house") }
                .tag(MainDestination.home)
            NavigationStack { AnalyticsView() }
                .tabItem { Label("统计", systemImage: "chart.bar") }
                .tag(MainDestination.analytics)
            Color.clear
                .tabItem { Label("记账", systemImage: "plus") }
                .tag(MainDestination.transaction)
            NavigationStack { SearchView() }
                .tabItem { Label("搜索", systemImage: "magnifyingglass") }
                .tag(MainDestination.search)
            NavigationStack { MoreView() }
                .tabItem { Label("更多", systemImage: "ellipsis.circle") }
                .tag(MainDestination.more)
        }
        .sheet(isPresented: Binding(
            get: { store.destination == .transaction },
            set: { if !$0 { store.destination = .home } }
        )) {
            NavigationStack {
                TransactionEditorView()
            }
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $store.selectedTransactionDetail) { transaction in
            TransactionDetailView(transaction: transaction)
                .environmentObject(store)
        }
    }
}
#endif

#if os(macOS)
struct MacRootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        NavigationSplitView {
            List(MainDestination.allCases, selection: $store.destination) { destination in
                Label(destination.title, systemImage: destination.systemImage)
                    .tag(destination)
            }
            .listStyle(.sidebar)
            .navigationTitle("OmniFlow")
        } detail: {
            NavigationStack {
                Group {
                    switch store.destination {
                    case .home: HomeView()
                    case .analytics: AnalyticsView()
                    case .transaction: TransactionEditorView()
                    case .search: SearchView()
                    case .more: MoreView()
                    }
                }
                .frame(maxWidth: 1200, maxHeight: .infinity, alignment: .topLeading)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .toolbar {
                    if store.destination != .transaction {
                        ToolbarItem(placement: .primaryAction) {
                            Button { store.startNewTransaction() } label: { Image(systemName: "plus") }
                                .help("新建交易")
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .sheet(item: $store.selectedTransactionDetail) { transaction in
            TransactionDetailView(transaction: transaction)
                .environmentObject(store)
        }
    }
}
#endif
