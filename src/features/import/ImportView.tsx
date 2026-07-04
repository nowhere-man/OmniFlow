import { useMemo, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { CheckCircle2, FileUp, Loader2, ShieldAlert } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useAppStore } from "../../stores/appStore";
import { ImportPreviewItem, TransactionAPI } from "../../tauri-adapter/transactions";
import { Category } from "../../models";
import { shortDate, yuan } from "../../lib/format";

export default function ImportView() {
  const { currentLedgerId, accounts, fetchTransactions } = useAppStore();
  const [categories, setCategories] = useState<Category[]>([]);
  const [accountId, setAccountId] = useState("");
  const [preview, setPreview] = useState<ImportPreviewItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const targetAccountId = accountId || accounts.find((account) => account.account_type === "cash")?.id || accounts[0]?.id || "";
  const counts = useMemo(() => ({
    selected: preview.filter((item) => item.selected).length,
    fuzzy: preview.filter((item) => item.duplicate_status === "fuzzy").length,
    absolute: preview.filter((item) => item.duplicate_status === "absolute").length,
  }), [preview]);

  async function ensureCategories() {
    if (categories.length > 0) return categories;
    const data = await invoke<Category[]>("list_categories");
    setCategories(data);
    return data;
  }

  async function selectFile() {
    if (!currentLedgerId || !targetAccountId) {
      setMessage("请先准备账本和账户");
      return;
    }
    const selected = await open({
      multiple: false,
      filters: [{ name: "Bills", extensions: ["csv", "xls", "xlsx", "json"] }],
    });
    if (!selected) return;

    setLoading(true);
    setMessage(null);
    try {
      await ensureCategories();
      const filePath = Array.isArray(selected) ? selected[0] : selected;
      const items = await TransactionAPI.parseAndPreview(filePath as string, currentLedgerId, targetAccountId);
      setPreview(items);
      setMessage(`已生成 ${items.length} 条预览，绝对重复会自动锁定`);
    } catch (error: any) {
      setMessage(`解析失败：${error?.message || error}`);
    } finally {
      setLoading(false);
    }
  }

  async function confirm() {
    if (!currentLedgerId || !targetAccountId) return;
    setLoading(true);
    try {
      const inserted = await TransactionAPI.confirmImport(currentLedgerId, targetAccountId, preview);
      setMessage(`已确认入账 ${inserted} 条交易`);
      setPreview([]);
      await fetchTransactions();
    } catch (error: any) {
      setMessage(`入账失败：${error?.message || error}`);
    } finally {
      setLoading(false);
    }
  }

  function updateItem(previewId: string, patch: Partial<ImportPreviewItem>) {
    setPreview((items) => items.map((item) => item.preview_id === previewId ? { ...item, ...patch } : item));
  }

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <div className="eyebrow">import flow</div>
          <h1 className="page-title">先预览，再放心入账</h1>
        </div>
        <div className="toolbar">
          <select className="select-field" value={accountId} onChange={(event) => setAccountId(event.target.value)}>
            <option value="">自动选择现金账户</option>
            {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
          </select>
          <button className="primary-button" onClick={selectFile} disabled={loading}>
            {loading ? <Loader2 size={17} className="animate-spin" /> : <FileUp size={17} />}
            选择账单
          </button>
        </div>
      </section>

      {message && <div className="panel panel-pad">{message}</div>}

      <section className="metric-grid">
        <div className="metric-tile"><div className="metric-label"><CheckCircle2 size={16} />待入账</div><strong>{counts.selected}</strong></div>
        <div className="metric-tile"><div className="metric-label"><ShieldAlert size={16} />疑似重复</div><strong>{counts.fuzzy}</strong></div>
        <div className="metric-tile"><div className="metric-label"><ShieldAlert size={16} />绝对重复</div><strong>{counts.absolute}</strong></div>
        <div className="metric-tile"><div className="metric-label">预览总数</div><strong>{preview.length}</strong></div>
      </section>

      <section className="panel overflow-x-auto">
        {preview.length === 0 ? (
          <div className="panel-pad text-center text-[var(--muted)]">选择支付宝、微信、京东、美团、建设银行或青子记账文件后，这里会出现可编辑预览。</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>入账</th>
                <th>状态</th>
                <th>日期</th>
                <th>商户/备注</th>
                <th>金额</th>
                <th>分类</th>
                <th>账户</th>
                <th>标签</th>
                <th>排除</th>
              </tr>
            </thead>
            <tbody>
              {preview.map((item) => (
                <tr key={item.preview_id}>
                  <td>
                    <input type="checkbox" checked={item.selected} disabled={item.duplicate_status === "absolute"} onChange={(event) => updateItem(item.preview_id, { selected: event.target.checked })} />
                  </td>
                  <td><span className={`status-chip chip-${item.duplicate_status}`}>{item.duplicate_status}</span></td>
                  <td>{shortDate(item.transaction_date)}</td>
                  <td>
                    <input className="field w-44" value={item.notes || item.merchant || ""} onChange={(event) => updateItem(item.preview_id, { notes: event.target.value })} />
                  </td>
                  <td className={item.transaction_type === "expense" ? "text-[var(--expense)]" : "text-[var(--income)]"}>{yuan(item.amount)}</td>
                  <td>
                    <select className="select-field w-36" value={item.category_id || ""} onChange={(event) => updateItem(item.preview_id, { category_id: event.target.value || null })}>
                      <option value="">未分类</option>
                      {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
                    </select>
                  </td>
                  <td>
                    <select className="select-field w-32" value={item.account_id} onChange={(event) => updateItem(item.preview_id, { account_id: event.target.value })}>
                      {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                    </select>
                  </td>
                  <td>
                    <input className="field w-36" value={item.tags.join(",")} onChange={(event) => updateItem(item.preview_id, { tags: event.target.value.split(",").map((tag) => tag.trim()).filter(Boolean) })} />
                  </td>
                  <td>
                    <input type="checkbox" checked={item.is_excluded} onChange={(event) => updateItem(item.preview_id, { is_excluded: event.target.checked })} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {preview.length > 0 && (
        <div className="toolbar justify-end">
          <button className="ghost-button" onClick={() => setPreview([])}>清空预览</button>
          <button className="primary-button" onClick={confirm} disabled={loading || counts.selected === 0}>
            {loading ? <Loader2 size={17} className="animate-spin" /> : <CheckCircle2 size={17} />}
            确认入账
          </button>
        </div>
      )}
    </div>
  );
}
