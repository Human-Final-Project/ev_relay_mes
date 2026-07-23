import React, { useEffect } from "react";
import { Link, NavLink } from "react-router-dom";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";

const menus = [
  ["/dashboard", "dashboard", "대시보드"],
  ["/work-orders", "assignment", "작업지시"],
  ["/lots", "conversion_path", "LOT 추적"],
  ["/production", "precision_manufacturing", "생산 모니터링"],
  ["/quality", "verified_user", "품질 관리"],
  ["/materials", "inventory_2", "자재 LOT"],
  ["/master-data", "database", "기준정보"],
  ["/machines", "manufacturing", "설비 관리"],
];

export default function Sidebar({ currentUser }) {
  const connections = useApiData(MesApi.getSystemConnections, []);
  const isAdmin = currentUser?.role === "ADMIN";

  useEffect(() => {
    const timer = setInterval(connections.reload, 5000);
    return () => clearInterval(timer);
  // reload is stable for the lifetime of useApiData.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connections.reload]);

  const l2 = connections.data?.l2 || { status: "OFFLINE" };
  const l1 = connections.data?.l1 || { status: "OFFLINE", connected: 0, total: 6 };
  const unavailable = connections.error && connections.data === null;

  return <aside className="sidebar">
    <Link to="/" className="sidebar-brand" aria-label="Mini MES 홈으로 이동"><h1>Mini MES</h1><p>System Operator</p></Link>
    <nav className="sidebar-nav">
      {menus.map(([to, icon, label]) => <NavLink key={to} to={to} className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
        <span className="material-symbols-outlined">{icon}</span><span>{label}</span>
      </NavLink>)}
      {isAdmin && <NavLink to="/members" className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
        <span className="material-symbols-outlined">manage_accounts</span><span>사용자 관리</span>
      </NavLink>}
    </nav>
    <div className="sidebar-footer system-connections" aria-label="시스템 연결 상태" title={unavailable ? "Backend에서 연결 상태를 조회할 수 없습니다." : "5초마다 연결 상태를 확인합니다."}>
      <ConnectionBadge label="L2 Collector" status={unavailable ? "OFFLINE" : l2.status} value={unavailable ? "조회 실패" : l2.status}/>
      <ConnectionBadge label="L1 Machines" status={unavailable ? "OFFLINE" : l1.status} value={`${unavailable ? 0 : l1.connected}/${l1.total || 6}`}/>
    </div>
  </aside>;
}

function ConnectionBadge({ label, status, value }) {
  const normalized = String(status || "OFFLINE").toLowerCase();
  return <div className={`connection-badge connection-${normalized}`}><span className="connection-dot"/><span className="connection-name">{label}</span><strong>{value}</strong></div>;
}
