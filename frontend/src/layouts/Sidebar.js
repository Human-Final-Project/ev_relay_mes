import React, { useMemo, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";

const GROUPS = [
  { label:"모니터링", icon:"monitoring", items:[
    ["/dashboard","dashboard","대시보드"],
  ]},
  { label:"기준 정보 관리", icon:"database", items:[
    ["/master/machines","precision_manufacturing","설비 관리"],
    ["/master/processes","account_tree","공정 관리"],
    ["/master/products","package_2","제품 관리"],
    ["/master/materials","category","자재 관리"],
    ["/master/boms","schema","BOM 관리"],
    ["/master/workers","engineering","작업자 관리"],
  ]},
  { label:"생산 관리", icon:"factory", items:[
    ["/production/work-orders","assignment","작업지시 관리"],
    ["/production/history","history","공정 이력"],
    ["/production/lots","conversion_path","제품 LOT 관리"],
  ]},
  { label:"품질 관리", icon:"verified_user", items:[
    ["/quality/inspections","fact_check","검사 이력"],
    ["/quality/defects","report_problem","불량 이력"],
  ]},
  { label:"자재/제품 관리", icon:"inventory_2", items:[
    ["/inventory/material-lots","deployed_code","자재 LOT 관리"],
    ["/inventory/material-stock","inventory","자재 재고 조회"],
    ["/inventory/material-transactions","swap_vert","자재 입출고 이력 조회"],
    ["/inventory/product-stock","warehouse","제품 재고 조회"],
    ["/inventory/product-transactions","local_shipping","제품 입출고 이력 조회"],
  ]},
  { label:"리포트/조회", icon:"analytics", items:[
    ["/reports/production","bar_chart","생산 리포트"],
    ["/reports/traceability","timeline","Traceability 조회"],
  ]},
];

export default function Sidebar({ collapsed, onToggle, collectorStatus, currentUser }) {
  const location=useLocation();
  const online=Boolean(collectorStatus?.l2Online);
  const initial=useMemo(()=>Object.fromEntries(GROUPS.map((group,index)=>[index,group.items.some(([to])=>location.pathname.startsWith(to))||index===0])),[]); // eslint-disable-line react-hooks/exhaustive-deps
  const [open,setOpen]=useState(initial);
  return <aside className={`sidebar ${collapsed?"collapsed":""}`}>
    <div className="sidebar-brand"><div className="sidebar-brand-row">
      <Link className="sidebar-brand-link" to="/" title="홈으로 이동"><h1>EV Relay MES</h1><p>Manufacturing Execution System</p></Link>
      <button type="button" className="sidebar-toggle" onClick={onToggle} aria-label={collapsed?"사이드바 펼치기":"사이드바 접기"}>{collapsed?"›":"‹"}</button>
    </div></div>
    <nav className="sidebar-nav">
      {GROUPS.map((group,index)=><section className={`nav-group ${open[index]?"open":""}`} key={group.label}>
        <button className="nav-group-toggle" type="button" title={group.label} onClick={()=>collapsed?setOpen({[index]:true}):setOpen(value=>({...value,[index]:!value[index]}))}>
          <span className="material-symbols-outlined">{group.icon}</span><span className="nav-label">{group.label}</span><span className="nav-label nav-chevron">expand_more</span>
        </button>
        {open[index]&&<div className="nav-group-items">{group.items.map(([to,icon,label])=><NavLink key={to} to={to} title={label} className={({isActive})=>`nav-item ${isActive?"active":""}`}><span className="material-symbols-outlined">{icon}</span><span className="nav-label">{label}</span></NavLink>)}</div>}
      </section>)}
      {currentUser?.role==="ADMIN"&&<NavLink to="/members" className={({isActive})=>`nav-item admin-nav ${isActive?"active":""}`}><span className="material-symbols-outlined">manage_accounts</span><span className="nav-label">사용자 관리</span></NavLink>}
    </nav>
    <div className="sidebar-footer">
      <div className="connection-tile"><span className="connection-name"><span className="connection-short-label">L2</span><i className={`connection-dot ${online?"":"offline"}`}/><span>L2 Collector</span></span><span className={`connection-value ${online?"":"offline"}`}>{online?"ONLINE":"OFFLINE"}</span></div>
      <div className="connection-tile"><span className="connection-name"><span className="connection-short-label">L1</span><i className={`connection-dot ${online&&collectorStatus?.connectedL1Count>0?"":"offline"}`}/><span>L1 Machines</span></span><span className={`connection-value ${online?"":"offline"}`}>{collectorStatus?.connectedL1Count||0} / {collectorStatus?.totalL1Count||6}</span></div>
    </div>
  </aside>;
}
