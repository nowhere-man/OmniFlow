import { useState } from "react";
import { Save, Trash2, X } from "lucide-react";
import { motion } from "framer-motion";
import { Transaction, Account } from "../../tauri-adapter/transactions";
import { Category } from "../../models";
import { Select } from "../../components/ui/Select";
import { DatePicker } from "../../components/ui/DatePicker";
import { CategoryIcon } from "../../components/ui/CategoryIcon";

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
    { 
      id: parent.id, 
      label: <div className="select-option-label">{parent.icon && <CategoryIcon name={parent.icon} size={14} />} {parent.name}</div> 
    },
    ...(childrenByParent.get(parent.id) || []).map((child) => ({ 
      id: child.id, 
      label: <div className="select-option-label child"><span aria-hidden="true" /> {parent.name} / {child.name}</div> 
    })),
  ]);
}

export function TransactionEditor({ transaction, onChange, onSave, onDelete, onClose, accounts, categories }: TransactionEditorProps) {
  const [confirmDelete, setConfirmDelete] = useState(false);

  return (
    <div className="modal-overlay transaction-editor-overlay" onClick={onClose}>
      <motion.div 
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
        transition={{ duration: 0.2 }}
        onClick={(e) => e.stopPropagation()}
        className="panel transaction-editor"
      >
        <div className="transaction-editor-head">
          <h2>{onDelete ? "编辑明细" : "记一笔"}</h2>
          <button className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>

        <div className="segmented">
          <button className={transaction.transaction_type === "expense" ? "active" : ""} onClick={() => onChange({ ...transaction, transaction_type: "expense" })}>支出</button>
          <button className={transaction.transaction_type === "income" ? "active" : ""} onClick={() => onChange({ ...transaction, transaction_type: "income" })}>收入</button>
        </div>
        
        <input className="money-input editor-money-input" type="number" min="0" step="0.01" value={transaction.amount || ""} placeholder="0.00" onChange={(event) => onChange({ ...transaction, amount: Number(event.target.value) })} />
        
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

        <label className="check-row editor-check-row">
          <input type="checkbox" checked={transaction.is_excluded} onChange={(event) => onChange({ ...transaction, is_excluded: event.target.checked })} />
          <span>不计入各项收支统计</span>
        </label>
        
        <div className="editor-actions">
           <button className="primary-button editor-save-button" onClick={onSave}><Save size={17} />保存</button>
           
           {onDelete && (confirmDelete ? (
             <div className="editor-action-row">
               <button className="danger-button grow-button" onClick={onDelete}>确认删除</button>
               <button className="ghost-button grow-button" onClick={() => setConfirmDelete(false)}>取消</button>
             </div>
           ) : (
             <button className="ghost-button editor-delete-button" onClick={() => setConfirmDelete(true)}><Trash2 size={17} />删除明细</button>
           ))}
        </div>
      </motion.div>
    </div>
  );
}
