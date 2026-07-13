import SwiftUI

#if os(iOS)
struct PhoneRootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var lastContentDestination = MainDestination.home

    var body: some View {
        TabView(selection: phoneDestination) {
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
            set: { if !$0 { store.destination = lastContentDestination } }
        ), onDismiss: {
            store.editingTransaction = nil
            if store.destination == .transaction { store.destination = lastContentDestination }
        }) {
            NavigationStack {
                TransactionEditorView()
            }
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $store.selectedTransactionDetail) { transaction in
            TransactionDetailView(transaction: transaction)
                .environmentObject(store)
        }
        .onChange(of: store.destination) { destination in
            if destination != .transaction { lastContentDestination = destination }
        }
    }

    private var phoneDestination: Binding<MainDestination> {
        Binding(
            get: { store.destination == .transaction ? lastContentDestination : store.destination },
            set: { destination in
                if destination == .transaction {
                    if store.destination != .transaction { lastContentDestination = store.destination }
                    store.startNewTransaction()
                } else {
                    lastContentDestination = destination
                    store.destination = destination
                }
            }
        )
    }
}
#endif

#if os(macOS)
struct MacRootView: View {
    @EnvironmentObject private var store: AppStore
    @SceneStorage("mac.sidebar.destination") private var selectedDestinationRaw = MainDestination.home.rawValue
    @State private var showingTransactionEditor = false

    var body: some View {
        NavigationSplitView {
            List(selection: sidebarSelection) {
                Section("概览") {
                    sidebarRow(.home, title: "首页", systemImage: "house")
                    sidebarRow(.analytics, title: "统计", systemImage: "chart.bar")
                    sidebarRow(.search, title: "搜索", systemImage: "magnifyingglass")
                }
                Section("管理") {
                    sidebarRow(.more, title: "管理", systemImage: "slider.horizontal.3")
                }
            }
            .listStyle(.sidebar)
            .navigationTitle("OmniFlow")
        } detail: {
            NavigationStack {
                Group {
                    switch selectedDestination {
                    case .home: HomeView()
                    case .analytics: AnalyticsView()
                    case .search: SearchView()
                    case .more: MacManagementView()
                    case .transaction: HomeView()
                    }
                }
                .frame(maxWidth: 1200, maxHeight: .infinity, alignment: .topLeading)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        Button { store.startNewTransaction() } label: { Label("新建交易", systemImage: "plus") }
                            .help("新建交易 (⌘N)")
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .searchable(text: $store.searchText, placement: .toolbar, prompt: "搜索交易")
        .onSubmit(of: .search) {
            select(.search)
            store.search()
        }
        .onAppear {
            if store.destination == .transaction {
                showingTransactionEditor = true
            } else {
                store.destination = selectedDestination
            }
        }
        .onChange(of: store.destination) { destination in
            if destination == .transaction {
                showingTransactionEditor = true
            } else if showingTransactionEditor {
                showingTransactionEditor = false
                store.destination = selectedDestination
            } else if destination != selectedDestination {
                selectedDestinationRaw = destination.rawValue
            }
        }
        .onChange(of: showingTransactionEditor) { visible in
            guard !visible, store.destination == .transaction else { return }
            store.editingTransaction = nil
            store.destination = selectedDestination
        }
        .sheet(isPresented: $showingTransactionEditor) {
            NavigationStack {
                TransactionEditorView()
                    .navigationTitle(store.editingTransaction == nil ? "新建交易" : "编辑交易")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消") { showingTransactionEditor = false }
                        }
                    }
            }
            .frame(minWidth: 980, minHeight: 660)
        }
        .sheet(item: $store.selectedTransactionDetail) { transaction in
            TransactionDetailView(transaction: transaction)
                .environmentObject(store)
        }
    }

    private var selectedDestination: MainDestination {
        let destination = MainDestination(rawValue: selectedDestinationRaw) ?? .home
        return destination == .transaction ? .home : destination
    }

    private var sidebarSelection: Binding<MainDestination> {
        Binding(get: { selectedDestination }, set: select)
    }

    private func select(_ destination: MainDestination) {
        selectedDestinationRaw = destination.rawValue
        store.destination = destination
    }

    private func sidebarRow(_ destination: MainDestination, title: String, systemImage: String) -> some View {
        Label(title, systemImage: systemImage).tag(destination)
    }
}

private struct MacManagementView: View {
    var body: some View {
        List {
            Section("数据") {
                managementLink("数据管理", systemImage: "externaldrive.badge.icloud")
                managementLink("导入", systemImage: "square.and.arrow.down")
                managementLink("导出", systemImage: "square.and.arrow.up")
            }
            Section("账本与账户") {
                managementLink("账本", systemImage: "books.vertical")
                managementLink("账户", systemImage: "wallet.pass")
                managementLink("资产", systemImage: "chart.pie")
                managementLink("分类管理", systemImage: "square.grid.2x2")
                managementLink("标签管理", systemImage: "tag")
            }
            Section("自动化") {
                managementLink("规则", systemImage: "list.bullet.rectangle")
                managementLink("提醒", systemImage: "bell")
            }
        }
        .listStyle(.inset)
        .navigationTitle("管理")
    }

    private func managementLink(_ title: String, systemImage: String) -> some View {
        NavigationLink { ModuleView(title: title) } label: { Label(title, systemImage: systemImage) }
    }
}
#endif
