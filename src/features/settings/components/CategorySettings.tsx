import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Plus, Trash2, Edit2, Loader2, Save, X } from "lucide-react";
import { Category, TransactionType } from "../../../models";
import { Select } from "../../../components/ui/Select";

export default function CategorySettings() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  
  const [isEditing, setIsEditing] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editType, setEditType] = useState<TransactionType>("expense");
  const [editParentId, setEditParentId] = useState("");

  useEffect(() => {
    loadCategories();
  }, []);

  const loadCategories = async () => {
    try {
      const data = await invoke<Category[]>('list_categories');
      setCategories(data);
    } catch (error) {
      console.error('Failed to load categories', error);
    } finally {
      setLoading(false);
    }
  };

  const handleAdd = () => {
    const newId = "new-" + Date.now();
    const newCat: Category = {
      id: newId,
      name: "",
      category_type: "expense",
      created_at: Math.floor(Date.now() / 1000),
      updated_at: Math.floor(Date.now() / 1000),
    };
    setCategories([newCat, ...categories]);
    setIsEditing(newId);
    setEditName("");
    setEditType("expense");
    setEditParentId("");
  };

  const handleSave = async (id: string) => {
    if (!editName.trim()) return;
    
    try {
      const isNew = id.startsWith("new-");
      const categoryToSave = {
        id: isNew ? window.crypto.randomUUID() : id,
        name: editName.trim(),
        category_type: editType,
        parent_id: editParentId || null,
        created_at: Math.floor(Date.now() / 1000),
        updated_at: Math.floor(Date.now() / 1000),
        deleted_at: null,
      };

      if (isNew) {
        await invoke('create_category', { category: categoryToSave });
      } else {
        await invoke('update_category', { category: categoryToSave });
      }
      
      setIsEditing(null);
      loadCategories();
    } catch (error) {
      console.error('Failed to save category', error);
    }
  };

  const handleDelete = async (id: string) => {
    if (id.startsWith("new-")) {
      setCategories(categories.filter(c => c.id !== id));
      setIsEditing(null);
      return;
    }
    
    if (!window.confirm("确定要删除这个分类吗？已绑定的账单分类将被置空。")) return;
    
    try {
      await invoke('delete_category', { id });
      loadCategories();
    } catch (error) {
      console.error('Failed to delete category', error);
    }
  };

  if (loading) {
    return <div className="center-state"><Loader2 className="spin" /></div>;
  }

  const expenses = categories.filter(c => c.category_type === 'expense');
  const incomes = categories.filter(c => c.category_type === 'income');

  return (
    <div className="settings-stack wide">
      <div className="settings-head-row">
        <div>
          <h2>分类管理</h2>
          <p className="muted-note compact">
            自定义账单的收支分类。
          </p>
        </div>
        <button
          onClick={handleAdd}
          disabled={isEditing !== null}
          className="primary-button"
        >
          <Plus size={16} /> 新增分类
        </button>
      </div>

      <div className="split-grid">
        <section className="settings-section">
          <h3 className="category-heading expense">支出分类</h3>
          <div className="category-list">
            {expenses.map(cat => (
              <CategoryRow 
                key={cat.id} 
                category={cat} 
                isEditing={isEditing === cat.id}
                editName={editName}
                setEditName={setEditName}
                editType={editType}
                setEditType={setEditType}
                editParentId={editParentId}
                setEditParentId={setEditParentId}
                categories={categories}
                onSave={() => handleSave(cat.id)}
                onCancel={() => { setIsEditing(null); if(cat.id.startsWith("new-")) loadCategories(); }}
                onEdit={() => { setIsEditing(cat.id); setEditName(cat.name); setEditType(cat.category_type); setEditParentId(cat.parent_id || ""); }}
                onDelete={() => handleDelete(cat.id)}
              />
            ))}
            {expenses.length === 0 && <div className="compact-empty">暂无支出分类</div>}
          </div>
        </section>

        <section className="settings-section">
          <h3 className="category-heading income">收入分类</h3>
          <div className="category-list">
            {incomes.map(cat => (
              <CategoryRow 
                key={cat.id} 
                category={cat} 
                isEditing={isEditing === cat.id}
                editName={editName}
                setEditName={setEditName}
                editType={editType}
                setEditType={setEditType}
                editParentId={editParentId}
                setEditParentId={setEditParentId}
                categories={categories}
                onSave={() => handleSave(cat.id)}
                onCancel={() => { setIsEditing(null); if(cat.id.startsWith("new-")) loadCategories(); }}
                onEdit={() => { setIsEditing(cat.id); setEditName(cat.name); setEditType(cat.category_type); setEditParentId(cat.parent_id || ""); }}
                onDelete={() => handleDelete(cat.id)}
              />
            ))}
            {incomes.length === 0 && <div className="compact-empty">暂无收入分类</div>}
          </div>
        </section>
      </div>
    </div>
  );
}

function CategoryRow({ category, isEditing, editName, setEditName, editType, setEditType, editParentId, setEditParentId, categories, onSave, onCancel, onEdit, onDelete }: any) {
  if (isEditing) {
    const parentOptions = categories.filter((item: Category) => item.id !== category.id && item.category_type === editType && !item.parent_id);
    return (
      <div className="category-row editing">
        <input 
          autoFocus
          className="field"
          value={editName}
          onChange={e => setEditName(e.target.value)}
          placeholder="分类名称"
        />
        <Select
          className="field-xs"
          value={editType}
          onChange={(val) => setEditType(val as TransactionType)}
          options={[
            { value: "expense", label: "支出" },
            { value: "income", label: "收入" },
          ]}
        />
        <Select
          className="field-sm"
          value={editParentId}
          onChange={(val) => setEditParentId(val)}
          options={[
            { value: "", label: "一级分类" },
            ...parentOptions.map((item: Category) => ({ value: item.id, label: `${item.name} 的二级分类` })),
          ]}
        />
        <button onClick={onSave} className="icon-button success-icon" aria-label="保存"><Save size={16} /></button>
        <button onClick={onCancel} className="icon-button danger-icon" aria-label="取消"><X size={16} /></button>
      </div>
    );
  }

  return (
    <div className="category-row">
      <span>{category.name}</span>
      <div className="row-actions">
        <button onClick={onEdit} className="icon-button" aria-label="编辑"><Edit2 size={14} /></button>
        <button onClick={onDelete} className="icon-button danger-icon" aria-label="删除"><Trash2 size={14} /></button>
      </div>
    </div>
  );
}
