import { useEffect, useState } from "react";
import { Search } from "lucide-react";
import { useAppStore } from "../../stores/appStore";
import { SearchResult, TransactionAPI, TransactionFilter } from "../../tauri-adapter/transactions";
import { shortDate, yuan } from "../../lib/format";
import { Select } from "../../components/ui/Select";

export default function SearchView() {
  const { currentLedgerId, accounts, fetchInitialData } = useAppStore();
  const [filter, setFilter] = useState<TransactionFilter>({});
  const [result, setResult] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchInitialData();
  }, [fetchInitialData]);

  async function runSearch(nextFilter = filter) {
    if (!currentLedgerId) return;
    setLoading(true);
    try {
      setResult(await TransactionAPI.searchTransactions(currentLedgerId, nextFilter));
    } finally {
      setLoading(false);
    }
  }

  function patch(patch: TransactionFilter) {
    const next = { ...filter, ...patch };
    setFilter(next);
  }

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <div className="eyebrow">搜索</div>
          <h1 className="page-title">搜索交易</h1>
        </div>
        <button className="primary-button" onClick={() => runSearch()} disabled={loading}><Search size={17} />搜索</button>
      </section>

      <section className="panel panel-pad">
        <div className="toolbar">
          <input className="field" placeholder="商户或备注" value={filter.keyword || ""} onChange={(event) => patch({ keyword: event.target.value || null })} />
          <Select
            value={filter.account_id || ""}
            onChange={(val) => patch({ account_id: val || null })}
            options={[
              { value: "", label: "全部账户" },
              ...accounts.map((account) => ({ value: account.id, label: account.name })),
            ]}
          />
          <input className="field field-xs" type="number" placeholder="最小金额" onChange={(event) => patch({ min_amount: event.target.value ? Number(event.target.value) : null })} />
          <input className="field field-xs" type="number" placeholder="最大金额" onChange={(event) => patch({ max_amount: event.target.value ? Number(event.target.value) : null })} />
          <input className="field" placeholder="标签" value={filter.tag || ""} onChange={(event) => patch({ tag: event.target.value || null })} />
        </div>
      </section>

      {result && (
        <section className="metric-grid">
          <div className="metric-tile"><div className="metric-label">结果数</div><strong>{result.transactions.length}</strong></div>
          <div className="metric-tile"><div className="metric-label">收入汇总</div><strong>{yuan(result.total_income)}</strong></div>
          <div className="metric-tile"><div className="metric-label">支出汇总</div><strong>{yuan(result.total_expense)}</strong></div>
          <div className="metric-tile"><div className="metric-label">净额</div><strong>{yuan(result.total_income - result.total_expense)}</strong></div>
        </section>
      )}

      <section className="panel table-scroll">
        {!result ? (
          <div className="panel-pad empty-copy">组合条件后点击搜索，结果和汇总都由后端计算。</div>
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
