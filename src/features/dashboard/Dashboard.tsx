import { useCallback, useEffect, useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Plus, LayoutGrid, List } from "lucide-react";
import { AnimatePresence } from "framer-motion";
import { format, startOfMonth, endOfMonth, addMonths, subMonths, eachDayOfInterval, startOfWeek, endOfWeek, isSameMonth, isToday } from "date-fns";
import { zhCN } from "date-fns/locale";

import { useAppStore } from "../../stores/appStore";
import { Transaction, TransactionAPI } from "../../tauri-adapter/transactions";
import { Category } from "../../models";
import { YearMonthPicker } from "../../components/ui/DatePicker";
import { TransactionEditor } from "../transactions/TransactionEditor";
import { CategoryIcon } from "../../components/ui/CategoryIcon";
import { yuan } from "../../lib/format";

const now = () => Math.floor(Date.now() / 1000);

function getTransactionIcon(t: Transaction, categories: Category[]) {
  const cat = categories.find(c => c.id === t.category_id);
  const parent = cat?.parent_id ? categories.find(c => c.id === cat.parent_id) : cat;
  return cat?.icon || parent?.icon;
}

function categoryOptions(categories: Category[]) {
  const parents = categories.filter((category) => !category.parent_id);
  const childrenByParent = new Map<string, Category[]>();
  for (const category of categories) {
    if (!category.parent_id) continue;
    childrenByParent.set(category.parent_id, [...(childrenByParent.get(category.parent_id) || []), category]);
  }
  return parents.flatMap((parent) => [
    { id: parent.id, label: parent.name },
    ...(childrenByParent.get(parent.id) || []).map((child) => ({ id: child.id, label: `${parent.name} / ${child.name}` })),
  ]);
}

export default function Dashboard() {
  const { accounts, currentLedgerId, fetchInitialData } = useAppStore();
  const [selectedMonth, setSelectedMonth] = useState(() => startOfMonth(new Date()));
  const [showMonthPicker, setShowMonthPicker] = useState(false);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [metrics, setMetrics] = useState({ income: 0, expense: 0 });
  const [isLoading, setIsLoading] = useState(false);
  
  const [layoutMode, setLayoutMode] = useState<"card" | "list">("card");
  const [editing, setEditing] = useState<Transaction | null>(null);
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
      setMetrics({ income: res.total_income, expense: res.total_expense });
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

  const categoryLabelById = useMemo(() => new Map(categoryOptions(categories).map((category) => [category.id, category.label])), [categories]);

  const groupedTransactions = useMemo(() => {
    const groups = new Map<string, Transaction[]>();
    for (const t of transactions) {
      const dateStr = format(new Date(t.transaction_date * 1000), "yyyy-MM-dd");
      if (!groups.has(dateStr)) groups.set(dateStr, []);
      groups.get(dateStr)!.push(t);
    }
    const sortedKeys = Array.from(groups.keys()).sort((a, b) => b.localeCompare(a));
    return sortedKeys.map(k => {
      const dateObj = new Date(k);
      return {
        dateString: k,
        label: format(dateObj, "MM月dd日 EEEE", { locale: zhCN }),
        transactions: groups.get(k)!.sort((a, b) => b.transaction_date - a.transaction_date)
      };
    });
  }, [transactions]);

  function openQuickEntry() {
    if (!currentLedgerId || accounts.length === 0) return;
    const defaultAccount = accounts.find((a) => a.account_type === "cash") || accounts[0];
    const timestamp = now();
    setEditing({
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

  async function save() {
    if (!editing) return;
    const payload = { ...editing, updated_at: now() };
    const exists = transactions.some((transaction) => transaction.id === payload.id);
    if (exists) {
      await TransactionAPI.updateTransaction(payload);
    } else {
      await TransactionAPI.createTransaction(payload);
    }
    setEditing(null);
    await loadMonthData();
  }

  async function remove() {
    if (!editing) return;
    await TransactionAPI.deleteTransaction(editing.id);
    setEditing(null);
    await loadMonthData();
  }

  const netIncome = metrics.income - metrics.expense;
  const netColor = netIncome > 0 ? "var(--success)" : netIncome < 0 ? "var(--danger)" : "inherit";
  const weekDays = ["一", "二", "三", "四", "五", "六", "日"];

  return (
    <div className="money-flow-page">
      <header className="dashboard-header">
        <div style={{ width: "80px" }}></div>
        <div className="dashboard-month-selector">
          <button className="icon-button" onClick={handlePrevMonth} aria-label="上个月">
            <ChevronLeft size={20} />
          </button>
          <div style={{ position: "relative" }}>
            <button 
              className="dashboard-title-trigger"
              onClick={() => setShowMonthPicker(!showMonthPicker)}
            >
              <h2>{format(selectedMonth, "yyyy年 MM月")}</h2>
            </button>
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
          <button className="icon-button" onClick={handleNextMonth} aria-label="下个月">
            <ChevronRight size={20} />
          </button>
        </div>
        <div style={{ width: "80px" }}></div>
      </header>

      <section className="transactions-kpis">
        <div className="kpi-card">
          <div className="kpi-card-label">总收入</div>
          <div className="kpi-card-value income">{yuan(metrics.income)}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card-label">总支出</div>
          <div className="kpi-card-value expense">{yuan(metrics.expense)}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card-label">净收入</div>
          <div className="kpi-card-value" style={{ color: netColor }}>{yuan(netIncome)}</div>
        </div>
      </section>

      <div className="home-stack" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
        {/* Top Block: Calendar */}
        <div className="calendar-panel">
          <div className="calendar-weekdays">
            {weekDays.map(d => (
              <div key={d} className="calendar-weekday">周{d}</div>
            ))}
          </div>
          {isLoading ? (
            <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--muted)", minHeight: "200px" }}>正在载入日历...</div>
          ) : (
            <div className="calendar-grid">
              {calendarDays.map((item, idx) => (
                <div 
                  key={idx} 
                  className={`calendar-day-card ${item.isCurrentMonth ? "current-month" : "other-month"} ${item.isToday ? "today" : ""}`}
                >
                  <div className="calendar-day-num">
                    {format(item.date, "d")}
                  </div>
                  <div className="calendar-day-net-container">
                    {item.netIncome !== undefined && item.netIncome !== 0 && (
                      <div className={`calendar-day-net ${item.netIncome > 0 ? "positive" : "negative"}`}>
                        {item.netIncome > 0 ? "+" : "-"}{yuan(Math.abs(item.netIncome))}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Bottom Block: Timeline */}
        <div style={{ display: "flex", flexDirection: "column", gap: "12px", minHeight: 0 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "0 4px" }}>
            <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "var(--foreground)" }}>本月收支明细</h3>
            <div className="segmented">
              <button className={layoutMode === "card" ? "active" : ""} onClick={() => setLayoutMode("card")} aria-label="卡片布局"><LayoutGrid size={16} /></button>
              <button className={layoutMode === "list" ? "active" : ""} onClick={() => setLayoutMode("list")} aria-label="列表布局"><List size={16} /></button>
            </div>
          </div>
          
          <section className="timeline-panel hide-scrollbar" style={{ flex: 1, overflowY: "auto", paddingBottom: "32px", maxHeight: "calc(100vh - 280px)" }}>
            {isLoading ? (
              <div className="empty-line">正在载入数据</div>
            ) : groupedTransactions.length === 0 ? (
              <div className="empty-line">本月无记录</div>
            ) : (
              <div className="transaction-groups-wrapper">
                {groupedTransactions.map((group) => (
                  <div key={group.dateString}>
                    <div className="transaction-group-title">
                      {group.label}
                    </div>
                    {layoutMode === "list" ? (
                      <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        {group.transactions.map((transaction) => (
                          <article key={transaction.id} className="transaction-card" onClick={() => setEditing(transaction)} style={{ cursor: 'pointer' }}>
                            <div className={transaction.transaction_type === "expense" ? "tx-symbol expense" : "tx-symbol income"}>
                              {getTransactionIcon(transaction, categories) ? (
                                <CategoryIcon name={getTransactionIcon(transaction, categories)!} size={16} />
                              ) : (
                                transaction.transaction_type === "expense" ? <ArrowUp size={16} /> : <ArrowDown size={16} />
                              )}
                            </div>
                            <div className="tx-body">
                              <div className="tx-mainline">
                                <strong>{transaction.merchant || transaction.notes || "未命名交易"}</strong>
                                <b style={{ color: transaction.transaction_type === "expense" ? "var(--danger)" : "var(--success)" }}>
                                  {transaction.transaction_type === "expense" ? "-" : "+"}{yuan(transaction.amount)}
                                </b>
                              </div>
                              <div className="tx-meta">
                                <span>{format(new Date(transaction.transaction_date * 1000), "HH:mm")}</span>
                                {transaction.category_id && <span>{categoryLabelById.get(transaction.category_id) || "未分类"}</span>}
                                <span>{transaction.external_source || "manual"}</span>
                                {transaction.is_excluded && <span>不计收支</span>}
                              </div>
                            </div>
                          </article>
                        ))}
                      </div>
                    ) : (
                      <div className="transaction-grid-cards">
                        {group.transactions.map((transaction) => (
                          <div 
                            key={transaction.id} 
                            onClick={() => setEditing(transaction)}
                            className="transaction-item-card"
                            style={{ cursor: 'pointer' }}
                          >
                            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                               <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                                 <div className={transaction.transaction_type === "expense" ? "tx-symbol expense" : "tx-symbol income"} style={{ width: "32px", height: "32px", flexShrink: 0 }}>
                                   {getTransactionIcon(transaction, categories) ? (
                                     <CategoryIcon name={getTransactionIcon(transaction, categories)!} size={14} />
                                   ) : (
                                     transaction.transaction_type === "expense" ? <ArrowUp size={14} /> : <ArrowDown size={14} />
                                   )}
                                 </div>
                                 <div>
                                   <div style={{ fontWeight: 600, fontSize: "14px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: "120px" }}>{transaction.merchant || transaction.notes || "未命名交易"}</div>
                                   <div style={{ fontSize: "12px", color: "var(--muted)", marginTop: "2px" }}>
                                     {transaction.category_id ? categoryLabelById.get(transaction.category_id) : "未分类"}
                                    </div>
                                 </div>
                               </div>
                               <b style={{ fontSize: "15px", color: transaction.transaction_type === "expense" ? "var(--danger)" : "var(--success)" }}>
                                 {transaction.transaction_type === "expense" ? "-" : "+"}{yuan(transaction.amount)}
                               </b>
                            </div>
                            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "12px", color: "var(--muted)", marginTop: "auto", paddingTop: "12px" }}>
                              <span>{format(new Date(transaction.transaction_date * 1000), "HH:mm")}</span>
                              {transaction.is_excluded && <span style={{ background: "var(--subtle)", padding: "2px 8px", borderRadius: "999px" }}>不计收支</span>}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>

      <button 
        className="fab-button" 
        onClick={openQuickEntry}
        aria-label="记一笔"
      >
        <Plus size={24} />
      </button>

      <AnimatePresence>
        {editing && (
          <TransactionEditor
            transaction={editing}
            onChange={setEditing}
            onSave={save}
            onDelete={remove}
            onClose={() => setEditing(null)}
            accounts={accounts}
            categories={categories}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
