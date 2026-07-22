import React from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";

const TITLES = {
  "/dashboard": "대시보드", "/work-orders": "작업지시", "/lots": "LOT 추적",
  "/production": "생산 모니터링", "/quality": "품질 관리", "/materials": "자재 LOT",
  "/master-data": "기준정보", "/machines": "설비 관리", "/workers": "설비 관리", "/members": "사용자 관리",
  "/account": "내 계정",
};

export default function Header({ currentTime, onLogout, currentUser }) {
  const location = useLocation();
  const navigate = useNavigate();
  const logout = async () => { await onLogout?.(); navigate("/login", { replace: true }); };
  return <header className="top-header">
    <div className="header-left"><h2>EV Relay Mini MES</h2><span>{TITLES[location.pathname] || "MES"}</span></div>
    <div className="header-right">
      <span className="current-time"><span className="material-symbols-outlined">calendar_today</span>{currentTime}</span>
      {location.pathname === "/dashboard" && <button type="button" onClick={() => window.dispatchEvent(new Event("mes:refresh"))} className="icon-button header-refresh" title="대시보드 데이터 갱신"><span className="material-symbols-outlined">refresh</span></button>}
      <Link to="/account" className="header-profile" title="내 계정으로 이동"><span className="header-avatar material-symbols-outlined">person</span><span><small>{currentUser?.role}</small><strong>{currentUser?.memberName || currentUser?.loginId}</strong></span></Link>
      <button type="button" onClick={logout} className="icon-button" title="로그아웃"><span className="material-symbols-outlined">logout</span></button>
    </div>
  </header>;
}
