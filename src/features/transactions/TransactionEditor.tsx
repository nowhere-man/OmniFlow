import { useState } from "react";
import { Save, Trash2, X } from "lucide-react";
import { motion } from "framer-motion";
import { Transaction, Account } from "../../tauri-adapter/transactions";
import { Category } from "../../models";
import { Select } from "../../components/ui/Select";
import { DatePicker } from "../../components/ui/DatePicker";

interface TransactionEditorProps {
  transaction: Transaction;
  onChange: (t: Transaction) => void;
  onSave: () => void;
  onDelete?: () => void;
  onClose: () => void;
  accounts: Account[];
  categories: Category[];
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

export function TransactionEditor({ transaction, onChange, onSave, onDelete, onClose, accounts, categories }: TransactionEditorProps) {
  const [confirmDelete, setConfirmDelete] = useState(false);

  return (
    <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, background: "rgba(0,0,0,0.5)", zIndex: 1000, display: "flex", alignItems: "center", justifyContent: "center" }} onClick={onClose}>
      <motion.div 
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
        transition={{ duration: 0.2 }}
        onClick={(e) => e.stopPropagation()}
        className="panel"
        style={{ width: "420px", padding: "24px", display: "flex", flexDirection: "column", gap: "16px", background: "var(--background)", border: "1px solid var(--border)", borderRadius: "12px", boxShadow: "0 20px 40px rgba(0,0,0,0.3)" }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px" }}>
          <h2 style={{ margin: 0, fontSize: "18px" }}>{onDelete ? "编辑明细" : "记一笔"}</h2>
          <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>

        <div className="segmented">
          <button className={transaction.transaction_type === "expense" ? "active" : ""} onClick={() => onChange({ ...transaction, transaction_type: "expense" })}>支出</button>
          <button className={transaction.transaction_type === "income" ? "active" : ""} onClick={() => onChange({ ...transaction, transaction_type: "income" })}>收入</button>
        </div>
        
        <input className="money-input" type="number" min="0" step="0.01" value={transaction.amount || ""} placeholder="0.00" onChange={(event) => onChange({ ...transaction, amount: Number(event.target.value) })} style={{ fontSize: "32px", textAlign: "center", padding: "12px", border: "none", background: "transparent", borderBottom: "2px solid var(--border)", outline: "none", color: "var(--foreground)" }} />
        
        <input className="field" value={transaction.merchant || ""} placeholder="商户" onChange={(event) => onChange({ ...transaction, merchant: event.target.value })} />
        <input className="field" value={transaction.notes || ""} placeholder="备注" onChange={(event) => onChange({ ...transaction, notes: event.target.value })} />
        
        <DatePicker 
          value={transaction.transaction_date}
          showTime={true}
          onChange={(val) => {
            if (val) onChange({ ...transaction, transaction_date: val });
          }}
        />
        
        <Select
          value={transaction.account_id}
          onChange={(val) => onChange({ ...transaction, account_id: val })}
          options={accounts.map((account) => ({ value: account.id, label: account.name }))}
        />
        
        <Select
          value={transaction.category_id || ""}
          onChange={(val) => onChange({ ...transaction, category_id: val || null })}
          options={[
            { value: "", label: "未分类" },
            ...categoryOptions(categories).map((opt) => ({ value: opt.id, label: opt.label }))
          ]}
        />

        <label style={{ display: "flex", alignItems: "center", gap: "8px", marginTop: "4px", cursor: "pointer" }}>
          <input type="checkbox" checked={transaction.is_excluded} onChange={(event) => onChange({ ...transaction, is_excluded: event.target.checked })} />
          <span>不计入各项收支统计</span>
        </label>
        
        <div style={{ display: "flex", flexDirection: "column", gap: "8px", marginTop: "8px" }}>
           <button className="primary-button" onClick={onSave} style={{ padding: "12px", fontSize: "15px" }}><Save size={17} />保存</button>
           
           {onDelete && (confirmDelete ? (
             <div style={{ display: "flex", gap: "8px" }}>
               <button className="primary-button" onClick={onDelete} style={{ flex: 1, padding: "12px", fontSize: "15px", background: "var(--danger)" }}>确认删除</button>
               <button className="ghost-button" onClick={() => setConfirmDelete(false)} style={{ flex: 1, padding: "12px", fontSize: "15px", border: "1px solid var(--border)" }}>取消</button>
             </div>
           ) : (
             <button className="ghost-button" onClick={() => setConfirmDelete(true)} style={{ padding: "12px", fontSize: "15px", color: "var(--danger)", border: "1px solid color-mix(in srgb, var(--danger) 20%, transparent)" }}><Trash2 size={17} />删除明细</button>
           ))}
        </div>
      </motion.div>
    </div>
  );
}
