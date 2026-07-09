import { useEffect } from "react";
import { FluentProvider, webDarkTheme, webLightTheme } from "@fluentui/react-components";
import { BrowserRouter as Router, Navigate, NavLink, Route, Routes } from "react-router-dom";
import {
  AreaChart,
  Banknote,
  FileDown,
  Home,
  Search,
  Settings,
} from "lucide-react";
import Dashboard from "./features/dashboard/Dashboard";
import ImportView from "./features/import/ImportView";
import ChartsView from "./features/charts/ChartsView";
import SearchView from "./features/search/SearchView";
import SettingsView from "./features/settings/SettingsView";
import { applyTheme, useSettingsStore } from "./store/useSettingsStore";

const navItems = [
  { icon: Home, label: "首页", path: "/" },
  { icon: AreaChart, label: "分析", path: "/charts" },
  { icon: Search, label: "搜索", path: "/search" },
  { icon: FileDown, label: "导入", path: "/import" },
  { icon: Settings, label: "设置", path: "/settings" },
];

function AppChrome() {
  return (
    <>
      <a className="skip-link" href="#main-content">跳到主要内容</a>
      <header className="workspace-topbar">
        <NavLink to="/" className="brand-lockup" aria-label="OmniFlow">
          <span className="brand-orb"><Banknote size={18} /></span>
          <strong>OmniFlow</strong>
        </NavLink>

        <nav className="task-switcher" aria-label="主导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink key={item.path} to={item.path} className={({ isActive }) => `task-tab ${isActive ? "active" : ""}`}>
                <Icon size={17} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </header>

      <nav className="mobile-taskbar" aria-label="移动端导航">
        {navItems.slice(0, 5).map((item) => {
          const Icon = item.icon;
          return (
            <NavLink key={item.path} to={item.path} className={({ isActive }) => `mobile-task ${isActive ? "active" : ""}`}>
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
  const themeColor = useSettingsStore((state) => state.themeColor);
  const fluentTheme = theme === "dark"
    || (theme === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches)
    ? webDarkTheme
    : webLightTheme;

  useEffect(() => {
    applyTheme(theme, themeColor);
  }, [theme, themeColor]);

  return (
    <FluentProvider theme={fluentTheme} className="fluent-root">
      <Router>
        <div className="workspace-shell">
          <AppChrome />
          <main id="main-content" className="workspace-main">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/import" element={<ImportView />} />
              <Route path="/charts" element={<ChartsView />} />
              <Route path="/search" element={<SearchView />} />
              <Route path="/settings/*" element={<SettingsView />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
        </div>
      </Router>
    </FluentProvider>
  );
}
