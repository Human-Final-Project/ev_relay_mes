import React, { useState } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import MesLayout from "./layouts/MesLayout"; 
import DashboardPage from "./pages/DashboardPage";
import WorkOrderPage from "./pages/WorkOrderPage";
import MaterialPage from "./pages/MaterialPage";
import LoginPage from "./pages/LoginPage";
import QualityPage from "./pages/QualityPage";
import AdminEmployeePage from "./pages/AdminEmployeePage";
import MyPage from "./pages/MyPage"; 

function App() {
  // 로컬스토리지 초기값을 기준으로 React 상태(State)를 생성합니다.
  const [isLoggedIn, setIsLoggedIn] = useState(
    localStorage.getItem("isLoggedIn") === "true"
  );
  const [userRole, setUserRole] = useState(
    localStorage.getItem("userRole") || ""
  );

  // 상태를 변경해줄 로그인 처리 함수
  const handleLoginSuccess = (role) => {
    localStorage.setItem("isLoggedIn", "true");
    localStorage.setItem("userRole", role);
    setIsLoggedIn(true);
    setUserRole(role);
  };

  // 로그아웃 처리 함수 (나중에 사용)
  const handleLogout = () => {
    localStorage.clear();
    setIsLoggedIn(false);
    setUserRole("");
  };

  return (
    <BrowserRouter>
      <Routes>
        {/* 1. 첫 화면 ('/') 접근 시 권한 분기 */}
        <Route 
          path="/" 
          element={
            !isLoggedIn ? (
              <Navigate to="/login" replace />
            ) : userRole === "admin" ? (
              <Navigate to="/admin/employees" replace />
            ) : (
              <Navigate to="/dashboard" replace />
            )
          } 
        />

        {/* 2. 로그인 페이지 */}
        <Route 
          path="/login" 
          element={<LoginPage onLoginSuccess={handleLoginSuccess} />} 
        />

        {/* 3. 메인 서비스 레이아웃 그룹 (인증 보호) */}
        <Route 
          element={
            isLoggedIn ? <MesLayout /> : <Navigate to="/login" replace />
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/order" element={<WorkOrderPage />} />
          <Route path="/material" element={<MaterialPage />} />
          <Route path="/quality" element={<QualityPage />} />
          
          {/* 💡 깔끔하게 인증 보호 레이아웃 내부로 합쳐진 마이페이지 */}
          <Route path="/mypage" element={<MyPage />} />

          {/* 관리자 전용 사원관리 페이지 */}
          <Route 
            path="/admin/employees" 
            element={
              userRole === "admin" ? (
                <AdminEmployeePage />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            } 
          />
        </Route>

        {/* 4. 잘못된 경로(404) 예외 처리 */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;