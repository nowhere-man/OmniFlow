import { useEffect, useMemo, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { CheckCircle2, FileUp, Loader2, Plus, ShieldAlert, Sparkles, Trash2 } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useAppStore } from "../../stores/appStore";
import { ImportPreviewItem, Rule, TransactionAPI } from "../../tauri-adapter/transactions";
import { Category } from "../../models";
import { shortDate, yuan } from "../../lib/format";
import { Select } from "../../components/ui/Select";

const now = () => Math.floor(Date.now() / 1000);

type RuleDraft = {
  name: string;
  matchField: "merchant_keyword" | "notes_keyword" | "regex";
  keyword: string;
  minAmount: string;
  maxAmount: string;
  categoryId: string;
  accountId: string;
  tags: string;
  notes: string;
  exclude: boolean;
  skip: boolean;
};

type RuleConditionDraft = {
  match_type: "merchant_keyword" | "notes_keyword" | "regex" | "amount_range";
  value: string;
};

const emptyRuleDraft: RuleDraft = {
  name: "",
  matchField: "merchant_keyword",
  keyword: "",
  minAmount: "",
  maxAmount: "",
  categoryId: "",
  accountId: "",
  tags: "",
  notes: "",
  exclude: false,
  skip: false,
};

export default function ImportView() {
  const { currentLedgerId, accounts, fetchTransactionsPage } = useAppStore();
  const [categories, setCategories] = useState<Category[]>([]);
  const [accountId, setAccountId] = useState("");
  const [preview, setPreview] = useState<ImportPreviewItem[]>([]);
  const [rules, setRules] = useState<Rule[]>([]);
  const [draft, setDraft] = useState<RuleDraft>(emptyRuleDraft);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const targetAccountId = accountId || accounts.find((account) => account.account_type === "cash")?.id || accounts[0]?.id || "";
  const counts = useMemo(() => ({
    selected: preview.filter((item) => item.selected).length,
    fuzzy: preview.filter((item) => item.duplicate_status === "fuzzy").length,
    absolute: preview.filter((item) => item.duplicate_status === "absolute").length,
  }), [preview]);

  useEffect(() => {
    ensureCategories();
    loadRules();
  }, []);

  async function ensureCategories() {
    if (categories.length > 0) return categories;
    const data = await invoke<Category[]>("list_categories");
    setCategories(data);
    return data;
  }

  async function loadRules() {
    setRules(await TransactionAPI.listRules());
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
      await fetchTransactionsPage(0, 80);
    } catch (error: any) {
      setMessage(`入账失败：${error?.message || error}`);
    } finally {
      setLoading(false);
    }
  }

  async function saveRule() {
    const keyword = draft.keyword.trim();
    if (!keyword) {
      setMessage("请先填写规则命中内容");
      return;
    }
    const conditions: RuleConditionDraft[] = [{ match_type: draft.matchField, value: keyword }];
    if (draft.minAmount || draft.maxAmount) {
      conditions.push({
        match_type: "amount_range",
        value: `${draft.minAmount || 0},${draft.maxAmount || Number.MAX_SAFE_INTEGER}`,
      });
    }
    const actions = buildRuleActions(draft);
    if (actions.length === 0) {
      setMessage("请至少选择一个规则动作");
      return;
    }
    const timestamp = now();
    await TransactionAPI.createRule({
      id: crypto.randomUUID(),
      name: draft.name.trim() || `导入规则 · ${keyword}`,
      priority: 50,
      match_condition: JSON.stringify(conditions),
      action: JSON.stringify(actions),
      created_at: timestamp,
      updated_at: timestamp,
      deleted_at: null,
    });
    setDraft(emptyRuleDraft);
    await loadRules();
    setMessage("规则已保存，下一次预览会自动应用");
  }

  async function deleteRule(id: string) {
    await TransactionAPI.deleteRule(id);
    await loadRules();
  }

  async function learnFromPreview() {
    const learnable = preview.filter((item) => item.selected && (item.merchant || item.notes));
    if (learnable.length === 0) {
      setMessage("没有可学习的预览项");
      return;
    }
    const timestamp = now();
    const rulesToCreate = learnable
      .filter((item) => item.category_id || item.account_id || item.is_excluded || item.tags.length > 0)
      .map((item) => ({
        id: crypto.randomUUID(),
        name: `学习 · ${item.merchant || item.notes || "未命名"}`,
        priority: 30,
        match_condition: JSON.stringify([{
          match_type: item.merchant ? "merchant_keyword" : "notes_keyword",
          value: item.merchant || item.notes || "",
        }]),
        action: JSON.stringify([
          ...(item.category_id ? [{ action_type: "set_category", value: item.category_id }] : []),
          ...(item.account_id ? [{ action_type: "set_account", value: item.account_id }] : []),
          ...(item.is_excluded ? [{ action_type: "exclude", value: "true" }] : []),
          ...item.tags.map((tag) => ({ action_type: "add_tag", value: tag })),
        ]),
        created_at: timestamp,
        updated_at: timestamp,
        deleted_at: null,
      }));
    if (rulesToCreate.length === 0) {
      setMessage("先在预览里调整分类、账户、标签或排除状态，再学习");
      return;
    }
    await Promise.all(rulesToCreate.map((rule) => TransactionAPI.createRule(rule)));
    await loadRules();
    setMessage(`已学习 ${rulesToCreate.length} 条规则，后续导入会自动填入`);
  }

  function updateItem(previewId: string, patch: Partial<ImportPreviewItem>) {
    setPreview((items) => items.map((item) => item.preview_id === previewId ? { ...item, ...patch } : item));
  }

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <div className="eyebrow">导入</div>
          <h1 className="page-title">导入账单</h1>
        </div>
        <div className="toolbar">
          <Select
            value={accountId}
            onChange={(val) => setAccountId(val)}
            options={[
              { value: "", label: "自动选择现金账户" },
              ...accounts.map((account) => ({ value: account.id, label: account.name })),
            ]}
          />
          <button className="primary-button" onClick={selectFile} disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <FileUp size={17} />}
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

      <section className="import-workbench">
        <div className="panel panel-pad">
          <div className="panel-head">
            <h2>导入规则</h2>
            <span className="panel-count">{rules.length} 条</span>
          </div>
          <div className="rule-list">
            {rules.length === 0 ? (
              <div className="compact-empty">暂无规则。可以手动创建，也可以从预览学习。</div>
            ) : rules.map((rule) => (
              <div className="rule-row" key={rule.id}>
                <div>
                  <strong>{rule.name}</strong>
                  <small>{describeRule(rule)}</small>
                </div>
                <button className="icon-button danger-icon" onClick={() => deleteRule(rule.id)} aria-label="删除规则">
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
          </div>
        </div>

        <div className="panel panel-pad">
          <div className="panel-head">
            <h2>新建规则</h2>
            <button className="ghost-button" onClick={learnFromPreview} disabled={preview.length === 0}>
              <Sparkles size={16} /> 从预览学习
            </button>
          </div>
          <div className="rule-form">
            <input className="field" placeholder="规则名称" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
            <div className="form-grid-two">
              <Select
                value={draft.matchField}
                onChange={(val) => setDraft({ ...draft, matchField: val as RuleDraft["matchField"] })}
                options={[
                  { value: "merchant_keyword", label: "商户包含" },
                  { value: "notes_keyword", label: "备注包含" },
                  { value: "regex", label: "正则匹配" },
                ]}
              />
              <input className="field" placeholder="命中内容" value={draft.keyword} onChange={(event) => setDraft({ ...draft, keyword: event.target.value })} />
            </div>
            <div className="form-grid-two">
              <input className="field" type="number" placeholder="最小金额" value={draft.minAmount} onChange={(event) => setDraft({ ...draft, minAmount: event.target.value })} />
              <input className="field" type="number" placeholder="最大金额" value={draft.maxAmount} onChange={(event) => setDraft({ ...draft, maxAmount: event.target.value })} />
            </div>
            <div className="form-grid-two">
              <Select
                value={draft.categoryId}
                onChange={(val) => setDraft({ ...draft, categoryId: val })}
                options={[
                  { value: "", label: "不改分类" },
                  ...categories.map((category) => ({ value: category.id, label: category.name })),
                ]}
              />
              <Select
                value={draft.accountId}
                onChange={(val) => setDraft({ ...draft, accountId: val })}
                options={[
                  { value: "", label: "不改账户" },
                  ...accounts.map((account) => ({ value: account.id, label: account.name })),
                ]}
              />
            </div>
            <input className="field" placeholder="添加标签，用逗号分隔" value={draft.tags} onChange={(event) => setDraft({ ...draft, tags: event.target.value })} />
            <input className="field" placeholder="覆盖备注，可留空" value={draft.notes} onChange={(event) => setDraft({ ...draft, notes: event.target.value })} />
            <div className="check-grid">
              <label className="check-row"><input type="checkbox" checked={draft.exclude} onChange={(event) => setDraft({ ...draft, exclude: event.target.checked })} /> 不计入收支</label>
              <label className="check-row"><input type="checkbox" checked={draft.skip} onChange={(event) => setDraft({ ...draft, skip: event.target.checked })} /> 命中后不导入</label>
            </div>
            <button className="primary-button" onClick={saveRule}><Plus size={16} />保存规则</button>
          </div>
        </div>
      </section>

      <section className="panel table-scroll">
        {preview.length === 0 ? (
          <div className="panel-pad empty-copy">选择支付宝、微信、京东、美团、建设银行或青子记账文件后，这里会出现可编辑预览。</div>
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
                    <input className="field field-md" value={item.notes || item.merchant || ""} onChange={(event) => updateItem(item.preview_id, { notes: event.target.value })} />
                  </td>
                  <td className={item.transaction_type === "expense" ? "amount-expense" : "amount-income"}>{yuan(item.amount)}</td>
                  <td>
                    <Select
                      className="field-sm"
                      value={item.category_id || ""}
                      onChange={(val) => updateItem(item.preview_id, { category_id: val || null })}
                      options={[
                        { value: "", label: "未分类" },
                        ...categories.map((category) => ({ value: category.id, label: category.name })),
                      ]}
                    />
                  </td>
                  <td>
                    <Select
                      className="field-sm"
                      value={item.account_id}
                      onChange={(val) => updateItem(item.preview_id, { account_id: val })}
                      options={accounts.map((account) => ({ value: account.id, label: account.name }))}
                    />
                  </td>
                  <td>
                    <input className="field field-sm" value={item.tags.join(",")} onChange={(event) => updateItem(item.preview_id, { tags: event.target.value.split(",").map((tag) => tag.trim()).filter(Boolean) })} />
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
        <div className="toolbar toolbar-end">
          <button className="ghost-button" onClick={() => setPreview([])}>清空预览</button>
          <button className="primary-button" onClick={confirm} disabled={loading || counts.selected === 0}>
            {loading ? <Loader2 size={17} className="spin" /> : <CheckCircle2 size={17} />}
            确认入账
          </button>
        </div>
      )}
    </div>
  );
}

function buildRuleActions(draft: RuleDraft) {
  return [
    ...(draft.categoryId ? [{ action_type: "set_category", value: draft.categoryId }] : []),
    ...(draft.accountId ? [{ action_type: "set_account", value: draft.accountId }] : []),
    ...(draft.tags ? draft.tags.split(",").map((tag) => tag.trim()).filter(Boolean).map((tag) => ({ action_type: "add_tag", value: tag })) : []),
    ...(draft.notes.trim() ? [{ action_type: "set_notes", value: draft.notes.trim() }] : []),
    ...(draft.exclude ? [{ action_type: "exclude", value: "true" }] : []),
    ...(draft.skip ? [{ action_type: "skip", value: "true" }] : []),
  ];
}

function describeRule(rule: Rule) {
  try {
    const conditions = JSON.parse(rule.match_condition) as Array<{ match_type: string; value: string }>;
    const actions = JSON.parse(rule.action) as Array<{ action_type: string; value: string }>;
    return `${conditions.map((item) => `${labelCondition(item.match_type)} ${item.value}`).join("，")} -> ${actions.map((item) => labelAction(item.action_type)).join("，")}`;
  } catch {
    return "规则内容无法解析";
  }
}

function labelCondition(type: string) {
  if (type === "merchant_keyword") return "商户包含";
  if (type === "notes_keyword") return "备注包含";
  if (type === "amount_range") return "金额";
  if (type === "regex") return "正则";
  return type;
}

function labelAction(type: string) {
  if (type === "set_category") return "分类";
  if (type === "set_account") return "账户";
  if (type === "add_tag") return "标签";
  if (type === "set_notes") return "备注";
  if (type === "exclude") return "不计收支";
  if (type === "skip") return "不导入";
  return type;
}
