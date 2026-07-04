import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Plus, Trash2, Edit2, Loader2, Save, X } from "lucide-react";
import { Category, TransactionType } from "../../../models";

export default function CategorySettings() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  
  const [isEditing, setIsEditing] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editType, setEditType] = useState<TransactionType>("expense");

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
  };

  const handleSave = async (id: string) => {
    if (!editName.trim()) return;
    
    try {
      const isNew = id.startsWith("new-");
      const categoryToSave = {
        id: isNew ? window.crypto.randomUUID() : id,
        name: editName.trim(),
        category_type: editType,
        parent_id: null,
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
    return <div className="flex justify-center p-8"><Loader2 className="animate-spin text-primary" /></div>;
  }

  const expenses = categories.filter(c => c.category_type === 'expense');
  const incomes = categories.filter(c => c.category_type === 'income');

  return (
    <div className="max-w-3xl">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-lg font-medium text-foreground">分类管理</h2>
          <p className="text-sm text-surface-foreground/80 mt-1">
            自定义账单的收支分类。
          </p>
        </div>
        <button
          onClick={handleAdd}
          disabled={isEditing !== null}
          className="flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
        >
          <Plus size={16} /> 新增分类
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* 支出分类 */}
        <div>
          <h3 className="text-sm font-semibold text-red-500 mb-3 border-b border-border pb-2">支出分类</h3>
          <div className="space-y-2">
            {expenses.map(cat => (
              <CategoryRow 
                key={cat.id} 
                category={cat} 
                isEditing={isEditing === cat.id}
                editName={editName}
                setEditName={setEditName}
                editType={editType}
                setEditType={setEditType}
                onSave={() => handleSave(cat.id)}
                onCancel={() => { setIsEditing(null); if(cat.id.startsWith("new-")) loadCategories(); }}
                onEdit={() => { setIsEditing(cat.id); setEditName(cat.name); setEditType(cat.category_type); }}
                onDelete={() => handleDelete(cat.id)}
              />
            ))}
            {expenses.length === 0 && <div className="text-sm text-surface-foreground/50 text-center py-4">暂无支出分类</div>}
          </div>
        </div>

        {/* 收入分类 */}
        <div>
          <h3 className="text-sm font-semibold text-green-500 mb-3 border-b border-border pb-2">收入分类</h3>
          <div className="space-y-2">
            {incomes.map(cat => (
              <CategoryRow 
                key={cat.id} 
                category={cat} 
                isEditing={isEditing === cat.id}
                editName={editName}
                setEditName={setEditName}
                editType={editType}
                setEditType={setEditType}
                onSave={() => handleSave(cat.id)}
                onCancel={() => { setIsEditing(null); if(cat.id.startsWith("new-")) loadCategories(); }}
                onEdit={() => { setIsEditing(cat.id); setEditName(cat.name); setEditType(cat.category_type); }}
                onDelete={() => handleDelete(cat.id)}
              />
            ))}
            {incomes.length === 0 && <div className="text-sm text-surface-foreground/50 text-center py-4">暂无收入分类</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

function CategoryRow({ category, isEditing, editName, setEditName, editType, setEditType, onSave, onCancel, onEdit, onDelete }: any) {
  if (isEditing) {
    return (
      <div className="flex items-center gap-2 p-2 bg-surface border border-primary/30 rounded-lg">
        <input 
          autoFocus
          className="flex-1 bg-background px-2 py-1.5 text-sm rounded border border-border focus:outline-none focus:border-primary"
          value={editName}
          onChange={e => setEditName(e.target.value)}
          placeholder="分类名称"
        />
        <select 
          className="bg-background px-2 py-1.5 text-sm rounded border border-border focus:outline-none"
          value={editType}
          onChange={e => setEditType(e.target.value as TransactionType)}
        >
          <option value="expense">支出</option>
          <option value="income">收入</option>
        </select>
        <button onClick={onSave} className="p-1.5 text-green-600 hover:bg-green-50 rounded"><Save size={16} /></button>
        <button onClick={onCancel} className="p-1.5 text-red-500 hover:bg-red-50 rounded"><X size={16} /></button>
      </div>
    );
  }

  return (
    <div className="flex justify-between items-center p-3 bg-surface border border-border rounded-lg group hover:border-primary/30 transition-colors">
      <span className="text-sm font-medium">{category.name}</span>
      <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button onClick={onEdit} className="p-1.5 text-surface-foreground hover:bg-background rounded text-xs"><Edit2 size={14} /></button>
        <button onClick={onDelete} className="p-1.5 text-red-500 hover:bg-red-500/10 rounded text-xs"><Trash2 size={14} /></button>
      </div>
    </div>
  );
}
