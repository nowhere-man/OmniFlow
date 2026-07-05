import { useCallback, useEffect, useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { AnimatePresence } from "framer-motion";
import { format, startOfMonth, endOfMonth, addMonths, subMonths, eachDayOfInterval, startOfWeek, endOfWeek, isSameMonth, isToday } from "date-fns";

import { useAppStore } from "../../stores/appStore";
import { Transaction, TransactionAPI } from "../../tauri-adapter/transactions";
import { Category } from "../../models";
import { YearMonthPicker } from "../../components/ui/DatePicker";
import { TransactionEditor } from "../transactions/TransactionEditor";
import { yuan } from "../../lib/format";

const now = () => Math.floor(Date.now() / 1000);

export default function Dashboard() {
  const { accounts, currentLedgerId, fetchInitialData } = useAppStore();
  const [selectedMonth, setSelectedMonth] = useState(() => startOfMonth(new Date()));
  const [showMonthPicker, setShowMonthPicker] = useState(false);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [draft, setDraft] = useState<Transaction | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);

  useEffect(() => {
    fetchInitialData();
    invoke<Category[]>("list_categories").then(setCategories).catch(() => setCategories([]));
  }, [fetchInitialData]);

  const loadMonthData = useCallback(async () => {
    if (!currentLedgerId) return;
    setIsLoading(true);
    const start = Math.floor(startOfMonth(selectedMonth).getTime() / 1000);
    const end = Math.floor(endOfMonth(selectedMonth).getTime() / 1000);
    try {
      const res = await TransactionAPI.searchTransactions(currentLedgerId, { start_date: start, end_date: end });
      setTransactions(res.transactions);
    } finally {
      setIsLoading(false);
    }
  }, [currentLedgerId, selectedMonth]);

  useEffect(() => {
    loadMonthData();
  }, [loadMonthData]);

  const handlePrevMonth = () => setSelectedMonth((m) => subMonths(m, 1));
  const handleNextMonth = () => setSelectedMonth((m) => addMonths(m, 1));

  const calendarDays = useMemo(() => {
    const monthStart = startOfMonth(selectedMonth);
    const monthEnd = endOfMonth(selectedMonth);
    const startDate = startOfWeek(monthStart, { weekStartsOn: 1 });
    const endDate = endOfWeek(monthEnd, { weekStartsOn: 1 });

    const days = eachDayOfInterval({ start: startDate, end: endDate });

    const dailyNet = new Map<string, number>();
    for (const t of transactions) {
      if (t.is_excluded) continue;
      const dateStr = format(new Date(t.transaction_date * 1000), "yyyy-MM-dd");
      const current = dailyNet.get(dateStr) || 0;
      const delta = t.transaction_type === "income" ? t.amount : -t.amount;
      dailyNet.set(dateStr, current + delta);
    }

    return days.map(day => {
      const dateStr = format(day, "yyyy-MM-dd");
      return {
        date: day,
        isCurrentMonth: isSameMonth(day, selectedMonth),
        isToday: isToday(day),
        netIncome: dailyNet.get(dateStr),
      };
    });
  }, [selectedMonth, transactions]);

  const weekDays = ["一", "二", "三", "四", "五", "六", "日"];

  function openQuickEntry() {
    if (!currentLedgerId || accounts.length === 0) return;
    const defaultAccount = accounts.find((a) => a.account_type === "cash") || accounts[0];
    const timestamp = now();
    setDraft({
      id: crypto.randomUUID(),
      ledger_id: currentLedgerId,
      account_id: defaultAccount.id,
      category_id: null,
      transaction_date: timestamp,
      amount: 0,
      transaction_type: "expense",
      merchant: null,
      notes: null,
      tags: [],
      is_excluded: false,
      external_source: "manual",
      external_id: null,
      created_at: timestamp,
      updated_at: timestamp,
      deleted_at: null,
    });
  }

  async function saveDraft() {
    if (!draft) return;
    await TransactionAPI.createTransaction(draft);
    setDraft(null);
    await loadMonthData();
  }

  return (
    <div className="money-flow-page" style={{ padding: "24px", maxWidth: "1000px", margin: "0 auto", height: "calc(100vh - 48px)", display: "flex", flexDirection: "column" }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "32px", position: "relative" }}>
        <div style={{ width: "80px" }}></div>
        <div style={{ display: "flex", alignItems: "center", gap: "16px", position: "relative" }}>
          <button className="icon-button" onClick={handlePrevMonth}><ChevronLeft size={24} /></button>
          <div style={{ position: "relative" }}>
            <div 
              style={{ cursor: "pointer", padding: "8px 16px", borderRadius: "8px", background: showMonthPicker ? "var(--surface)" : "color-mix(in srgb, var(--surface) 50%, transparent)", transition: "background 0.2s" }} 
              onClick={() => setShowMonthPicker(!showMonthPicker)}
            >
              <h2 style={{ margin: 0, fontSize: "22px", fontWeight: 600 }}>{format(selectedMonth, "yyyy年 MM月")}</h2>
            </div>
            <AnimatePresence>
              {showMonthPicker && (
                <YearMonthPicker 
                  value={selectedMonth} 
                  onChange={setSelectedMonth} 
                  onClose={() => setShowMonthPicker(false)} 
                />
              )}
            </AnimatePresence>
          </div>
          <button className="icon-button" onClick={handleNextMonth}><ChevronRight size={24} /></button>
        </div>
        <div style={{ width: "80px" }}></div>
      </header>

      <div className="panel" style={{ flex: 1, padding: "24px", display: "flex", flexDirection: "column" }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", marginBottom: "16px" }}>
          {weekDays.map(d => (
            <div key={d} style={{ textAlign: "center", fontSize: "14px", fontWeight: 600, color: "var(--muted)" }}>周{d}</div>
          ))}
        </div>
        {isLoading ? (
          <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--muted)" }}>正在载入日历...</div>
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: "12px", flex: 1 }}>
            {calendarDays.map((item, idx) => (
              <div 
                key={idx} 
                style={{ 
                  background: item.isCurrentMonth ? "color-mix(in srgb, var(--surface) 30%, transparent)" : "transparent",
                  borderRadius: "12px", 
                  padding: "12px",
                  display: "flex", 
                  flexDirection: "column", 
                  alignItems: "center",
                  opacity: item.isCurrentMonth ? 1 : 0.4,
                  border: item.isToday ? "1.5px solid var(--primary)" : "1.5px solid color-mix(in srgb, var(--border) 40%, transparent)"
                }}
              >
                <div style={{ fontSize: "18px", fontWeight: item.isToday ? 700 : 500, color: item.isToday ? "var(--primary)" : "var(--foreground)" }}>
                  {format(item.date, "d")}
                </div>
                <div style={{ marginTop: "auto", fontSize: "13px", fontWeight: 600, height: "20px", display: "flex", alignItems: "flex-end" }}>
                  {item.netIncome !== undefined && item.netIncome !== 0 && (
                    <span style={{ color: item.netIncome > 0 ? "var(--success)" : "var(--danger)" }}>
                      {item.netIncome > 0 ? "+" : "-"}{yuan(Math.abs(item.netIncome))}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <button 
        className="primary-button hover-transform" 
        onClick={openQuickEntry}
        style={{ position: "fixed", bottom: "40px", right: "40px", width: "64px", height: "64px", borderRadius: "32px", display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 12px 24px rgba(0,0,0,0.3)", padding: 0 }}
      >
        <Plus size={28} />
      </button>

      <AnimatePresence>
        {draft && (
          <TransactionEditor
            transaction={draft}
            onChange={setDraft}
            onSave={saveDraft}
            onClose={() => setDraft(null)}
            accounts={accounts}
            categories={categories}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
