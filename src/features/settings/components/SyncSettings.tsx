import { useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { useSettingsStore } from "../../../store/useSettingsStore";
import { Cloud, Download, Loader2, KeyRound } from "lucide-react";

export default function SyncSettings() {
  const { syncConfig, setSyncConfig } = useSettingsStore();
  const [isSyncing, setIsSyncing] = useState(false);
  const [isRestoring, setIsRestoring] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleSync = async (direction: 'upload' | 'download') => {
    if (!syncConfig.url || !syncConfig.user || !syncConfig.encryptKey) {
      setMessage({ type: 'error', text: '请填写完整的 WebDAV 和密钥信息' });
      return;
    }

    const command = direction === 'upload' ? 'sync_to_webdav' : 'restore_from_webdav';
    const setLoading = direction === 'upload' ? setIsSyncing : setIsRestoring;
    
    setLoading(true);
    setMessage(null);
    try {
      await invoke(command, {
        url: syncConfig.url,
        user: syncConfig.user,
        pass: syncConfig.pass,
        encryptKey: syncConfig.encryptKey,
      });
      setMessage({ 
        type: 'success', 
        text: direction === 'upload' ? '备份成功上传至云端' : '已成功从云端恢复数据，请刷新页面查看' 
      });
    } catch (error: any) {
      setMessage({ type: 'error', text: `操作失败: ${error}` });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h2 className="text-lg font-medium text-foreground mb-4">WebDAV 高级同步</h2>
        <p className="text-sm text-surface-foreground/80 mb-6">
          OmniFlow 支持将你的本地数据库文件通过 AES-256-GCM 高强度加密后，同步到你自己的 WebDAV 服务器（如坚果云、群晖 NAS、Nextcloud 等）。
        </p>

        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">WebDAV 服务器地址</label>
              <input
                type="text"
                placeholder="https://dav.jianguoyun.com/dav/omniflow"
                className="w-full bg-background border border-border rounded-lg px-4 py-2 focus:ring-2 focus:ring-primary focus:border-transparent outline-none"
                value={syncConfig.url}
                onChange={(e) => setSyncConfig({ url: e.target.value })}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">账号 (User)</label>
                <input
                  type="text"
                  className="w-full bg-background border border-border rounded-lg px-4 py-2 focus:ring-2 focus:ring-primary focus:border-transparent outline-none"
                  value={syncConfig.user}
                  onChange={(e) => setSyncConfig({ user: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">密码/应用密码 (Password)</label>
                <input
                  type="password"
                  className="w-full bg-background border border-border rounded-lg px-4 py-2 focus:ring-2 focus:ring-primary focus:border-transparent outline-none"
                  value={syncConfig.pass}
                  onChange={(e) => setSyncConfig({ pass: e.target.value })}
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 flex items-center gap-1">
                <KeyRound size={16} /> 本地加密密钥 (AES-256 Key)
              </label>
              <input
                type="password"
                placeholder="设置一个高强度的密码，遗忘将无法恢复数据！"
                className="w-full bg-background border border-border rounded-lg px-4 py-2 focus:ring-2 focus:ring-primary focus:border-transparent outline-none font-mono"
                value={syncConfig.encryptKey}
                onChange={(e) => setSyncConfig({ encryptKey: e.target.value })}
              />
              <p className="text-xs text-red-500/80 mt-1">此密码仅保存在本地设备，每次上传/下载均会在本地完成加解密，绝不在网络中传输明文数据库。</p>
            </div>
          </div>

          <div className="flex gap-4 pt-4">
            <button
              onClick={() => handleSync('upload')}
              disabled={isSyncing || isRestoring}
              className="flex-1 flex items-center justify-center gap-2 bg-primary text-white py-2.5 rounded-lg font-medium hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {isSyncing ? <Loader2 className="animate-spin" size={20} /> : <Cloud size={20} />}
              加密并上传备份
            </button>
            <button
              onClick={() => handleSync('download')}
              disabled={isSyncing || isRestoring}
              className="flex-1 flex items-center justify-center gap-2 bg-surface text-foreground border border-border py-2.5 rounded-lg font-medium hover:bg-background transition-colors disabled:opacity-50"
            >
              {isRestoring ? <Loader2 className="animate-spin" size={20} /> : <Download size={20} />}
              从云端恢复覆盖
            </button>
          </div>

          {message && (
            <div className={`p-4 rounded-lg mt-4 text-sm font-medium ${message.type === 'success' ? 'bg-green-500/10 text-green-600' : 'bg-red-500/10 text-red-600'}`}>
              {message.text}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
