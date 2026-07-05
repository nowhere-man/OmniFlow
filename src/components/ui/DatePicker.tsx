import { useEffect, useRef, useState, useLayoutEffect } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight } from "lucide-react";
import { format, addMonths, subMonths, startOfMonth, endOfMonth, eachDayOfInterval, isSameMonth, isSameDay, startOfWeek, endOfWeek } from "date-fns";
import { zhCN } from "date-fns/locale";

export interface DatePickerProps {
  value: number | null; // Unix timestamp in seconds
  onChange: (value: number | null) => void;
  placeholder?: string;
  className?: string;
  showTime?: boolean;
}

export function DatePicker({ value, onChange, placeholder = "选择日期", className = "", showTime = false }: DatePickerProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [currentMonth, setCurrentMonth] = useState(value ? new Date(value * 1000) : new Date());
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleDateSelect = (date: Date) => {
    if (showTime) {
      const referenceDate = value ? new Date(value * 1000) : new Date();
      date.setHours(referenceDate.getHours(), referenceDate.getMinutes(), 0, 0);
      onChange(Math.floor(date.getTime() / 1000));
    } else {
      date.setHours(0, 0, 0, 0);
      onChange(Math.floor(date.getTime() / 1000));
      setIsOpen(false);
    }
  };

  const monthStart = startOfMonth(currentMonth);
  const monthEnd = endOfMonth(currentMonth);
  const startDate = startOfWeek(monthStart, { weekStartsOn: 1 });
  const endDate = endOfWeek(monthEnd, { weekStartsOn: 1 });

  const calendarDays = eachDayOfInterval({ start: startDate, end: endDate });
  const weekDays = ["一", "二", "三", "四", "五", "六", "日"];

  return (
    <div className={`custom-select-container ${className}`} ref={containerRef}>
      <button
        type="button"
        className={`select-field select-trigger ${isOpen ? "open" : ""}`}
        onClick={() => setIsOpen(!isOpen)}
      >
        <span className={`select-value ${!value ? "text-muted" : ""}`}>
          {value ? format(new Date(value * 1000), showTime ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd") : placeholder}
        </span>
        <CalendarIcon size={15} className="select-chevron" />
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.98 }}
            transition={{ duration: 0.15, ease: "easeOut" }}
            className="select-popover datepicker-popover"
          >
            <div className="datepicker-header">
              <button type="button" className="ghost-button icon-btn" onClick={() => setCurrentMonth(subMonths(currentMonth, 1))}>
                <ChevronLeft size={16} />
              </button>
              <div className="datepicker-title">
                {format(currentMonth, "yyyy年 M月", { locale: zhCN })}
              </div>
              <button type="button" className="ghost-button icon-btn" onClick={() => setCurrentMonth(addMonths(currentMonth, 1))}>
                <ChevronRight size={16} />
              </button>
            </div>
            
            <div className="datepicker-grid week-header">
              {weekDays.map((day) => (
                <div key={day} className="datepicker-cell muted">{day}</div>
              ))}
            </div>
            
            <div className="datepicker-grid">
              {calendarDays.map((day, idx) => {
                const isSelected = value ? isSameDay(day, new Date(value * 1000)) : false;
                const isCurrentMonth = isSameMonth(day, currentMonth);
                const isToday = isSameDay(day, new Date());
                
                return (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => handleDateSelect(day)}
                    className={`datepicker-cell day-btn ${!isCurrentMonth ? "outside-month" : ""} ${isSelected ? "selected" : ""} ${isToday && !isSelected ? "today" : ""}`}
                  >
                    {format(day, "d")}
                  </button>
                );
              })}
            </div>
            
            {showTime && value && (
              <div className="datepicker-time" style={{ padding: "8px 12px", borderTop: "1px dashed color-mix(in srgb, var(--border) 60%, transparent)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span style={{ fontSize: "13px", color: "var(--muted)", fontWeight: 500 }}>具体时间</span>
                <div style={{ display: "flex", alignItems: "center", gap: "2px" }}>
                  <TimeDropdown 
                    options={Array.from({ length: 24 }, (_, i) => i.toString().padStart(2, "0"))} 
                    value={format(new Date(value * 1000), "HH")} 
                    onChange={(h) => {
                       const date = new Date(value * 1000);
                       date.setHours(parseInt(h, 10));
                       onChange(Math.floor(date.getTime() / 1000));
                    }} 
                  />
                  <span style={{ fontWeight: 600, color: "var(--muted)", paddingBottom: "2px" }}>:</span>
                  <TimeDropdown 
                    options={Array.from({ length: 60 }, (_, i) => i.toString().padStart(2, "0"))} 
                    value={format(new Date(value * 1000), "mm")} 
                    onChange={(m) => {
                       const date = new Date(value * 1000);
                       date.setMinutes(parseInt(m, 10));
                       onChange(Math.floor(date.getTime() / 1000));
                    }} 
                  />
                </div>
              </div>
            )}
            
            {value && (
              <div className="datepicker-footer">
                <button type="button" className="ghost-button text-sm w-full text-center" onClick={() => { onChange(null); setIsOpen(false); }}>
                  清除选择
                </button>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function TimeDropdown({ options, value, onChange }: { options: string[], value: string, onChange: (val: string) => void }) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  return (
    <div ref={containerRef} style={{ position: "relative" }}>
      <button 
        type="button" 
        onClick={() => setOpen(!open)}
        style={{ padding: "4px 6px", background: open ? "color-mix(in srgb, var(--surface) 80%, transparent)" : "transparent", borderRadius: "6px", fontSize: "15px", fontWeight: 600, color: "var(--foreground)", cursor: "pointer", border: "none", fontFamily: "monospace" }}
      >
        {value}
      </button>
      <AnimatePresence>
        {open && (
           <motion.div 
             initial={{ opacity: 0, y: 4, scale: 0.95 }}
             animate={{ opacity: 1, y: 0, scale: 1 }}
             exit={{ opacity: 0, y: 4, scale: 0.95 }}
             transition={{ duration: 0.15 }}
             style={{ position: "absolute", bottom: "calc(100% + 4px)", left: "50%", transform: "translateX(-50%)", background: "var(--background)", border: "1px solid var(--border)", borderRadius: "8px", boxShadow: "0 10px 25px rgba(0,0,0,0.2)", zIndex: 1000, height: "180px", width: "56px", overflow: "hidden" }}
           >
              <ScrollColumn options={options} value={value} onChange={(v) => { onChange(v); }} />
           </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function ScrollColumn({ options, value, onChange }: { options: string[], value: string, onChange: (val: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const isScrollingRef = useRef(false);

  useLayoutEffect(() => {
    if (ref.current) {
      const idx = options.indexOf(value);
      if (idx !== -1) {
        ref.current.scrollTop = idx * 36;
      }
    }
  }, [options]);

  const handleScroll = () => {
    if (isScrollingRef.current || !ref.current) return;
    const idx = Math.round(ref.current.scrollTop / 36);
    const safeIdx = Math.max(0, Math.min(options.length - 1, idx));
    const newValue = options[safeIdx];
    if (newValue !== value) {
       onChange(newValue);
    }
  };

  return (
    <div 
      ref={ref}
      onScroll={handleScroll}
      style={{ flex: 1, height: "100%", overflowY: "auto", scrollSnapType: "y mandatory", padding: "72px 0", scrollbarWidth: "none", msOverflowStyle: "none" }} 
      className="hide-scrollbar"
    >
      {options.map((opt) => (
        <div
          key={opt}
          onClick={() => { 
            isScrollingRef.current = true;
            onChange(opt); 
            if (ref.current) ref.current.scrollTo({ top: options.indexOf(opt) * 36, behavior: "smooth" }); 
            setTimeout(() => isScrollingRef.current = false, 300);
          }}
          style={{ height: "36px", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", scrollSnapAlign: "center", color: opt === value ? "var(--foreground)" : "var(--muted)", fontWeight: opt === value ? 600 : 400, background: opt === value ? "color-mix(in srgb, var(--foreground) 10%, transparent)" : "transparent", borderRadius: "8px", margin: "0 6px", transition: "all 0.15s ease" }}
        >
          {opt}
        </div>
      ))}
    </div>
  );
}

export function YearMonthPicker({ value, onChange, onClose }: { value: Date, onChange: (date: Date) => void, onClose: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        onClose();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [onClose]);

  const years = Array.from({ length: 21 }, (_, i) => (2020 + i).toString());
  const months = Array.from({ length: 12 }, (_, i) => (i + 1).toString().padStart(2, "0"));

  return (
    <motion.div 
      initial={{ opacity: 0, y: -10, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -10, scale: 0.95 }}
      transition={{ duration: 0.15 }}
      ref={containerRef}
      style={{ position: "absolute", top: "calc(100% + 8px)", left: "50%", transform: "translateX(-50%)", background: "var(--background)", border: "1px solid var(--border)", borderRadius: "12px", boxShadow: "0 10px 30px rgba(0,0,0,0.2)", zIndex: 100, display: "flex", width: "200px", height: "240px", overflow: "hidden" }}
    >
      <div style={{ flex: 1, borderRight: "1px solid var(--border)", display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "8px", textAlign: "center", fontSize: "12px", color: "var(--muted)", fontWeight: 600, background: "color-mix(in srgb, var(--surface) 50%, transparent)" }}>年份</div>
        <ScrollColumn options={years} value={format(value, "yyyy")} onChange={(y) => onChange(new Date(`${y}-${format(value, "MM")}-01`))} />
      </div>
      <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "8px", textAlign: "center", fontSize: "12px", color: "var(--muted)", fontWeight: 600, background: "color-mix(in srgb, var(--surface) 50%, transparent)" }}>月份</div>
        <ScrollColumn options={months} value={format(value, "MM")} onChange={(m) => onChange(new Date(`${format(value, "yyyy")}-${m}-01`))} />
      </div>
    </motion.div>
  );
}
