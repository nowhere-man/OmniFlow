import { FC, useState } from "react";
import { X, Search } from "lucide-react";
import { motion } from "framer-motion";
import { CategoryIcon } from "./CategoryIcon";

const COMMON_ICONS = [
  "Coffee", "Utensils", "ShoppingCart", "Bus", "Train", "Plane", "Car",
  "Home", "Building", "Phone", "Smartphone", "Tv", "Zap", "Droplet", "Flame",
  "Shirt", "ShoppingBag", "Gamepad", "Music", "Camera", "Ticket", "Popcorn",
  "HeartPulse", "Pill", "Stethoscope", "Book", "GraduationCap", "PenTool",
  "Briefcase", "Coins", "PiggyBank", "CreditCard", "Wallet", "Banknote",
  "Gift", "Heart", "Users", "Baby", "Cat", "Dog",
  "Wrench", "Hammer", "Scissors", "ScissorsSquare", "Paintbrush",
  "Smile", "Frown", "Star", "Cloud", "Sun", "Moon", "Wind",
  "ArrowDown", "ArrowUp", "ArrowRight", "ArrowLeft", "Plus", "Minus",
  "CircleDashed", "CheckCircle", "XCircle", "AlertCircle"
];

interface IconPickerProps {
  value?: string | null;
  onChange: (val: string) => void;
  onClose: () => void;
}

export const IconPicker: FC<IconPickerProps> = ({ value, onChange, onClose }) => {
  const [search, setSearch] = useState("");

  const filteredIcons = COMMON_ICONS.filter(icon => icon.toLowerCase().includes(search.toLowerCase()));

  return (
    <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, background: "rgba(0,0,0,0.5)", zIndex: 2000, display: "flex", alignItems: "center", justifyContent: "center" }} onClick={onClose}>
      <motion.div 
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
        transition={{ duration: 0.2 }}
        onClick={(e) => e.stopPropagation()}
        className="panel"
        style={{ width: "400px", height: "500px", padding: "24px", display: "flex", flexDirection: "column", gap: "16px", background: "var(--background)", border: "1px solid var(--border)", borderRadius: "12px", boxShadow: "0 20px 40px rgba(0,0,0,0.3)" }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px" }}>
          <h2 style={{ margin: 0, fontSize: "18px" }}>选择图标</h2>
          <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>

        <div style={{ position: "relative" }}>
          <Search size={16} style={{ position: "absolute", left: "12px", top: "50%", transform: "translateY(-50%)", color: "var(--muted)" }} />
          <input 
            className="field" 
            placeholder="搜索图标..." 
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ paddingLeft: "36px", width: "100%" }}
          />
        </div>

        <div className="hide-scrollbar" style={{ flex: 1, overflowY: "auto", display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: "12px", alignContent: "start", padding: "4px" }}>
          {filteredIcons.map(icon => (
            <div 
              key={icon}
              onClick={() => { onChange(icon); onClose(); }}
              style={{ 
                aspectRatio: "1", 
                display: "flex", 
                alignItems: "center", 
                justifyContent: "center", 
                borderRadius: "8px", 
                cursor: "pointer",
                background: value === icon ? "color-mix(in srgb, var(--primary) 20%, transparent)" : "transparent",
                border: value === icon ? "1.5px solid var(--primary)" : "1px solid color-mix(in srgb, var(--border) 40%, transparent)",
                color: value === icon ? "var(--primary)" : "var(--foreground)",
                transition: "all 0.15s ease"
              }}
            >
              <CategoryIcon name={icon} size={24} />
            </div>
          ))}
          {filteredIcons.length === 0 && (
            <div style={{ gridColumn: "1 / -1", textAlign: "center", padding: "32px 0", color: "var(--muted)" }}>没找到相关图标</div>
          )}
        </div>
      </motion.div>
    </div>
  );
};
