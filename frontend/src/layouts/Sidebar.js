import React from "react";
import { NavLink } from "react-router-dom";

const menus = [
  ["/dashboard", "dashboard", "대시보드"],
  ["/work-orders", "assignment", "작업지시"],
  ["/lots", "conversion_path", "LOT 추적"],
  ["/production", "precision_manufacturing", "생산 모니터링"],
  ["/alarms", "warning", "설비 알람"],
  ["/quality", "verified_user", "품질 관리"],
  ["/materials", "inventory_2", "자재 LOT"],
  ["/master-data", "database", "기준정보"],
  ["/workers", "engineering", "작업자 배정"],
];

export default function Sidebar({ currentUser }) {
  const isAdmin = currentUser?.role === "ADMIN";
  return <aside className="sidebar">
    <div className="sidebar-brand"><h1>Mini MES</h1><p>EV RELAY PRODUCTION</p></div>
    <nav className="sidebar-nav">
      {menus.map(([to, icon, label]) => <NavLink key={to} to={to} className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
        <span className="material-symbols-outlined">{icon}</span><span>{label}</span>
      </NavLink>)}
      {isAdmin && <NavLink to="/members" className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
        <span className="material-symbols-outlined">manage_accounts</span><span>사용자 관리</span>
      </NavLink>}
    </nav>
    <div className="sidebar-footer">
      <NavLink to="/account" className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
        <span className="material-symbols-outlined">account_circle</span>
        <span>{currentUser?.memberName || currentUser?.loginId || "내 계정"}</span>
      </NavLink>
    </div>
  </aside>;
}
