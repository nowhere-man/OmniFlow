import { FC, useState } from "react";
import { X, Search } from "lucide-react";
import { motion } from "framer-motion";
import { Icon } from "@iconify/react";

const COLORED_ICONS = [
  // Food & Drinks
  "fluent-emoji-flat:hamburger", "fluent-emoji-flat:pizza", "fluent-emoji-flat:steaming-bowl", 
  "fluent-emoji-flat:bento-box", "fluent-emoji-flat:shortcake", "fluent-emoji-flat:hot-beverage", 
  "fluent-emoji-flat:beer-mug", "fluent-emoji-flat:red-apple",
  
  // Transport
  "fluent-emoji-flat:bus", "fluent-emoji-flat:metro", "fluent-emoji-flat:taxi", 
  "fluent-emoji-flat:automobile", "fluent-emoji-flat:airplane", "fluent-emoji-flat:bicycle", 
  "fluent-emoji-flat:high-speed-train", "fluent-emoji-flat:ship",
  
  // Shopping & Clothes
  "fluent-emoji-flat:shopping-cart", "fluent-emoji-flat:shopping-bags", "fluent-emoji-flat:dress", 
  "fluent-emoji-flat:running-shoe", "fluent-emoji-flat:wrapped-gift", "fluent-emoji-flat:teddy-bear",
  
  // Home & Living
  "fluent-emoji-flat:house", "fluent-emoji-flat:couch-and-lamp", "fluent-emoji-flat:bed", 
  "fluent-emoji-flat:broom", "fluent-emoji-flat:sponge", "fluent-emoji-flat:high-voltage",
  "fluent-emoji-flat:droplet", "fluent-emoji-flat:fire",
  
  // Health & Medical
  "fluent-emoji-flat:pill", "fluent-emoji-flat:hospital", "fluent-emoji-flat:stethoscope", 
  "fluent-emoji-flat:drop-of-blood", "fluent-emoji-flat:tooth",
  
  // Entertainment & Hobby
  "fluent-emoji-flat:video-game", "fluent-emoji-flat:clapper-board", "fluent-emoji-flat:microphone", 
  "fluent-emoji-flat:ferris-wheel", "fluent-emoji-flat:open-book", "fluent-emoji-flat:musical-notes",
  "fluent-emoji-flat:camera", "fluent-emoji-flat:ticket",
  
  // Work & Study
  "fluent-emoji-flat:briefcase", "fluent-emoji-flat:laptop", "fluent-emoji-flat:office-building", 
  "fluent-emoji-flat:hammer-and-wrench", "fluent-emoji-flat:graduation-cap", "fluent-emoji-flat:pen",
  
  // Finance & Money
  "fluent-emoji-flat:money-bag", "fluent-emoji-flat:credit-card", "fluent-emoji-flat:dollar-banknote", 
  "fluent-emoji-flat:coin", "fluent-emoji-flat:bank", "fluent-emoji-flat:chart-increasing",
  
  // Family & Pets
  "fluent-emoji-flat:red-heart", "fluent-emoji-flat:people-hugging", "fluent-emoji-flat:baby", 
  "fluent-emoji-flat:cat-face", "fluent-emoji-flat:dog-face"
];

interface IconPickerProps {
  value?: string | null;
  onChange: (val: string) => void;
  onClose: () => void;
}

export const IconPicker: FC<IconPickerProps> = ({ value, onChange, onClose }) => {
  const [search, setSearch] = useState("");

  const filteredIcons = COLORED_ICONS.filter(icon => icon.toLowerCase().includes(search.toLowerCase()));

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
          <h2 style={{ margin: 0, fontSize: "18px" }}>选择精美图标</h2>
          <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>

        <div style={{ position: "relative" }}>
          <Search size={16} style={{ position: "absolute", left: "12px", top: "50%", transform: "translateY(-50%)", color: "var(--muted)" }} />
          <input 
            className="field" 
            placeholder="搜索图标 (例如: hamburger)..." 
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
                transition: "all 0.15s ease"
              }}
            >
              <Icon icon={icon} width={28} height={28} />
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
