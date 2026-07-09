import { useSettingsStore, ThemeMode, ThemeColor } from "../../../store/useSettingsStore";
import { Monitor, Moon, Sun, Check } from "lucide-react";

export default function BasicSettings() {
  const { theme, setTheme, themeColor, setThemeColor } = useSettingsStore();

  const themeOptions: { value: ThemeMode; label: string; icon: React.ElementType }[] = [
    { value: 'light', label: '浅色模式', icon: Sun },
    { value: 'dark', label: '深色模式', icon: Moon },
    { value: 'system', label: '跟随系统', icon: Monitor },
  ];

  const colorOptions: { value: ThemeColor; label: string; colorHex: string }[] = [
    { value: 'mint', label: '薄荷绿', colorHex: '#3f7f72' },
    { value: 'blue', label: '科技蓝', colorHex: '#56779b' },
    { value: 'purple', label: '星空紫', colorHex: '#7b6596' },
    { value: 'orange', label: '暖琥珀', colorHex: '#a9783d' },
    { value: 'rose', label: '灰玫瑰', colorHex: '#a95d6f' },
  ];

  return (
    <div className="settings-stack">
      <section className="settings-section">
        <h2>外观与主题</h2>
        <div className="theme-choice-grid">
          {themeOptions.map((option) => {
            const Icon = option.icon;
            const isActive = theme === option.value;
            return (
              <button
                key={option.value}
                onClick={() => setTheme(option.value)}
                className={`theme-choice ${isActive ? 'active' : ''}`}
              >
                <Icon size={28} />
                <span>{option.label}</span>
              </button>
            );
          })}
        </div>
      </section>
      <section className="settings-section">
        <h2>主题色</h2>
        <div className="color-choice-grid">
          {colorOptions.map((option) => {
            const isActive = themeColor === option.value;
            return (
              <button
                key={option.value}
                onClick={() => setThemeColor(option.value)}
                className={`color-choice ${isActive ? 'active' : ''}`}
                style={{ '--choice-bg': option.colorHex } as React.CSSProperties}
                title={option.label}
                aria-label={`选择${option.label}`}
              >
                <Check size={24} />
              </button>
            );
          })}
        </div>
      </section>
      <p className="muted-note">
        主题切换会立即生效，跟随系统模式会根据您操作系统的当前外观自动匹配深浅色。
      </p>
    </div>
  );
}
