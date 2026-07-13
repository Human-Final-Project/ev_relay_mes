import React from "react";
import { NavLink } from "react-router-dom";

const Sidebar = () => {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <h1>Mini MES</h1>
        <p>EV 릴레이 생산라인</p>
      </div>
      <nav className="sidebar-nav">
        <NavLink to="/dashboard" className="nav-item">
          <span className="material-symbols-outlined">dashboard</span>
          <span>대시보드</span>
        </NavLink>
        <NavLink to="/material" className="nav-item">
          <span className="material-symbols-outlined">database</span>
          <span>자재 관리</span>
        </NavLink>
        <NavLink to="/order" className="nav-item">
          <span className="material-symbols-outlined">
            precision_manufacturing
          </span>
          <span>생산 관리</span>
        </NavLink>
        {/* ⭕ 일반 div였던 메뉴를 NavLink와 /quality 주소로 연결 완료 */}
        <NavLink to="/quality" className="nav-item">
          <span className="material-symbols-outlined">verified</span>
          <span>품질 관리</span>
        </NavLink>
        <div className="nav-item">
          <span className="material-symbols-outlined">
            settings_applications
          </span>
          <span>시스템 설정</span>
        </div>
      </nav>
      <div className="sidebar-footer">
        <div className="nav-item">
          <span className="material-symbols-outlined">help</span>
          <span>고객지원</span>
        </div>
        <div className="nav-item">
          <span className="material-symbols-outlined">terminal</span>
          <span>시스템 로그</span>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
