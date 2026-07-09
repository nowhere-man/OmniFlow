import { useState } from "react";
import { useSettingsStore } from "../../../store/useSettingsStore";
import { Cloud, Download, Loader2, KeyRound } from "lucide-react";
import { invoke } from "../../../tauri-adapter/invoke";

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
    <div className="settings-stack">
      <section className="settings-section">
        <h2>WebDAV 高级同步</h2>
        <p className="muted-note">
          OmniFlow 支持将你的本地数据库文件通过 AES-256-GCM 高强度加密后，同步到你自己的 WebDAV 服务器（如坚果云、群晖 NAS、Nextcloud 等）。
        </p>

        <div className="form-stack">
          <div className="form-stack">
            <div>
              <label className="form-label">WebDAV 服务器地址</label>
              <input
                type="text"
                placeholder="https://dav.jianguoyun.com/dav/omniflow"
                className="field"
                value={syncConfig.url}
                onChange={(e) => setSyncConfig({ url: e.target.value })}
              />
            </div>
            <div className="form-grid-two">
              <div>
                <label className="form-label">账号 (User)</label>
                <input
                  type="text"
                  className="field"
                  value={syncConfig.user}
                  onChange={(e) => setSyncConfig({ user: e.target.value })}
                />
              </div>
              <div>
                <label className="form-label">密码/应用密码 (Password)</label>
                <input
                  type="password"
                  className="field"
                  value={syncConfig.pass}
                  onChange={(e) => setSyncConfig({ pass: e.target.value })}
                />
              </div>
            </div>
            <div>
              <label className="form-label inline-label">
                <KeyRound size={16} /> 本地加密密钥 (AES-256 Key)
              </label>
              <input
                type="password"
                placeholder="设置一个高强度的密码，遗忘将无法恢复数据！"
                className="field mono-field"
                value={syncConfig.encryptKey}
                onChange={(e) => setSyncConfig({ encryptKey: e.target.value })}
              />
              <p className="danger-note">此密码仅保存在本地设备，每次上传/下载均会在本地完成加解密，绝不在网络中传输明文数据库。</p>
            </div>
          </div>

          <div className="button-row">
            <button
              onClick={() => handleSync('upload')}
              disabled={isSyncing || isRestoring}
              className="primary-button grow-button"
            >
              {isSyncing ? <Loader2 className="spin" size={20} /> : <Cloud size={20} />}
              加密并上传备份
            </button>
            <button
              onClick={() => handleSync('download')}
              disabled={isSyncing || isRestoring}
              className="ghost-button grow-button"
            >
              {isRestoring ? <Loader2 className="spin" size={20} /> : <Download size={20} />}
              从云端恢复覆盖
            </button>
          </div>

          {message && (
            <div className={`message-bar ${message.type}`}>
              {message.text}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
