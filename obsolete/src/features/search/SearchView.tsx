import { useEffect, useState, useMemo } from "react";
import { Search, Filter, ChevronUp } from "lucide-react";
import { useAppStore } from "../../stores/appStore";
import { SearchResult, TransactionAPI, TransactionFilter } from "../../tauri-adapter/transactions";
import { shortDate, yuan } from "../../lib/format";
import { Select } from "../../components/ui/Select";
import { DatePicker } from "../../components/ui/DatePicker";
import { Category } from "../../models";
import { invoke } from "../../tauri-adapter/invoke";

export default function SearchView() {
  const { currentLedgerId, ledgers, accounts, fetchInitialData } = useAppStore();
  const [filter, setFilter] = useState<TransactionFilter>({});
  const [result, setResult] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [transactionType, setTransactionType] = useState<"all" | "expense" | "income">("all");
  const [searchLedgerId, setSearchLedgerId] = useState<string>("");
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [minAmountStr, setMinAmountStr] = useState("");
  const [maxAmountStr, setMaxAmountStr] = useState("");

  useEffect(() => {
    fetchInitialData();
    invoke<Category[]>("list_categories").then(setCategories).catch(() => setCategories([]));
  }, [fetchInitialData]);

  useEffect(() => {
    if (currentLedgerId && !searchLedgerId) {
      setSearchLedgerId(currentLedgerId);
    }
  }, [currentLedgerId, searchLedgerId]);

  function resetFilters() {
    setFilter({});
    setSearchLedgerId(currentLedgerId || "");
    setTransactionType("all");
    setMinAmountStr("");
    setMaxAmountStr("");
    setResult(null);
  }

  async function runSearch(nextFilter = filter) {
    if (!searchLedgerId) return;
    setLoading(true);
    try {
      const data = await TransactionAPI.searchTransactions(searchLedgerId, nextFilter);
      
      // Frontend-side filtering for transaction_type since the backend filter struct lacks it
      if (transactionType !== "all") {
        data.transactions = data.transactions.filter((t) => t.transaction_type === transactionType);
        data.total_income = data.transactions.filter((t) => t.transaction_type === "income").reduce((sum, t) => sum + t.amount, 0);
        data.total_expense = data.transactions.filter((t) => t.transaction_type === "expense").reduce((sum, t) => sum + t.amount, 0);
      }
      
      setResult(data);
    } finally {
      setLoading(false);
    }
  }

  function patch(patch: TransactionFilter) {
    setFilter((prev) => ({ ...prev, ...patch }));
  }

  const primaryCategories = useMemo(() => categories.filter((c) => !c.parent_id), [categories]);
  const secondaryCategories = useMemo(() => {
    if (!filter.parent_category_id) return categories.filter((c) => c.parent_id);
    return categories.filter((c) => c.parent_id === filter.parent_category_id);
  }, [categories, filter.parent_category_id]);

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <div className="eyebrow">搜索</div>
          <h1 className="page-title">搜索交易</h1>
        </div>
      </section>

      <section className="panel panel-pad search-panel">
        <div className="search-bar-row">
          <div className="search-input-shell">
            <Search size={18} className="search-input-icon" />
            <input 
              className="field search-input" 
              placeholder="搜索商户、备注或关键字..." 
              value={filter.keyword || ""} 
              onChange={(event) => patch({ keyword: event.target.value || null })} 
              onKeyDown={(e) => e.key === "Enter" && runSearch()}
            />
          </div>
          <button className="ghost-button search-filter-button" onClick={() => setShowAdvanced(!showAdvanced)} aria-label="展开筛选">
            {showAdvanced ? <ChevronUp size={20} /> : <Filter size={20} />}
          </button>
          <button className="primary-button search-submit-button" onClick={() => runSearch()} disabled={loading}>
            搜索
          </button>
        </div>

        {showAdvanced && (
          <div className="advanced-search-rows">
            <div className="advanced-search-row">
              <div className="filter-field filter-wide">
                <Select
                  value={searchLedgerId}
                  onChange={(val) => setSearchLedgerId(val)}
                  options={ledgers.map((ledger) => ({ value: ledger.id, label: ledger.name }))}
                />
              </div>
              <div className="filter-field filter-wide">
                <Select
                  value={filter.account_id || ""}
                  onChange={(val) => patch({ account_id: val || null })}
                  options={[
                    { value: "", label: "全部账户" },
                    ...accounts.map((account) => ({ value: account.id, label: account.name })),
                  ]}
                />
              </div>
            </div>
            
            <div className="advanced-search-row">
              <div className="filter-field filter-narrow">
                <Select
                  value={transactionType}
                  onChange={(val) => setTransactionType(val as "all" | "expense" | "income")}
                  options={[
                    { value: "all", label: "全部收支" },
                    { value: "expense", label: "仅支出" },
                    { value: "income", label: "仅收入" },
                  ]}
                />
              </div>
              <div className="filter-field">
                <Select
                  value={filter.parent_category_id || ""}
                  onChange={(val) => patch({ parent_category_id: val || null, category_id: null })}
                  options={[
                    { value: "", label: "所有一级分类" },
                    ...primaryCategories.map((c) => ({ value: c.id, label: c.name })),
                  ]}
                />
              </div>
              <div className="filter-field">
                <Select
                  value={filter.category_id || ""}
                  onChange={(val) => patch({ category_id: val || null })}
                  options={[
                    { value: "", label: "所有二级分类" },
                    ...secondaryCategories.map((c) => ({ value: c.id, label: c.name })),
                  ]}
                />
              </div>
            </div>

            <div className="advanced-search-row">
              <div className="filter-field">
                <DatePicker
                  value={filter.start_date || null}
                  onChange={(val) => patch({ start_date: val })}
                  placeholder="开始日期"
                />
              </div>
              <div className="filter-field">
                <DatePicker
                  value={filter.end_date || null}
                  onChange={(val) => patch({ end_date: val })}
                  placeholder="结束日期"
                />
              </div>
            </div>

            <div className="advanced-search-row">
              <div className="filter-field filter-narrow">
                <input 
                  className="field no-spin" 
                  type="text" 
                  placeholder="最小金额" 
                  value={minAmountStr}
                  onChange={(e) => {
                    const val = e.target.value.replace(/[^\d.]/g, '');
                    setMinAmountStr(val);
                    patch({ min_amount: val ? Number(val) : null });
                  }} 
                />
              </div>
              <div className="filter-field filter-narrow">
                <input 
                  className="field no-spin" 
                  type="text" 
                  placeholder="最大金额" 
                  value={maxAmountStr}
                  onChange={(e) => {
                    const val = e.target.value.replace(/[^\d.]/g, '');
                    setMaxAmountStr(val);
                    patch({ max_amount: val ? Number(val) : null });
                  }} 
                />
              </div>
              <div className="filter-field filter-wide">
                <input 
                  className="field" 
                  placeholder="指定标签 (支持模糊)" 
                  value={filter.tag || ""} 
                  onChange={(event) => patch({ tag: event.target.value || null })} 
                />
              </div>
            </div>

            <div className="advanced-search-actions">
              <button className="ghost-button" onClick={resetFilters}>
                重置选项
              </button>
            </div>
          </div>
        )}
      </section>

      {result && (
        <section className="metric-grid">
          <div className="metric-tile"><div className="metric-label">结果数</div><strong>{result.transactions.length}</strong></div>
          <div className="metric-tile"><div className="metric-label">收入汇总</div><strong className="amount-income">{yuan(result.total_income)}</strong></div>
          <div className="metric-tile"><div className="metric-label">支出汇总</div><strong className="amount-expense">{yuan(result.total_expense)}</strong></div>
          <div className="metric-tile"><div className="metric-label">净额</div><strong>{yuan(result.total_income - result.total_expense)}</strong></div>
        </section>
      )}

      <section className="panel table-scroll">
        {!result ? (
          <div className="panel-pad empty-copy">组合条件后点击搜索，寻找你需要的交易记录。</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>商户/备注</th>
                <th>金额</th>
                <th>标签</th>
                <th>排除</th>
              </tr>
            </thead>
            <tbody>
              {result.transactions.map((transaction) => (
                <tr key={transaction.id}>
                  <td>{shortDate(transaction.transaction_date)}</td>
                  <td><strong>{transaction.merchant || "未命名交易"}</strong><div className="table-note">{transaction.notes || "-"}</div></td>
                  <td className={transaction.transaction_type === "expense" ? "amount-expense" : "amount-income"}>{yuan(transaction.amount)}</td>
                  <td>{transaction.tags.join(" / ") || "-"}</td>
                  <td>{transaction.is_excluded ? "是" : "否"}</td>
                </tr>
              ))}
              {result.transactions.length === 0 && <tr><td colSpan={5} className="table-empty">没有匹配交易</td></tr>}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
