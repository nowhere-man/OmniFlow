import { Link, Routes, Route, useLocation, Navigate } from "react-router-dom";
import { Palette, Database, CloudSync, Tags, SlidersHorizontal } from "lucide-react";
import BasicSettings from "./components/BasicSettings";
import SyncSettings from "./components/SyncSettings";
import DataSettings from "./components/DataSettings";
import CategorySettings from "./components/CategorySettings";
import ManagementSettings from "./components/ManagementSettings";

export default function SettingsView() {
  const location = useLocation();

  const tabs = [
    { id: 'basic', label: '外观与基础', icon: Palette, path: '/settings/basic' },
    { id: 'categories', label: '分类管理', icon: Tags, path: '/settings/categories' },
    { id: 'management', label: '账本与规则', icon: SlidersHorizontal, path: '/settings/management' },
    { id: 'data', label: '数据安全', icon: Database, path: '/settings/data' },
    { id: 'sync', label: '高级同步', icon: CloudSync, path: '/settings/sync' },
  ];

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <div className="eyebrow">settings</div>
          <h1 className="page-title">把复杂能力收进清楚的地方</h1>
        </div>
      </section>
      <div className="grid grid-cols-1 lg:grid-cols-[220px_minmax(0,1fr)] gap-4">
      {/* Settings Sidebar */}
      <div className="panel panel-pad">
        <nav className="flex flex-col gap-1">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = location.pathname.includes(tab.id);
            return (
              <Link
                key={tab.id}
                to={tab.path}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-primary/10 text-primary'
                    : 'text-surface-foreground hover:bg-surface hover:text-foreground'
                }`}
              >
                <Icon size={18} />
                {tab.label}
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Settings Content Area */}
      <div className="panel panel-pad min-h-[520px]">
        <Routes>
          <Route path="/" element={<Navigate to="basic" replace />} />
          <Route path="basic" element={<BasicSettings />} />
          <Route path="categories" element={<CategorySettings />} />
          <Route path="management" element={<ManagementSettings />} />
          <Route path="data" element={<DataSettings />} />
          <Route path="sync" element={<SyncSettings />} />
        </Routes>
      </div>
      </div>
    </div>
  );
}
