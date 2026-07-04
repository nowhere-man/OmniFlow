import { useEffect } from "react";
import { BrowserRouter as Router, Navigate, NavLink, Route, Routes } from "react-router-dom";
import {
  ArrowDownToLine,
  BarChart3,
  LayoutDashboard,
  ReceiptText,
  Search,
  Settings,
  Sparkles,
} from "lucide-react";
import Dashboard from "./features/dashboard/Dashboard";
import TransactionList from "./features/transactions/TransactionList";
import ImportView from "./features/import/ImportView";
import ChartsView from "./features/charts/ChartsView";
import SearchView from "./features/search/SearchView";
import SettingsView from "./features/settings/SettingsView";
import { applyTheme, useSettingsStore } from "./store/useSettingsStore";

const navItems = [
  { icon: LayoutDashboard, label: "今日", path: "/" },
  { icon: BarChart3, label: "分析", path: "/charts" },
  { icon: ArrowDownToLine, label: "导入", path: "/import" },
  { icon: ReceiptText, label: "明细", path: "/transactions" },
  { icon: Search, label: "搜索", path: "/search" },
  { icon: Settings, label: "设置", path: "/settings" },
];

function AppNav() {
  return (
    <>
      <header className="app-header">
        <NavLink to="/" className="brand-mark" aria-label="OmniFlow">
          <span className="brand-icon"><Sparkles size={18} /></span>
          <span className="brand-copy">
            <strong>OmniFlow</strong>
            <small>flowing money, clear mind</small>
          </span>
        </NavLink>
        <nav className="desktop-nav">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink key={item.path} to={item.path} className={({ isActive }) => `nav-pill ${isActive ? "active" : ""}`}>
                <Icon size={17} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </header>

      <nav className="mobile-nav" aria-label="底部导航">
        {navItems.slice(0, 5).map((item) => {
          const Icon = item.icon;
          return (
            <NavLink key={item.path} to={item.path} className={({ isActive }) => `mobile-nav-item ${isActive ? "active" : ""}`}>
              <Icon size={20} />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>
    </>
  );
}

export default function App() {
  const theme = useSettingsStore((state) => state.theme);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  return (
    <Router>
      <div className="app-shell">
        <AppNav />
        <main className="app-main">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/charts" element={<ChartsView />} />
            <Route path="/import" element={<ImportView />} />
            <Route path="/transactions" element={<TransactionList />} />
            <Route path="/search" element={<SearchView />} />
            <Route path="/settings/*" element={<SettingsView />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}
