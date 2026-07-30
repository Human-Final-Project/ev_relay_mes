import React, { useState, useEffect } from "react";
import { Outlet } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";
import GlobalStyle from "../style/GlobalStyle";
import MesApi from "../api/MesApi";
import "../style/MesUi.css";

// 폰트 및 핵심 레이아웃 디자인 전체 저장
const layoutStyles = `
  @import url("https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@500&display=swap");
  @import url("https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap");

  .mesdash {
    --primary: #0566d9;
    --primary-container: #d8e2ff;
    --secondary: #0566d9;
    --tertiary: #006d48;
    --error: #ba1a1a;
    --background: #f4f7fb;
    --surface: #f4f7fb;
    --surface-container-lowest: #ffffff;
    --surface-container-low: #f2f3f7;
    --surface-container: #eceef4;
    --surface-container-high: #e7e8ec;
    --on-background: #191c20;
    --on-surface: #191c20;
    --on-surface-variant: #45474c;
    --outline: #75777d;
    --outline-variant: #c5c6cd;
    --xs: 4px; --sm: 8px; --md: 16px; --lg: 24px; --xl: 32px;
    font-family: "Inter", sans-serif;
    background-color: var(--background);
    color: var(--on-background);
    height: 100vh; width: 100%; overflow: hidden; position: relative;
  }
  
  .mesdash * { box-sizing: border-box; }
  .mesdash .material-symbols-outlined { font-variation-settings: "FILL" 0, "wght" 400, "GRAD" 0, "opsz" 24; }
  .mesdash .dashboard-layout { display: flex; height: 100%; width: 100%; }
  
  /* 사이드바 스타일 */
  .mesdash .sidebar { width:264px; background:linear-gradient(180deg,#0a2c55 0%,#061e3d 100%); border-right:1px solid #173e68; display:flex; flex-direction:column; flex-shrink:0; z-index:50; color:#fff; transition:width .2s ease; overflow:hidden; box-shadow:8px 0 30px rgba(3,20,43,.08); }
  .mesdash .sidebar.collapsed { width:76px; }
  .mesdash .sidebar-brand { min-height:92px; padding:17px 16px; border-bottom:1px solid rgba(255,255,255,.1); }
  .mesdash .sidebar-brand-row { display:flex; align-items:center; justify-content:space-between; gap:10px; }
  .mesdash .sidebar-brand-link { min-width:0; display:flex; align-items:center; gap:11px; color:#fff; text-decoration:none; }
  .mesdash .sidebar-brand-mark { width:38px; height:38px; display:grid; place-items:center; flex:0 0 38px; border:1px solid rgba(255,255,255,.18); border-radius:11px; background:linear-gradient(145deg,#1681f4,#0755bd); box-shadow:0 8px 18px rgba(0,76,181,.35); }
  .mesdash .sidebar-brand-mark .material-symbols-outlined { font-size:22px; font-variation-settings:"FILL" 1,"wght" 500; }
  .mesdash .sidebar-brand-copy { min-width:0; }
  .mesdash .sidebar-brand h1 { margin:0 0 3px; font-size:17px; font-weight:850; line-height:21px; white-space:nowrap; }
  .mesdash .sidebar-brand p { margin:0; font-size:9px; font-weight:600; color:#abc8e8; letter-spacing:.04em; white-space:nowrap; text-transform:uppercase; }
  .mesdash .sidebar-toggle { width:28px; height:28px; border:1px solid rgba(255,255,255,.2); border-radius:8px; background:rgba(255,255,255,.06); color:#dcecff; cursor:pointer; flex:0 0 auto; transition:background .18s; }
  .mesdash .sidebar-toggle:hover { background:rgba(255,255,255,.13); }
  .mesdash .sidebar-nav { flex:1; display:flex; flex-direction:column; gap:4px; padding:14px 13px; overflow-y:auto; }
  .mesdash .nav-item { min-height:43px; display:flex; align-items:center; gap:13px; padding:9px 13px; border-radius:9px; text-decoration:none; color:#dceafb; font-size:13px; font-weight:650; transition:all .18s; white-space:nowrap; }
  .mesdash .nav-item .material-symbols-outlined { font-size:22px; flex:0 0 22px; }
  .mesdash .nav-item:hover { color:#fff; background:rgba(255,255,255,.08); transform:translateX(2px); }
  .mesdash .nav-item.active { color:#fff; background:linear-gradient(135deg,#117bed,#1255b7); box-shadow:0 7px 18px rgba(0,72,170,.34); }
  .mesdash .sidebar-footer { display:grid; gap:10px; padding:14px 18px 18px; margin-top:auto; }
  .mesdash .connection-tile { min-height:42px; display:flex; align-items:center; justify-content:space-between; gap:10px; padding:0 13px; border:1px solid rgba(153,196,241,.24); border-radius:8px; color:#eaf4ff; font-size:12px; font-weight:700; white-space:nowrap; }
  .mesdash .connection-name { display:flex; align-items:center; gap:9px; }
  .mesdash .connection-short-label { display:none; color:#eaf4ff; font-size:10px; font-weight:850; letter-spacing:.02em; }
  .mesdash .connection-value { color:#17db79; font-size:11px; }
  .mesdash .connection-value.offline { color:#ff6570; }
  .mesdash .connection-dot { width:11px; height:11px; border-radius:50%; background:#17d87a; box-shadow:0 0 10px rgba(23,216,122,.55); }
  .mesdash .connection-dot.offline { background:#ff5964; box-shadow:0 0 10px rgba(255,89,100,.45); }
  .mesdash .sidebar.collapsed .sidebar-brand { min-height:108px; padding:14px 10px; }
  .mesdash .sidebar.collapsed .sidebar-brand-copy,.mesdash .sidebar.collapsed .nav-label,.mesdash .sidebar.collapsed .connection-name span:last-child,.mesdash .sidebar.collapsed .connection-value { display:none; }
  .mesdash .sidebar.collapsed .connection-short-label { display:inline; }
  .mesdash .sidebar.collapsed .sidebar-brand-row { flex-direction:column; justify-content:center; }
  .mesdash .sidebar.collapsed .sidebar-nav { padding:12px 9px; }
  .mesdash .sidebar.collapsed .nav-item { justify-content:center; padding:9px; }
  .mesdash .sidebar.collapsed .sidebar-footer { padding:14px 10px 18px; }
  .mesdash .sidebar.collapsed .connection-tile { justify-content:center; padding:0; }
  .mesdash .sidebar.collapsed .connection-name { flex-direction:column; gap:4px; }
  .mesdash .sidebar.collapsed .connection-dot { width:8px; height:8px; }
  
  /* 우측 프레임 및 본문 영역 */
  .mesdash .main-container { flex: 1; display: flex; flex-direction: column; position: relative; min-width: 0; }
  .mesdash .content-area { flex:1; padding:28px clamp(20px,2.5vw,38px) 42px; overflow-y:auto; background:linear-gradient(180deg,#f7f9fc 0,#f3f6fa 100%); }
  .mesdash .content-area> * { width:100%; max-width:1600px; margin-left:auto; margin-right:auto; }
  .mesdash .custom-scrollbar::-webkit-scrollbar { width: 4px; }
  .mesdash .custom-scrollbar::-webkit-scrollbar-track { background: var(--surface-container-low); }
  .mesdash .custom-scrollbar::-webkit-scrollbar-thumb { background: var(--outline-variant); border-radius: 2px; }
  
  /* 상단 헤더 스타일 */
  .mesdash .top-header { height:72px; background-color:rgba(255,255,255,.96); border-bottom:1px solid #e1e7ef; display:flex; justify-content:space-between; align-items:center; padding:0 clamp(18px,2.4vw,34px); z-index:40; box-shadow:0 3px 14px rgba(15,23,42,.035); backdrop-filter:blur(12px); }
  .mesdash .header-left { display: flex; align-items: center; gap: var(--xl); }
  .mesdash .header-left h2 { margin:0; font-size:18px; font-weight:850; color:#0e1f3a; white-space:nowrap; letter-spacing:-.02em; }
  .mesdash .header-breadcrumb { color:#4d607d; font-size:13px; font-weight:700; }
  .mesdash .header-divider { color:#9cabc0; }
  .mesdash .header-right { display: flex; align-items: center; gap: var(--md); }
  .mesdash .live-copy { display:flex; align-items:center; gap:7px; color:#263c5e; font-size:12px; font-weight:700; white-space:nowrap; }
  .mesdash .header-separator { width:1px; height:34px; background:#e0e6ee; }
  .mesdash .profile-link { display:flex; align-items:center; gap:9px; padding:5px 8px 5px 5px; border-radius:10px; color:#132746; text-decoration:none; transition:background .18s; }
  .mesdash .profile-link:hover { background:#f2f6fb; }
  .mesdash .profile-avatar { width:36px; height:36px; display:grid; place-items:center; border-radius:10px; color:#2563eb; background:#eaf2ff; border:1px solid #d7e5f8; }
  .mesdash .profile-copy { display:block; text-align:left; line-height:1.2; }
  .mesdash .profile-copy strong { display:block; font-size:12px; }
  .mesdash .profile-copy>span { color:#64748b; font-size:9px; font-weight:700; letter-spacing:.05em; }
  .mesdash .tcp-status { display: flex; align-items: center; gap: var(--xs); background-color: var(--surface-container); padding: 4px var(--sm); border-radius: 9999px; border: 1px solid rgba(197, 198, 205, 0.2); flex-shrink: 0; }
  .mesdash .status-dot { width: 6px; height: 6px; border-radius: 50%; background-color: var(--tertiary); }
  .mesdash .glow-pulse { animation: mesdash-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
  @keyframes mesdash-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
  .mesdash .tcp-status span:last-child { font-family: "Inter", sans-serif; font-size: 11px; font-weight: 600; color: var(--tertiary); }
  .mesdash .current-time { font-family: "JetBrains Mono", monospace; font-size: 11px; color: var(--on-surface-variant); white-space: nowrap; margin-right: var(--xs); }
  .mesdash .user-actions { display: flex; align-items: center; gap: var(--md); }
  .mesdash .user-actions button { background: none; border: none; padding: 4px; cursor: pointer; color: var(--on-surface-variant); display: flex; align-items: center; justify-content: center; }
  .mesdash .user-actions button:hover { color: var(--primary); }
  .mesdash .user-avatar { width: 32px; height: 32px; border-radius: 50%; overflow: hidden; border: 1px solid var(--outline-variant); background-color: var(--surface-container); flex-shrink: 0; }
  .mesdash .user-avatar img { width: 100%; height: 100%; object-fit: cover; }
  

  .mesdash .notification-center { position:relative; }
  .mesdash .notification-button { position:relative; width:36px; height:36px; border:1px solid #dfe6ee; border-radius:10px; background:#fff; color:#50647f; }
  .mesdash .notification-button:hover { color:#2563eb; background:#f2f6fb; }
  .mesdash .notification-button .material-symbols-outlined { font-size:21px; }
  .mesdash .notification-dot { position:absolute; top:6px; right:7px; width:8px; height:8px; border:2px solid #fff; border-radius:50%; background:#dc2626; box-shadow:0 0 0 1px rgba(220,38,38,.16); }
  .mesdash .noti-dropdown {
    position:absolute; top:44px; right:0; width:min(380px,calc(100vw - 24px)); max-height:480px;
    background:#fff; border:1px solid #dbe4ee;
    border-radius:13px; box-shadow:0 18px 48px rgba(15,35,64,.2); z-index:100; overflow:hidden;
  }
  .mesdash .noti-header {
    display:flex; justify-content:space-between; align-items:center;
    padding:13px 15px; border-bottom:1px solid #e4eaf1;
    font-size:13px; font-weight:700; background:#f7f9fc;
  }
  .mesdash .noti-header>div { display:flex; align-items:center; gap:8px; }
  .mesdash .noti-header button { border:0; background:transparent; color:#2563eb; font-size:10px; font-weight:800; cursor:pointer; }
  .mesdash .noti-count { padding:3px 6px; border-radius:999px; color:#dc2626; background:#fee2e2; font-size:9px; font-weight:800; }
  .mesdash .noti-list { max-height:350px; display:flex; flex-direction:column; overflow-y:auto; }
  .mesdash .noti-item { width:100%; display:grid; gap:5px; padding:12px 14px; border:0; border-bottom:1px solid #edf1f5; border-left:4px solid transparent; background:#fff; color:inherit; text-align:left; cursor:pointer; }
  .mesdash .noti-item:hover { background:#f7faff; }
  .mesdash .noti-item.read { opacity:.72; }
  .mesdash .noti-item.info { border-left-color:var(--primary); }
  .mesdash .noti-item.warn { border-left-color:#d97706; }
  .mesdash .noti-item.error { border-left-color:var(--error); background:#fffafa; }
  .mesdash .noti-item-header { display:flex; justify-content:space-between; align-items:center; gap:8px; }
  .mesdash .noti-title { overflow:hidden; font-size:12px; font-weight:800; text-overflow:ellipsis; white-space:nowrap; }
  .mesdash .noti-item.info .noti-title { color: var(--primary); }
  .mesdash .noti-item.warn .noti-title { color: #b78103; }
  .mesdash .noti-item.error .noti-title { color: var(--error); }
  .mesdash .noti-unread-dot { width:7px; height:7px; flex:0 0 7px; border-radius:50%; background:#dc2626; }
  .mesdash .noti-time { color:#8795a8; font-family:"JetBrains Mono",monospace; font-size:8px; }
  .mesdash .noti-desc { display:block; overflow:hidden; color:#52677f; font-size:10px; line-height:1.4; text-overflow:ellipsis; white-space:nowrap; }
  .mesdash .noti-empty { padding:36px 16px; color:#7890aa; font-size:11px; text-align:center; }
  .mesdash .noti-all-link { display:block; padding:11px; border-top:1px solid #e4eaf1; color:#2563eb; background:#f8fafc; font-size:10px; font-weight:800; text-align:center; text-decoration:none; }

  @media(max-width:900px) {
    .mesdash .sidebar { width:76px; }
    .mesdash .sidebar-brand { min-height:108px; padding:14px 10px; }
    .mesdash .sidebar-brand-row { flex-direction:column; justify-content:center; }
    .mesdash .sidebar-brand-copy,.mesdash .nav-label,.mesdash .connection-name span:last-child,.mesdash .connection-value { display:none; }
    .mesdash .connection-short-label { display:inline; }
    .mesdash .sidebar-nav { padding:12px 9px; }
    .mesdash .nav-item { justify-content:center; padding:9px; }
    .mesdash .sidebar-footer { padding:12px 9px 16px; }
    .mesdash .connection-tile { justify-content:center; padding:0; }
    .mesdash .connection-name { flex-direction:column; gap:4px; }
    .mesdash .header-left { gap:12px; }
    .mesdash .live-copy,.mesdash .header-divider,.mesdash .header-breadcrumb { display:none; }
    .mesdash .content-area { padding:22px 18px 34px; }
  }
  @media(max-width:620px) {
    .mesdash .sidebar { width:64px; }
    .mesdash .sidebar-brand-mark { width:34px; height:34px; flex-basis:34px; }
    .mesdash .sidebar-toggle { width:26px; height:26px; }
    .mesdash .top-header { height:64px; padding:0 12px; }
    .mesdash .header-left h2 { font-size:14px; }
    .mesdash .current-time,.mesdash .profile-copy,.mesdash .header-separator { display:none; }
    .mesdash .header-right { gap:7px; }
    .mesdash .content-area { padding:18px 12px 30px; }
  }
`;

const MesLayout = ({ onLogout, currentUser }) => {
  const [currentTime, setCurrentTime] = useState("");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [collectorStatus, setCollectorStatus] = useState(null);

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      const pad = (value) => String(value).padStart(2, "0");
      setCurrentTime(`${now.getFullYear()}-${pad(now.getMonth()+1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`);
    };
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    let active = true;
    const loadStatus = () => MesApi.getCollectorStatus()
      .then((response) => active && setCollectorStatus({ ...response.data, statusAvailable: true }))
      .catch(() => active && setCollectorStatus({
        statusAvailable: false,
        l2Online: false,
        connectedL1Count: 0,
        totalL1Count: 6,
        connectedMachineIds: [],
      }));
    loadStatus();
    const timer = setInterval(loadStatus, 1000);
    return () => { active = false; clearInterval(timer); };
  }, []);

  return (
    <>
      <GlobalStyle />
      <style>{layoutStyles}</style>
      <div className="mesdash">
        <div className="dashboard-layout">
          <Sidebar
            collapsed={sidebarCollapsed}
            onToggle={() => setSidebarCollapsed((value) => !value)}
            collectorStatus={collectorStatus}
            currentUser={currentUser}
          />
          <div className="main-container">
            <Header currentTime={currentTime} onLogout={onLogout} currentUser={currentUser} />
            <main className="content-area custom-scrollbar">
              <Outlet />
            </main>
          </div>
        </div>
      </div>
    </>
  );
};

export default MesLayout;
