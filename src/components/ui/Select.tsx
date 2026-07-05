import React, { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Check, ChevronDown } from "lucide-react";

export interface SelectOption {
  value: string;
  label: React.ReactNode;
}

export interface SelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  className?: string;
  disabled?: boolean;
}

export function Select({ value, onChange, options, placeholder = "请选择", className = "", disabled = false }: SelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((opt) => opt.value === value);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className={`custom-select-container ${className}`} ref={containerRef}>
      <button
        type="button"
        className={`select-field select-trigger ${isOpen ? "open" : ""} ${disabled ? "disabled" : ""}`}
        onClick={() => !disabled && setIsOpen(!isOpen)}
        disabled={disabled}
      >
        <span className="select-value">{selectedOption ? selectedOption.label : placeholder}</span>
        <ChevronDown size={15} className={`select-chevron ${isOpen ? "open" : ""}`} />
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.98 }}
            transition={{ duration: 0.15, ease: "easeOut" }}
            className="select-popover"
          >
            <div className="select-list">
              {options.length === 0 ? (
                <div className="select-empty">无选项</div>
              ) : (
                options.map((option) => {
                  const isSelected = option.value === value;
                  return (
                    <button
                      key={option.value}
                      type="button"
                      className={`select-option ${isSelected ? "selected" : ""}`}
                      onClick={() => {
                        onChange(option.value);
                        setIsOpen(false);
                      }}
                    >
                      <span className="option-label">{option.label}</span>
                      {isSelected && <Check size={14} className="option-check" />}
                    </button>
                  );
                })
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
