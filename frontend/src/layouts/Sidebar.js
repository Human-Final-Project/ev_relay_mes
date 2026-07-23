import React from "react";
import { Link, NavLink } from "react-router-dom";

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

export default function Sidebar({ collapsed, onToggle, collectorStatus, currentUser }) {
  const online = Boolean(collectorStatus?.l2Online);
  const isAdmin = currentUser?.role === "ADMIN";
  return <aside className={`sidebar ${collapsed ? "collapsed" : ""}`}>
    <div className="sidebar-brand">
      <div className="sidebar-brand-row">
        <Link className="sidebar-brand-link" to="/" title="홈으로 이동">
          <h1>EV Relay Mini MES</h1><p>System Operator</p>
        </Link>
        <button type="button" className="sidebar-toggle" onClick={onToggle} aria-label={collapsed ? "사이드바 펼치기" : "사이드바 접기"}>{collapsed ? "≫" : "≪"}</button>
      </div>
    </div>
    <nav className="sidebar-nav">
      {menus.map(([to, icon, label]) => <NavLink key={to} to={to} className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
        <span className="material-symbols-outlined">{icon}</span><span className="nav-label">{label}</span>
      </NavLink>)}
      {isAdmin && <NavLink to="/members" className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
        <span className="material-symbols-outlined">manage_accounts</span><span className="nav-label">사용자 관리</span>
      </NavLink>}
    </nav>
    <div className="sidebar-footer">
      <div className="connection-tile" title={online ? "L2 Collector heartbeat 정상" : "L2 Collector heartbeat 없음"}>
        <span className="connection-name"><span className="connection-short-label">L2</span><i className={`connection-dot ${online ? "" : "offline"}`}/><span>L2 Collector</span></span>
        <span className={`connection-value ${online ? "" : "offline"}`}>{online ? "ONLINE" : "OFFLINE"}</span>
      </div>
      <div className="connection-tile" title={(collectorStatus?.connectedMachineIds || []).join(", ") || "연결된 L1 설비 없음"}>
        <span className="connection-name"><span className="connection-short-label">L1</span><i className={`connection-dot ${online && collectorStatus?.connectedL1Count > 0 ? "" : "offline"}`}/><span>L1 Machines</span></span>
        <span className={`connection-value ${online ? "" : "offline"}`}>{collectorStatus?.connectedL1Count || 0} / {collectorStatus?.totalL1Count || 6}</span>
      </div>
    </div>
  </aside>;
}
