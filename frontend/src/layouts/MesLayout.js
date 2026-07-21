import React, { useState, useEffect } from "react";
import { Outlet } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";
import Footer from "./Footer";
import GlobalStyle from "../style/GlobalStyle";
import LowStockAlertModal from "../modal/LowStockAlertModal";

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
  .mesdash .sidebar { width: 256px; background-color: var(--surface-container-low); border-right: 1px solid var(--outline-variant); display: flex; flex-direction: column; flex-shrink: 0; z-index: 50; }
  .mesdash .sidebar-brand { padding: var(--md) var(--lg) var(--xl); }
  .mesdash .sidebar-brand h1 { margin: 0; font-size: 20px; font-weight: 700; color: var(--primary); line-height: 28px; }
  .mesdash .sidebar-brand p { margin: 0; font-size: 11px; font-weight: 700; letter-spacing: 0.05em; text-transform: uppercase; color: var(--on-surface-variant); }
  .mesdash .sidebar-nav { flex: 1; display: flex; flex-direction: column; gap: var(--xs); padding: 0 var(--sm); }
  .mesdash .nav-item { display: flex; align-items: center; gap: var(--md); padding: var(--sm) var(--md); text-decoration: none; color: var(--on-surface-variant); font-size: 16px; transition: all 0.2s; cursor: pointer; border-right: 4px solid transparent; }
  .mesdash .nav-item:hover { background-color: var(--surface-container-high); }
  .mesdash .nav-item.active { color: var(--primary); font-weight: 700; background-color: var(--primary-container); border-right-color: var(--primary); }
  .mesdash .sidebar-footer { padding: var(--md) var(--sm) 0; border-top: 1px solid rgba(197, 198, 205, 0.3); margin-top: auto; margin-bottom: 40px; }
  
  /* 우측 프레임 및 본문 영역 */
  .mesdash .main-container { flex: 1; display: flex; flex-direction: column; position: relative; min-width: 0; }
  .mesdash .content-area { flex: 1; padding: var(--lg); overflow-y: auto; background-color: var(--background); }
  .mesdash .custom-scrollbar::-webkit-scrollbar { width: 4px; }
  .mesdash .custom-scrollbar::-webkit-scrollbar-track { background: var(--surface-container-low); }
  .mesdash .custom-scrollbar::-webkit-scrollbar-thumb { background: var(--outline-variant); border-radius: 2px; }
  
  /* 상단 헤더 스타일 */
  .mesdash .top-header { height: 56px; background-color: var(--surface-container-lowest); border-bottom: 1px solid var(--outline-variant); display: flex; justify-content: space-between; align-items: center; padding: 0 var(--lg); z-index: 40; }
  .mesdash .header-left { display: flex; align-items: center; gap: var(--xl); }
  .mesdash .header-left h2 { margin: 0; font-size: 20px; font-weight: 900; color: var(--on-surface); }
  .mesdash .header-right { display: flex; align-items: center; gap: var(--md); }
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
  
  /* 하단 푸터 스타일 */
  .mesdash .footer { height: 40px; background-color: var(--surface-container-lowest); border-top: 1px solid rgba(197, 198, 205, 0.3); display: flex; align-items: center; justify-content: space-between; padding: 0 var(--lg); font-size: 10px; font-weight: 700; letter-spacing: 0.05em; color: var(--outline); margin-top: auto; }
  .mesdash .footer-links { display: flex; gap: var(--lg); }
  .mesdash .footer-copyright { display: flex; align-items: center; gap: var(--md); }
  .mesdash .footer-copyright strong { color: var(--on-surface); }

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

const MesLayout = () => {
  const [currentTime, setCurrentTime] = useState("");

  useEffect(() => {
    const updateTime = () => {
      setCurrentTime(new Date().toTimeString().split(" ")[0]);
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
        {/* 관리자 로그인 시 제일 먼저 뜨는 재고 부족 알림 */}
        <LowStockAlertModal />
        <div className="dashboard-layout">
          <Sidebar />
          <div className="main-container">
            <Header currentTime={currentTime} />
            <main className="content-area custom-scrollbar">
              <Outlet />
            </main>
            <Footer />
          </div>
        </div>
      </div>
    </>
  );
};

export default MesLayout;