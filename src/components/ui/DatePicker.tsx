import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight } from "lucide-react";
import { format, addMonths, subMonths, startOfMonth, endOfMonth, eachDayOfInterval, isSameMonth, isSameDay, startOfWeek, endOfWeek } from "date-fns";
import { zhCN } from "date-fns/locale";

export interface DatePickerProps {
  value: number | null; // Unix timestamp in seconds
  onChange: (value: number | null) => void;
  placeholder?: string;
  className?: string;
}

export function DatePicker({ value, onChange, placeholder = "选择日期", className = "" }: DatePickerProps) {
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
    onChange(Math.floor(date.getTime() / 1000));
    setIsOpen(false);
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
          {value ? format(new Date(value * 1000), "yyyy-MM-dd") : placeholder}
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
