import React, { useState, useRef, useEffect } from 'react';
import {
  Terminal,
  Send,
  MessageSquare,
  Users,
  Plus,
  Smile,
  ImagePlus,
  Code2,
  Shield,
  Info,
  X,
  Activity,
  Eye,
  Sliders,
} from 'lucide-react';
import type {
  UserProfile,
  ConversationItem,
  ChatMessage,
  GroupRoomItem,
  GroupChatMessage,
  GroupMemberItem,
} from '../types';
import { formatChatTime } from '../utils/format';
import { StealthWorkspaceCamouflage, type StealthTheme } from './StealthWorkspaceCamouflage';

export interface ProChatWorkspaceProps {
  user: UserProfile | null;
  onRequireAuth: () => void;
  chatTab: 'direct' | 'group';
  setChatTab: (tab: 'direct' | 'group') => void;
  conversations: ConversationItem[];
  activeConvId: string | null;
  setActiveConvId: (id: string | null) => void;
  messages: ChatMessage[];
  groups: GroupRoomItem[];
  activeGroupId: string | null;
  setActiveGroupId: (id: string | null) => void;
  groupMessages: GroupChatMessage[];
  groupMembers: GroupMemberItem[];
  activeGroup?: GroupRoomItem;
  activeConv?: ConversationItem;
  inputText: string;
  setInputText: (text: string) => void;
  onSendMessage: (e?: React.FormEvent, overrideContent?: string) => void | Promise<void>;
  onUploadImage?: (file: File) => void | Promise<void>;
  onRecallMessage?: (msgId: string) => void;
  onOpenNewChatModal: () => void;
  onOpenGroupModal: () => void;
  onSelectUser?: (username: string) => void;
  connStatus: 'offline' | 'http' | 'ws';
}

export const ProChatWorkspace: React.FC<ProChatWorkspaceProps> = ({
  user,
  onRequireAuth,
  chatTab,
  setChatTab,
  conversations,
  activeConvId,
  setActiveConvId,
  messages,
  groups,
  activeGroupId,
  setActiveGroupId,
  groupMessages,
  groupMembers,
  activeGroup,
  activeConv,
  inputText,
  setInputText,
  onSendMessage,
  onUploadImage,
  onOpenNewChatModal,
  onOpenGroupModal,
  onSelectUser,
  connStatus,
}) => {
  // 视图渲染模式：'cyber' (科技气泡流) 或 'terminal' (纯正终端代码单行流)
  const [viewMode, setViewMode] = useState<'cyber' | 'terminal'>(() => {
    try {
      return (localStorage.getItem('geek_pro_chat_view_mode') as 'cyber' | 'terminal') || 'cyber';
    } catch {
      return 'cyber';
    }
  });

  // 摸鱼伪装中枢主题
  const [stealthTheme, setStealthTheme] = useState<StealthTheme>('normal');
  const [showStealthDropdown, setShowStealthDropdown] = useState(false);

  // 右侧极客监视控制台看板展开状态
  const [showInspector, setShowInspector] = useState(true);

  // 频道/会话搜索过滤关键词
  const [filterQuery, setFilterQuery] = useState('');

  // 消息滚动容器
  const feedScrollRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // 切换并持久化视图模式
  const handleToggleViewMode = () => {
    const next = viewMode === 'cyber' ? 'terminal' : 'cyber';
    setViewMode(next);
    try {
      localStorage.setItem('geek_pro_chat_view_mode', next);
    } catch {}
  };

  // 自动滚动到最新消息
  useEffect(() => {
    if (feedScrollRef.current) {
      feedScrollRef.current.scrollTop = feedScrollRef.current.scrollHeight;
    }
  }, [messages, groupMessages, activeConvId, activeGroupId]);

  // 全局键盘快捷键：Alt+1~6 摸鱼伪装，Alt+B 紧凑流切换，Esc 退出伪装
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.altKey) {
        if (e.key === '1') { e.preventDefault(); setStealthTheme('vscode'); }
        if (e.key === '2') { e.preventDefault(); setStealthTheme('idea'); }
        if (e.key === '3') { e.preventDefault(); setStealthTheme('feishu'); }
        if (e.key === '4') { e.preventDefault(); setStealthTheme('excel'); }
        if (e.key === '5') { e.preventDefault(); setStealthTheme('word'); }
        if (e.key === '6') { e.preventDefault(); setStealthTheme('ppt'); }
        if (e.key.toLowerCase() === 'b') { e.preventDefault(); handleToggleViewMode(); }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [viewMode]);

  // 过滤后的群聊与私聊
  const filteredGroups = groups.filter((g) =>
    g.name.toLowerCase().includes(filterQuery.toLowerCase())
  );
  const filteredConversations = conversations.filter((c) =>
    (c.peer_nickname || c.peer_username || '').toLowerCase().includes(filterQuery.toLowerCase())
  );

  // 格式化当前活动目标标题与副标
  const activeTitle = chatTab === 'group'
    ? activeGroup ? `# ${activeGroup.name}` : '未选择群组频道'
    : activeConv ? `@ ${activeConv.peer_nickname || activeConv.peer_username}` : '未选择私聊频道';

  const activeDesc = chatTab === 'group'
    ? activeGroup?.description || `${groupMembers.length} 名开发者在线讨论`
    : activeConv ? `活跃会话 · 延迟 18ms` : '请从左侧极客频道树中挑选一个加入';

  // 快捷插入代码块
  const handleInsertCodeSnippet = () => {
    const snippet = "```javascript\n// Write code here\nconsole.log('Hello Geek Pro!');\n```\n";
    setInputText((inputText ? inputText + '\n' : '') + snippet);
  };

  return (
    <div className="w-full h-full flex flex-col bg-[#0d1117] text-[#c9d1d9] font-sans overflow-hidden select-none border border-[#30363d]/80 rounded-none md:rounded-xl shadow-2xl relative">
      {/* 1. 顶部极客全息状态 HUD (Geek Command HUD) */}
      <header className="h-12 border-b border-[#30363d] bg-[#161b22]/90 backdrop-blur-md px-3 flex items-center justify-between shrink-0 z-30">
        {/* 左侧：节点状态与延迟指示 */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 px-2.5 py-1 rounded bg-[#0d1117] border border-[#30363d] text-3xs font-mono">
            <span className={`w-2 h-2 rounded-full ${connStatus === 'ws' ? 'bg-emerald-400 shadow-[0_0_8px_#34d399]' : 'bg-amber-400'}`} />
            <span className="text-zinc-400 font-semibold">NODE:</span>
            <span className="text-emerald-400">PRO-GEEK-01</span>
            <span className="text-zinc-600">|</span>
            <span className="text-zinc-400">PING:</span>
            <span className="text-cyan-400">18ms</span>
          </div>

          <div className="hidden lg:flex items-center gap-1.5 text-2xs font-mono text-zinc-400 bg-emerald-950/40 border border-emerald-500/20 px-2 py-0.5 rounded text-emerald-300">
            <Shield className="w-3 h-3 text-emerald-400" />
            <span>TLS 1.3 / E2EE VERIFIED</span>
          </div>
        </div>

        {/* 中间：当前会话极客路径 */}
        <div className="hidden md:flex items-center gap-2 font-mono text-xs text-zinc-300 bg-[#0d1117]/60 px-3 py-1 rounded-md border border-[#30363d]/60">
          <Terminal className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-zinc-500">~/shendu/{chatTab}/</span>
          <span className="text-emerald-300 font-bold">{activeTitle}</span>
        </div>

        {/* 右侧：专业控制中枢与摸鱼伪装快捷切换 */}
        <div className="flex items-center gap-2">
          {/* 视图模式切换：Cyber vs Terminal */}
          <button
            type="button"
            onClick={handleToggleViewMode}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-[#21262d] hover:bg-[#30363d] border border-[#30363d] text-2xs font-mono text-zinc-300 transition-colors cursor-pointer"
            title="一键切换代码日志流与科技气泡流 (Alt+B)"
          >
            <Activity className="w-3.5 h-3.5 text-cyan-400" />
            <span className="hidden sm:inline">{viewMode === 'cyber' ? '气泡流' : '终端流'}</span>
            <kbd className="text-[9px] text-zinc-500 font-mono">Alt+B</kbd>
          </button>

          {/* 摸鱼伪装下拉菜单 */}
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowStealthDropdown(!showStealthDropdown)}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-emerald-950/60 hover:bg-emerald-900/80 border border-emerald-500/40 text-2xs font-mono text-emerald-300 transition-all cursor-pointer shadow-[0_0_12px_rgba(16,185,129,0.15)]"
              title="一键伪装为工作软件 (VSCode/IDEA/飞书/Excel/Word/PPT)"
            >
              <Eye className="w-3.5 h-3.5 text-emerald-400" />
              <span className="hidden sm:inline">摸鱼伪装 ▾</span>
            </button>

            {showStealthDropdown && (
              <div className="absolute right-0 top-full mt-1.5 w-52 bg-[#161b22] border border-[#30363d] rounded-lg shadow-2xl p-1.5 z-50 text-3xs font-mono space-y-1">
                <div className="px-2 py-1 text-zinc-400 font-bold border-b border-[#30363d]">
                  选择工作软件伪装皮肤
                </div>
                {[
                  { key: 'vscode', name: 'VS Code 编辑器', color: 'bg-blue-500', shortcut: 'Alt+1' },
                  { key: 'idea', name: 'IntelliJ IDEA', color: 'bg-amber-500', shortcut: 'Alt+2' },
                  { key: 'feishu', name: '飞书工作协同', color: 'bg-sky-500', shortcut: 'Alt+3' },
                  { key: 'excel', name: 'Excel 电子表格', color: 'bg-emerald-500', shortcut: 'Alt+4' },
                  { key: 'word', name: 'Word 文档方案', color: 'bg-blue-700', shortcut: 'Alt+5' },
                  { key: 'ppt', name: 'PPT 演示文稿', color: 'bg-orange-500', shortcut: 'Alt+6' },
                ].map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => {
                      setStealthTheme(item.key as StealthTheme);
                      setShowStealthDropdown(false);
                    }}
                    className="w-full flex items-center justify-between px-2.5 py-1.5 rounded hover:bg-[#21262d] text-zinc-200 transition-colors text-left cursor-pointer"
                  >
                    <span className="flex items-center gap-2">
                      <span className={`w-2 h-2 rounded-full ${item.color}`} />
                      <span>{item.name}</span>
                    </span>
                    <kbd className="text-[9px] text-zinc-500">{item.shortcut}</kbd>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 切换右侧群/会话侦测面板 */}
          <button
            type="button"
            onClick={() => setShowInspector(!showInspector)}
            className={`p-1.5 rounded border transition-colors cursor-pointer ${
              showInspector
                ? 'bg-[#21262d] text-cyan-400 border-cyan-500/40'
                : 'bg-[#161b22] text-zinc-400 border-[#30363d] hover:text-white'
            }`}
            title="展开/收起极客信息看板"
          >
            <Sliders className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* 2. 主体三栏极客工作区 (Sidebar + Console Feed + Inspector) */}
      <div className="flex-1 flex overflow-hidden">
        {/* A. 左侧：极客频道树与私聊流 (Geek Channel Explorer) */}
        <aside className="w-64 sm:w-72 border-r border-[#30363d] bg-[#0d1117] flex flex-col shrink-0">
          {/* Tab 切换：频道群聊 vs 个人私聊 */}
          <div className="p-2 border-b border-[#30363d] flex gap-1 bg-[#161b22]/50">
            <button
              type="button"
              onClick={() => setChatTab('group')}
              className={`flex-1 py-1.5 rounded flex items-center justify-center gap-1.5 text-2xs font-mono font-semibold transition-all cursor-pointer ${
                chatTab === 'group'
                  ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-500/30'
                  : 'text-zinc-400 hover:text-white hover:bg-[#21262d]'
              }`}
            >
              <Users className="w-3.5 h-3.5" />
              <span>群组频道</span>
              <span className="text-[10px] opacity-70">({groups.length})</span>
            </button>
            <button
              type="button"
              onClick={() => setChatTab('direct')}
              className={`flex-1 py-1.5 rounded flex items-center justify-center gap-1.5 text-2xs font-mono font-semibold transition-all cursor-pointer ${
                chatTab === 'direct'
                  ? 'bg-cyan-950/80 text-cyan-300 border border-cyan-500/30'
                  : 'text-zinc-400 hover:text-white hover:bg-[#21262d]'
              }`}
            >
              <MessageSquare className="w-3.5 h-3.5" />
              <span>私聊终端</span>
              <span className="text-[10px] opacity-70">({conversations.length})</span>
            </button>
          </div>

          {/* 命令行风格搜索与过滤 */}
          <div className="p-2 border-b border-[#30363d] bg-[#0d1117]">
            <div className="flex items-center gap-1.5 px-2 py-1 bg-[#161b22] border border-[#30363d] rounded text-2xs font-mono">
              <span className="text-emerald-400 font-bold">{'>'}</span>
              <input
                type="text"
                value={filterQuery}
                onChange={(e) => setFilterQuery(e.target.value)}
                placeholder="grep channel / user..."
                className="w-full bg-transparent outline-none text-zinc-200 placeholder:text-zinc-600 text-2xs"
              />
              {filterQuery && (
                <button type="button" onClick={() => setFilterQuery('')} className="text-zinc-500 hover:text-white">
                  <X className="w-3 h-3" />
                </button>
              )}
            </div>
          </div>

          {/* 频道与会话列表 */}
          <div className="flex-1 overflow-y-auto p-2 space-y-1 font-mono text-xs">
            {chatTab === 'group' ? (
              <>
                <div className="flex items-center justify-between px-2 py-1 text-3xs text-zinc-500 font-bold uppercase tracking-wider">
                  <span>PUBLIC & PRIVATE CHANNELS</span>
                  <button
                    type="button"
                    onClick={onOpenGroupModal}
                    className="p-1 hover:text-emerald-400 transition-colors"
                    title="创建新频道"
                  >
                    <Plus className="w-3 h-3" />
                  </button>
                </div>
                {filteredGroups.map((g) => {
                  const isActive = g.id === activeGroupId;
                  return (
                    <button
                      key={g.id}
                      type="button"
                      onClick={() => setActiveGroupId(g.id)}
                      className={`w-full flex items-center justify-between px-2.5 py-2 rounded text-left transition-colors cursor-pointer group ${
                        isActive
                          ? 'bg-emerald-950/60 border border-emerald-500/40 text-emerald-300'
                          : 'hover:bg-[#161b22] text-zinc-400 hover:text-zinc-200 border border-transparent'
                      }`}
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span className={`text-sm ${isActive ? 'text-emerald-400 font-bold' : 'text-zinc-500'}`}>#</span>
                        <span className="truncate text-2xs">{g.name}</span>
                      </div>
                      {(g.unread_count || 0) > 0 && (
                        <span className="px-1.5 py-0.2 rounded-full bg-emerald-500 text-black text-[10px] font-bold">
                          {g.unread_count}
                        </span>
                      )}
                    </button>
                  );
                })}
              </>
            ) : (
              <>
                <div className="flex items-center justify-between px-2 py-1 text-3xs text-zinc-500 font-bold uppercase tracking-wider">
                  <span>DIRECT SESSIONS</span>
                  <button
                    type="button"
                    onClick={onOpenNewChatModal}
                    className="p-1 hover:text-cyan-400 transition-colors"
                    title="发起新私聊"
                  >
                    <Plus className="w-3 h-3" />
                  </button>
                </div>
                {filteredConversations.map((c) => {
                  const isActive = c.id === activeConvId;
                  const name = c.peer_nickname || c.peer_username || '开发者';
                  return (
                    <button
                      key={c.id}
                      type="button"
                      onClick={() => setActiveConvId(c.id)}
                      className={`w-full flex items-center justify-between px-2.5 py-2 rounded text-left transition-colors cursor-pointer group ${
                        isActive
                          ? 'bg-cyan-950/60 border border-cyan-500/40 text-cyan-300'
                          : 'hover:bg-[#161b22] text-zinc-400 hover:text-zinc-200 border border-transparent'
                      }`}
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                        <span className="truncate text-2xs">@{name}</span>
                      </div>
                      {(c.unread_count || 0) > 0 && (
                        <span className="px-1.5 py-0.2 rounded-full bg-cyan-500 text-black text-[10px] font-bold">
                          {c.unread_count}
                        </span>
                      )}
                    </button>
                  );
                })}
              </>
            )}
          </div>
        </aside>

        {/* B. 中间：专业版极客消息流与控制台 (Feed & Command Launcher) */}
        <main className="flex-1 flex flex-col bg-[#0d1117] overflow-hidden relative">
          {/* 频道次级状态标头 */}
          <div className="h-10 border-b border-[#30363d] bg-[#161b22]/40 px-4 flex items-center justify-between shrink-0 font-mono text-2xs">
            <div className="flex items-center gap-2">
              <span className="text-emerald-400 font-bold">{activeTitle}</span>
              <span className="text-zinc-600">·</span>
              <span className="text-zinc-400 text-3xs truncate max-w-sm">{activeDesc}</span>
            </div>
            <div className="text-3xs text-zinc-500">
              {chatTab === 'group' ? `${groupMessages.length} 条已同步` : `${messages.length} 条已同步`}
            </div>
          </div>

          {/* 消息滚动流视窗 */}
          <div
            ref={feedScrollRef}
            className={`flex-1 overflow-y-auto p-4 space-y-2 select-text ${
              viewMode === 'terminal' ? 'font-mono text-2xs' : 'font-sans'
            }`}
          >
            {/* 1. 终端代码日志流视图 (Terminal Log View) */}
            {viewMode === 'terminal' ? (
              <div className="space-y-1">
                {(chatTab === 'group' ? groupMessages : messages).map((msg, idx) => {
                  const isMe = msg.sender_id === user?.id;
                  const senderName =
                    chatTab === 'group'
                      ? (msg as GroupChatMessage).sender_nickname || (msg as GroupChatMessage).sender_username
                      : isMe ? user?.nickname || user?.username || 'me' : activeConv?.peer_nickname || activeConv?.peer_username || 'peer';
                  const lineNum = String(idx + 1).padStart(3, '0');

                  return (
                    <div
                      key={msg.id}
                      className="flex items-start gap-3 py-1 px-2 rounded hover:bg-[#161b22] transition-colors group font-mono"
                    >
                      <span className="text-zinc-600 select-none text-[10px] w-6 shrink-0">{lineNum}</span>
                      <span className="text-zinc-500 select-none text-[10px] shrink-0">[{formatChatTime(msg.created_at)}]</span>
                      <span className={`shrink-0 font-bold ${isMe ? 'text-emerald-400' : 'text-cyan-400'}`}>
                        {isMe ? '[ME]' : `[${senderName}]`}:
                      </span>
                      <span className="text-zinc-200 break-all leading-relaxed flex-1">{msg.content}</span>
                    </div>
                  );
                })}
              </div>
            ) : (
              /* 2. 科技气泡流视图 (Cyber Bubble View) */
              <div className="space-y-4">
                {(chatTab === 'group' ? groupMessages : messages).map((msg) => {
                  const isMe = msg.sender_id === user?.id;
                  const senderName =
                    chatTab === 'group'
                      ? (msg as GroupChatMessage).sender_nickname || (msg as GroupChatMessage).sender_username
                      : isMe ? user?.nickname || user?.username || '我' : activeConv?.peer_nickname || activeConv?.peer_username || '对方';

                  return (
                    <div
                      key={msg.id}
                      className={`flex flex-col ${isMe ? 'items-end' : 'items-start'} group`}
                    >
                      {/* 发言人头标与极客时间戳 */}
                      <div className="flex items-center gap-2 mb-1 text-3xs font-mono text-zinc-500">
                        <span className={isMe ? 'text-emerald-400 font-semibold' : 'text-cyan-400 font-semibold'}>
                          {senderName}
                        </span>
                        <span>·</span>
                        <span>{formatChatTime(msg.created_at)}</span>
                      </div>

                      {/* 科技气泡主体 */}
                      <div
                        className={`max-w-[85%] sm:max-w-[75%] px-4 py-2.5 rounded-xl border text-xs leading-relaxed transition-all ${
                          isMe
                            ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-100 rounded-tr-none shadow-[0_0_15px_rgba(16,185,129,0.06)]'
                            : 'bg-[#161b22] border-[#30363d] text-zinc-200 rounded-tl-none shadow-sm'
                        }`}
                      >
                        <p className="whitespace-pre-wrap break-all">{msg.content}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* 底部指令发射台 (Geek Command Terminal Launcher) */}
          <div className="border-t border-[#30363d] bg-[#161b22]/90 backdrop-blur-md p-3 flex flex-col gap-2">
            {/* 极客快捷工具条 */}
            <div className="flex items-center justify-between text-zinc-400">
              <div className="flex items-center gap-1 font-mono text-3xs">
                <button
                  type="button"
                  onClick={handleInsertCodeSnippet}
                  className="px-2 py-1 rounded bg-[#21262d] hover:bg-[#30363d] hover:text-white flex items-center gap-1 transition-colors cursor-pointer"
                  title="插入代码块片段"
                >
                  <Code2 className="w-3.5 h-3.5 text-emerald-400" />
                  <span>代码块</span>
                </button>

                <button
                  type="button"
                  onClick={() => setInputText((inputText ? inputText + ' ' : '') + '⚡')}
                  className="px-2 py-1 rounded bg-[#21262d] hover:bg-[#30363d] hover:text-white flex items-center gap-1 transition-colors cursor-pointer"
                  title="插入极客标志"
                >
                  <Smile className="w-3.5 h-3.5 text-amber-400" />
                  <span>极客标志</span>
                </button>

                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="px-2 py-1 rounded bg-[#21262d] hover:bg-[#30363d] hover:text-white flex items-center gap-1 transition-colors cursor-pointer"
                  title="上传图片文件"
                >
                  <ImagePlus className="w-3.5 h-3.5 text-cyan-400" />
                  <span>图片</span>
                </button>
                <input
                  type="file"
                  ref={fileInputRef}
                  accept="image/*"
                  className="hidden"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file && onUploadImage) void onUploadImage(file);
                    e.target.value = '';
                  }}
                />
              </div>

              <div className="hidden sm:flex items-center gap-2 font-mono text-3xs text-zinc-500">
                <span>Enter 发送</span>
                <span>·</span>
                <span>Shift+Enter 换行</span>
                <span>·</span>
                <span className="text-emerald-400 font-semibold">Alt+1~6 摸鱼</span>
              </div>
            </div>

            {/* 命令行输入框容器 */}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!user) {
                  onRequireAuth();
                  return;
                }
                if (inputText.trim()) {
                  void onSendMessage(e);
                }
              }}
              className="flex items-center gap-2 bg-[#0d1117] border border-[#30363d] focus-within:border-emerald-500/60 rounded-lg p-2 transition-all shadow-inner"
            >
              <span className="text-emerald-400 font-mono text-xs select-none pl-1">geek@shendu:~$</span>
              <input
                type="text"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                placeholder="输入消息或代码指令（支持 Enter 极速发送）..."
                className="flex-1 bg-transparent border-0 outline-none text-xs text-zinc-100 placeholder:text-zinc-600 font-mono"
              />
              <button
                type="submit"
                disabled={!inputText.trim()}
                className="px-3 py-1 rounded bg-emerald-600 hover:bg-emerald-500 disabled:opacity-40 text-black font-mono font-bold text-2xs flex items-center gap-1 transition-colors cursor-pointer"
              >
                <Send className="w-3 h-3" />
                <span>EXEC</span>
              </button>
            </form>
          </div>
        </main>

        {/* C. 右侧：极客控制台监视看板 (Geek Inspector Panel) */}
        {showInspector && (
          <aside className="hidden xl:flex w-64 border-l border-[#30363d] bg-[#0d1117] flex-col shrink-0 p-3 space-y-4 font-mono text-xs overflow-y-auto">
            {/* 频道元信息卡片 */}
            <div className="bg-[#161b22] border border-[#30363d] rounded-lg p-3 space-y-2">
              <div className="flex items-center justify-between text-2xs text-zinc-400">
                <span className="font-bold uppercase">CHANNEL INFO</span>
                <Info className="w-3.5 h-3.5 text-cyan-400" />
              </div>
              <div className="text-2xs text-zinc-300 font-bold">{activeTitle}</div>
              <p className="text-3xs text-zinc-400 leading-relaxed">{activeDesc}</p>
            </div>

            {/* 极客在线成员矩阵 */}
            {chatTab === 'group' && (
              <div className="bg-[#161b22] border border-[#30363d] rounded-lg p-3 space-y-2 flex-1">
                <div className="flex items-center justify-between text-2xs text-zinc-400 pb-1 border-b border-[#30363d]">
                  <span className="font-bold">ACTIVE DEVS ({groupMembers.length})</span>
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                </div>
                <div className="space-y-1.5 overflow-y-auto max-h-72">
                  {groupMembers.map((m) => (
                    <div
                      key={m.user_id}
                      onClick={() => onSelectUser?.(m.username)}
                      className="flex items-center justify-between py-1 px-1.5 rounded hover:bg-[#21262d] text-2xs transition-colors cursor-pointer"
                    >
                      <div className="flex items-center gap-1.5 truncate">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                        <span className="text-zinc-300 truncate">{m.nickname || m.username}</span>
                      </div>
                      <span className="text-[9px] px-1 rounded bg-[#30363d] text-zinc-400 uppercase">
                        {m.role || 'DEV'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </aside>
        )}
      </div>

      {/* 3. 六大办公软件摸鱼伪装层 (支持 VSCode/IDEA/飞书/Excel/Word/PPT) */}
      {stealthTheme !== 'normal' && (
        <StealthWorkspaceCamouflage
          theme={stealthTheme}
          onThemeChange={setStealthTheme}
          activeTitle={activeTitle}
          conversations={
            chatTab === 'group'
              ? groups.map((g) => ({
                  id: g.id,
                  name: g.name,
                  isGroup: true,
                  unreadCount: g.unread_count,
                  lastMessage: g.last_message_preview,
                }))
              : conversations.map((c) => ({
                  id: c.id,
                  name: c.peer_nickname || c.peer_username || '开发者',
                  isGroup: false,
                  unreadCount: c.unread_count,
                  lastMessage: c.last_message_preview,
                }))
          }
          activeConvId={chatTab === 'group' ? activeGroupId || '' : activeConvId || ''}
          onSelectConversation={(id, isGroup) => {
            if (isGroup) {
              setActiveGroupId(id);
            } else {
              setActiveConvId(id);
            }
          }}
          messages={
            chatTab === 'group'
              ? groupMessages.map((gm) => ({
                  id: gm.id,
                  senderName: gm.sender_nickname || gm.sender_username,
                  senderAvatar: gm.sender_avatar,
                  content: gm.content,
                  timeStr: formatChatTime(gm.created_at),
                  isMe: gm.sender_id === user?.id,
                }))
              : messages.map((m) => ({
                  id: m.id,
                  senderName:
                    m.sender_id === user?.id
                      ? user?.nickname || user?.username || '我'
                      : activeConv?.peer_nickname || activeConv?.peer_username || '对方',
                  content: m.content,
                  timeStr: formatChatTime(m.created_at),
                  isMe: m.sender_id === user?.id,
                }))
          }
          onSendMessage={(text) => {
            void onSendMessage(undefined, text);
          }}
          onClose={() => setStealthTheme('normal')}
        />
      )}
    </div>
  );
};
