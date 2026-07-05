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
          <div className="eyebrow">设置</div>
          <h1 className="page-title">设置</h1>
        </div>
      </section>
      <div className="settings-layout">
      <div className="panel panel-pad settings-sidebar">
        <nav className="settings-nav" aria-label="设置分类">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = location.pathname.includes(tab.id);
            return (
              <Link
                key={tab.id}
                to={tab.path}
                className={`settings-nav-item ${isActive ? 'active' : ''}`}
              >
                <Icon size={18} />
                {tab.label}
              </Link>
            );
          })}
        </nav>
      </div>

      <div className="panel panel-pad settings-content">
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
