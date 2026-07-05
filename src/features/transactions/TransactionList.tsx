import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, LayoutGrid, List } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { startOfMonth, endOfMonth, addMonths, subMonths, format } from "date-fns";
import { zhCN } from "date-fns/locale";
import { AnimatePresence } from "framer-motion";
import { useAppStore } from "../../stores/appStore";
import { Transaction, TransactionAPI } from "../../tauri-adapter/transactions";
import { yuan } from "../../lib/format";
import { Category } from "../../models";
import { YearMonthPicker } from "../../components/ui/DatePicker";
import { TransactionEditor } from "./TransactionEditor";
import { CategoryIcon } from "../../components/ui/CategoryIcon";

const now = () => Math.floor(Date.now() / 1000);

function getTransactionIcon(t: Transaction, categories: Category[]) {
  const cat = categories.find(c => c.id === t.category_id);
  const parent = cat?.parent_id ? categories.find(c => c.id === cat.parent_id) : cat;
  return cat?.icon || parent?.icon;
}

export default function TransactionList() {
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
    const start = Math.floor(selectedMonth.getTime() / 1000);
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

  const handlePrevMonth = () => setSelectedMonth((m) => subMonths(m, 1));
  const handleNextMonth = () => setSelectedMonth((m) => addMonths(m, 1));
  
  const netIncome = metrics.income - metrics.expense;
  const netColor = netIncome > 0 ? "var(--success)" : netIncome < 0 ? "var(--danger)" : "inherit";

  return (
    <div className="money-flow-page" style={{ padding: "24px", maxWidth: "1000px", margin: "0 auto" }}>
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
        
        <div style={{ width: "80px", display: "flex", justifyContent: "flex-end", gap: "4px" }}>
          <button className={`icon-button ${layoutMode === "card" ? "active" : ""}`} onClick={() => setLayoutMode("card")} style={{ background: layoutMode === "card" ? "var(--surface)" : "transparent" }}><LayoutGrid size={18} /></button>
          <button className={`icon-button ${layoutMode === "list" ? "active" : ""}`} onClick={() => setLayoutMode("list")} style={{ background: layoutMode === "list" ? "var(--surface)" : "transparent" }}><List size={18} /></button>
        </div>
      </header>

      <section style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "16px", marginBottom: "32px" }}>
        <div className="panel panel-pad" style={{ textAlign: "center", padding: "24px" }}>
          <div style={{ color: "var(--muted)", fontSize: "14px", marginBottom: "8px" }}>总收入</div>
          <div style={{ fontSize: "28px", fontWeight: 700, color: "var(--success)" }}>{yuan(metrics.income)}</div>
        </div>
        <div className="panel panel-pad" style={{ textAlign: "center", padding: "24px" }}>
          <div style={{ color: "var(--muted)", fontSize: "14px", marginBottom: "8px" }}>总支出</div>
          <div style={{ fontSize: "28px", fontWeight: 700, color: "var(--danger)" }}>{yuan(metrics.expense)}</div>
        </div>
        <div className="panel panel-pad" style={{ textAlign: "center", padding: "24px" }}>
          <div style={{ color: "var(--muted)", fontSize: "14px", marginBottom: "8px" }}>净收入</div>
          <div style={{ fontSize: "28px", fontWeight: 700, color: netColor }}>{yuan(netIncome)}</div>
        </div>
      </section>

      <section className="timeline-panel hide-scrollbar" style={{ flex: 1, overflowY: "auto", paddingBottom: "100px", padding: "0 4px" }}>
        {isLoading ? (
          <div className="empty-line">正在载入数据</div>
        ) : groupedTransactions.length === 0 ? (
          <div className="empty-line">本月无记录</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            {groupedTransactions.map((group) => (
              <div key={group.dateString}>
                <div style={{ fontSize: "14px", fontWeight: 600, color: "var(--muted)", marginBottom: "12px", paddingLeft: "4px" }}>
                  {group.label}
                </div>
                
                {layoutMode === "list" ? (
                  <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                    {group.transactions.map((transaction) => (
                      <article key={transaction.id} className="transaction-card" onClick={() => setEditing(transaction)} style={{ cursor: "pointer", gridTemplateColumns: "auto 1fr" }}>
                        <div className={transaction.transaction_type === "expense" ? "tx-symbol expense" : "tx-symbol income"}>
                          {getTransactionIcon(transaction, categories) ? (
                            <CategoryIcon name={getTransactionIcon(transaction, categories)} size={16} />
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
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "12px" }}>
                    {group.transactions.map((transaction) => (
                      <div 
                        key={transaction.id} 
                        onClick={() => setEditing(transaction)}
                        className="panel hover-transform"
                        style={{ padding: "16px", cursor: "pointer", display: "flex", flexDirection: "column", gap: "12px", transition: "transform 0.15s, box-shadow 0.15s" }}
                      >
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                           <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                             <div className={transaction.transaction_type === "expense" ? "tx-symbol expense" : "tx-symbol income"} style={{ width: "32px", height: "32px", flexShrink: 0 }}>
                               {getTransactionIcon(transaction, categories) ? (
                                 <CategoryIcon name={getTransactionIcon(transaction, categories)} size={14} />
                               ) : (
                                 transaction.transaction_type === "expense" ? <ArrowUp size={14} /> : <ArrowDown size={14} />
                               )}
                             </div>
                             <div>
                               <div style={{ fontWeight: 600, fontSize: "15px" }}>{transaction.merchant || transaction.notes || "未命名交易"}</div>
                               <div style={{ fontSize: "12px", color: "var(--muted)", marginTop: "2px" }}>
                                 {transaction.category_id ? categoryLabelById.get(transaction.category_id) : "未分类"}
                               </div>
                             </div>
                           </div>
                           <b style={{ fontSize: "16px", color: transaction.transaction_type === "expense" ? "var(--danger)" : "var(--success)" }}>
                             {transaction.transaction_type === "expense" ? "-" : "+"}{yuan(transaction.amount)}
                           </b>
                        </div>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "12px", color: "var(--muted)" }}>
                          <span>{format(new Date(transaction.transaction_date * 1000), "HH:mm")}</span>
                          {transaction.is_excluded && <span style={{ background: "color-mix(in srgb, var(--muted) 20%, transparent)", padding: "2px 6px", borderRadius: "4px" }}>不计收支</span>}
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
