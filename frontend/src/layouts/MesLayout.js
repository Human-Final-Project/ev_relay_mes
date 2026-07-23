import React, { useState, useEffect } from "react";
import { Outlet } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";
import GlobalStyle from "../style/GlobalStyle";
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
    --background: #f8f9ff;
    --surface: #f8f9ff;
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
  .mesdash .sidebar { width: 230px; background-color: #f4f6fa; border-right: 1px solid #d9dee8; display: flex; flex-direction: column; flex-shrink: 0; z-index: 50; }
  .mesdash .sidebar-brand { display:block; min-height:96px; padding:20px 24px 22px; border-bottom:1px solid #e1e5ec; color:inherit; text-decoration:none; transition:background-color .15s; }
  .mesdash .sidebar-brand:hover { background-color:#edf2f8; }
  .mesdash .sidebar-brand:focus-visible { outline:2px solid rgba(5,102,217,.35); outline-offset:-2px; }
  .mesdash .sidebar-brand h1 { margin: 0; font-size: 21px; font-weight: 800; color: #15314f; line-height: 28px; letter-spacing: -.02em; }
  .mesdash .sidebar-brand p { margin: 2px 0 0; font-size: 10px; font-weight: 700; letter-spacing: 0.04em; color: #7b8796; }
  .mesdash .sidebar-nav { flex: 1; display: flex; flex-direction: column; gap: 3px; padding: 16px 10px; }
  .mesdash .nav-item { display: flex; align-items: center; gap: 12px; min-height: 40px; padding: 8px 13px; border-radius: 7px; text-decoration: none; color: #526174; font-size: 13px; font-weight: 600; transition: all 0.15s; cursor: pointer; border-left: 3px solid transparent; }
  .mesdash .nav-item .material-symbols-outlined { font-size: 19px; }
  .mesdash .nav-item:hover { background-color: var(--surface-container-high); }
  .mesdash .nav-item.active { color: #0566d9; font-weight: 800; background-color: #e2ebfb; border-left-color: #0566d9; }
  .mesdash .sidebar-footer { padding: 13px 12px 15px; border-top: 1px solid #dfe3ea; margin-top: auto; }
  .mesdash .system-connections { display: grid; gap: 7px; }
  .mesdash .connection-badge { display: grid; grid-template-columns: 8px 1fr auto; align-items: center; gap: 7px; min-height: 29px; padding: 6px 8px; border: 1px solid #dfe4eb; border-radius: 5px; background: #fff; }
  .mesdash .connection-dot { width: 7px; height: 7px; border-radius: 50%; background: #98a2b3; }
  .mesdash .connection-name { color: #526174; font-size: 10px; font-weight: 700; }
  .mesdash .connection-badge strong { color: #667085; font-family: "JetBrains Mono", monospace; font-size: 8px; }
  .mesdash .connection-online .connection-dot { background: #00a65a; box-shadow: 0 0 0 3px rgba(0,166,90,.1); }
  .mesdash .connection-online strong { color: #00874a; }
  .mesdash .connection-partial .connection-dot { background: #e59a00; box-shadow: 0 0 0 3px rgba(229,154,0,.1); }
  .mesdash .connection-partial strong { color: #b86f00; }
  .mesdash .connection-offline .connection-dot { background: #d92d20; box-shadow: 0 0 0 3px rgba(217,45,32,.09); }
  .mesdash .connection-offline strong { color: #b42318; }
  
  /* 우측 프레임 및 본문 영역 */
  .mesdash .main-container { flex: 1; display: flex; flex-direction: column; position: relative; min-width: 0; }
  .mesdash .content-area { flex: 1; padding: 18px; overflow-y: auto; background-color: #f5f7fb; }
  .mesdash .custom-scrollbar::-webkit-scrollbar { width: 4px; }
  .mesdash .custom-scrollbar::-webkit-scrollbar-track { background: var(--surface-container-low); }
  .mesdash .custom-scrollbar::-webkit-scrollbar-thumb { background: var(--outline-variant); border-radius: 2px; }
  
  /* 상단 헤더 스타일 */
  .mesdash .top-header { height: 58px; background-color: #fff; border-bottom: 1px solid #d9dee8; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; z-index: 40; }
  .mesdash .header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
  .mesdash .header-left h2 { margin: 0; font-size: 15px; font-weight: 800; color: #182230; white-space: nowrap; }
  .mesdash .header-left > span { padding-left: 12px; border-left: 1px solid #dfe3ea; color: #7b8796; font-size: 11px; font-weight: 700; }
  .mesdash .header-right { display: flex; align-items: center; gap: 12px; }
  .mesdash .tcp-status { display: flex; align-items: center; gap: var(--xs); background-color: var(--surface-container); padding: 4px var(--sm); border-radius: 9999px; border: 1px solid rgba(197, 198, 205, 0.2); flex-shrink: 0; }
  .mesdash .status-dot { width: 6px; height: 6px; border-radius: 50%; background-color: var(--tertiary); }
  .mesdash .glow-pulse { animation: mesdash-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
  @keyframes mesdash-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
  .mesdash .tcp-status span:last-child { font-family: "Inter", sans-serif; font-size: 11px; font-weight: 600; color: var(--tertiary); }
  .mesdash .current-time { display: flex; align-items: center; gap: 7px; font-family: "JetBrains Mono", monospace; font-size: 10px; color: #526174; white-space: nowrap; margin-right: 2px; }
  .mesdash .current-time .material-symbols-outlined { font-size: 14px; }
  .mesdash .header-refresh { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 6px; }
  .mesdash .header-refresh:hover { background: #edf2f8; }
  .mesdash .header-profile { display: flex; align-items: center; gap: 8px; padding: 4px 7px 4px 12px; border-left: 1px solid #e1e5ec; border-radius: 5px; color: inherit; text-decoration: none; }
  .mesdash .header-profile:hover { background: #f2f5f9; }
  .mesdash .header-profile > span:last-child { display: flex; flex-direction: column; line-height: 1.2; }
  .mesdash .header-profile small { color: #0566d9; font-size: 9px; font-weight: 800; text-transform: uppercase; }
  .mesdash .header-profile strong { color: #273444; font-size: 11px; white-space: nowrap; }
  .mesdash .header-avatar { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 6px; border: 1px solid #d4dbe5; background: #edf2f8; color: #526174; font-size: 20px; }
  .mesdash .user-actions { display: flex; align-items: center; gap: var(--md); }
  .mesdash .user-actions button { background: none; border: none; padding: 4px; cursor: pointer; color: var(--on-surface-variant); display: flex; align-items: center; justify-content: center; }
  .mesdash .user-actions button:hover { color: var(--primary); }
  .mesdash .user-avatar { width: 32px; height: 32px; border-radius: 50%; overflow: hidden; border: 1px solid var(--outline-variant); background-color: var(--surface-container); flex-shrink: 0; }
  .mesdash .user-avatar img { width: 100%; height: 100%; object-fit: cover; }
  
  /* 🔔 알림 버튼 및 드롭다운 토글 기능용 전역 스타일 디자인 추가 */
  .mesdash .user-actions button { position: relative; }
  .mesdash .user-actions button .notification-badge {
    position: absolute; top: 2px; right: 2px; width: 6px; height: 6px;
    background-color: var(--error); border-radius: 50%;
  }
  .mesdash .noti-dropdown {
    position: absolute; top: 50px; right: 0; width: 320px; max-height: 400px;
    background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant);
    border-radius: 8px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); z-index: 100; overflow-y: auto;
  }
  .mesdash .noti-header {
    display: flex; justify-content: space-between; align-items: center;
    padding: var(--sm) var(--md); border-bottom: 1px solid var(--outline-variant);
    font-size: 13px; font-weight: 700; background-color: var(--surface-container-low);
  }
  .mesdash .noti-count { font-size: 11px; color: var(--outline); font-weight: normal; }
  .mesdash .noti-list { display: flex; flex-direction: column; }
  .mesdash .noti-item { padding: var(--md); border-bottom: 1px solid rgba(197, 198, 205, 0.2); border-left: 4px solid transparent; text-align: left; }
  .mesdash .noti-item.info { border-left-color: var(--primary); background-color: rgba(5, 102, 217, 0.02); }
  .mesdash .noti-item.warn { border-left-color: #b78103; background-color: rgba(183, 129, 3, 0.02); }
  .mesdash .noti-item.error { border-left-color: var(--error); background-color: rgba(186, 26, 26, 0.04); }
  .mesdash .noti-item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
  .mesdash .noti-title { font-size: 13px; font-weight: 700; }
  .mesdash .noti-item.info .noti-title { color: var(--primary); }
  .mesdash .noti-item.warn .noti-title { color: #b78103; }
  .mesdash .noti-item.error .noti-title { color: var(--error); }
  .mesdash .noti-time { font-family: "JetBrains Mono", monospace; font-size: 11px; color: var(--outline); }
  .mesdash .noti-desc { margin: 0; font-size: 12px; color: var(--on-surface-variant); line-height: 1.4; }
`;

const MesLayout = ({ onLogout, currentUser }) => {
  const [currentTime, setCurrentTime] = useState("");

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      const date = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, "0"), String(now.getDate()).padStart(2, "0")].join("-");
      setCurrentTime(`${date} ${now.toTimeString().split(" ")[0]}`);
    };
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <>
      <GlobalStyle />
      <style>{layoutStyles}</style>
      <div className="mesdash">
        <div className="dashboard-layout">
          <Sidebar currentUser={currentUser} />
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
