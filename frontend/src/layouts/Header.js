import React from "react";
import { useLocation, useNavigate } from "react-router-dom";

const TITLES = {
  "/dashboard": "대시보드", "/work-orders": "작업지시", "/lots": "LOT 추적",
  "/production": "생산 모니터링", "/quality": "품질 관리", "/materials": "자재 LOT",
  "/master-data": "기준정보", "/workers": "작업자 배정", "/members": "사용자 관리",
  "/account": "내 계정",
};

export default function Header({ currentTime, onLogout, currentUser }) {
  const location = useLocation();
  const navigate = useNavigate();
  const logout = async () => { await onLogout?.(); navigate("/login", { replace: true }); };
  return <header className="top-header">
    <div className="header-left"><h2>{TITLES[location.pathname] || "EV Relay MES"}</h2></div>
    <div className="header-right">
      <span className="current-time">{currentTime}</span>
      <div style={{ textAlign:"right", lineHeight:1.2 }}><strong style={{ display:"block",fontSize:12 }}>{currentUser?.memberName || currentUser?.loginId}</strong><span style={{ fontSize:10,color:"#64748b" }}>{currentUser?.role}</span></div>
      <button type="button" onClick={logout} className="icon-button" title="로그아웃"><span className="material-symbols-outlined">logout</span></button>
    </div>
  </header>;
}
