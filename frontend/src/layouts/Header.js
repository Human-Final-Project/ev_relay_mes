import React from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";

const TITLES = {
  "/dashboard": "대시보드", "/work-orders": "작업지시", "/lots": "LOT 추적",
  "/production": "생산 모니터링", "/alarms": "설비 알람", "/quality": "품질 관리", "/materials": "자재 LOT",
  "/master-data": "기준정보", "/workers": "작업자 배정", "/members": "사용자 관리",
  "/account": "내 계정",
};

export default function Header({ currentTime, onLogout, currentUser }) {
  const location = useLocation();
  const navigate = useNavigate();
  const logout = async () => { await onLogout?.(); navigate("/login", { replace: true }); };
  return <header className="top-header">
    <div className="header-left"><h2>EV Relay Mini MES</h2><span className="header-divider">/</span><span className="header-breadcrumb">{TITLES[location.pathname] || "MES"}</span></div>
    <div className="header-right">
      <span className="live-copy">설비 1초 / 집계 5초</span>
      <span className="header-separator"/>
      <span className="current-time">{currentTime}</span>
      <span className="header-separator"/>
      <Link to="/account" className="profile-link"><span className="profile-avatar"><span className="material-symbols-outlined">person</span></span><span style={{ textAlign:"left",lineHeight:1.2 }}><strong style={{ display:"block",fontSize:12 }}>{currentUser?.memberName || currentUser?.loginId}</strong><span style={{ fontSize:10,color:"#64748b" }}>{currentUser?.role}</span></span></Link>
      <button type="button" onClick={logout} className="icon-button" title="로그아웃"><span className="material-symbols-outlined">logout</span></button>
    </div>
  </header>;
}
