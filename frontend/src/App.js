import React, { useState } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import MesLayout from "./layouts/MesLayout"; 
import DashboardPage from "./pages/DashboardPage";
import ProductionPage from "./pages/ProductionPage"; // 1. 정상적으로 교체 완료!
import MaterialPage from "./pages/MaterialPage";
import LoginPage from "./pages/LoginPage";
import QualityPage from "./pages/QualityPage";
import QualityInspectionPage from "./pages/QualityInspectionPage";
import ProductionEntryPage from "./pages/ProductionEntryPage";
import EquipmentIssuePage from "./pages/EquipmentIssuePage";
import AdminEquipmentIssuePage from "./pages/AdminEquipmentIssuePage";
import MyAttendancePage from "./pages/MyAttendancePage";
import AdminAttendancePage from "./pages/AdminAttendancePage";
import TrainingRecordPage from "./pages/TrainingRecordPage";
import AdminEmployeePage from "./pages/AdminEmployeePage";
import AdminNoticeEditor from "./pages/AdminNoticeEditor";
import NoticeBoardPage from "./pages/NoticeBoardPage";
import MyPage from "./pages/MyPage";
import AdminProfilePage from "./pages/AdminProfilePage";
import MasterDataPage from "./pages/MasterDataPage";
import WorkOrderPage from "./pages/WorkOrderPage";
import EmployeeDashboardPage from "./pages/EmployeeDashboardPage";

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

  // 로그아웃 처리 함수
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
          <Route
            path="/dashboard"
            element={userRole === "admin" ? <DashboardPage /> : <EmployeeDashboardPage />}
          />

          {/* 💡 기준정보 관리 (제품/BOM) */}
          <Route path="/master-data" element={<MasterDataPage />} />
          
          {/* 2. [수정 포인트] 경로를 /production으로 변경하고, 기존 WorkOrderPage를 ProductionPage로 교체했습니다. */}
          <Route path="/production" element={<ProductionPage />} />
          
          <Route path="/material" element={<MaterialPage />} />

          {/* 💡 생산 실적 입력 (관리자/사원 공통) */}
          <Route path="/production-entry" element={<ProductionEntryPage />} />

          {/* 💡 설비 이상 신고 (관리자/사원 공통) */}
          <Route path="/equipment-issue" element={<EquipmentIssuePage />} />

          {/* 💡 설비 이상 신고 관리 (관리자 전용) */}
          <Route 
            path="/admin/equipment-issues" 
            element={
              userRole === "admin" ? (
                <AdminEquipmentIssuePage />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            } 
          />

          {/* 💡 작업지시 (관리자 전용) */}
          <Route 
            path="/work-order" 
            element={
              userRole === "admin" ? (
                <WorkOrderPage />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            } 
          />

          <Route path="/quality" element={<QualityPage />} />

          {/* 💡 품질 검사 입력 (관리자/사원 공통) */}
          <Route path="/quality-inspection" element={<QualityInspectionPage />} />
          
          {/* 💡 마이페이지: 관리자/사원 역할에 따라 다른 컴포넌트 렌더링 */}
          <Route
            path="/mypage"
            element={userRole === "admin" ? <AdminProfilePage /> : <MyPage />}
          />

          {/* 💡 내 근태 조회 (관리자/사원 공통) */}
          <Route path="/my-attendance" element={<MyAttendancePage />} />

          {/* 💡 전체 사원 근태 조회 (관리자 전용) */}
          <Route 
            path="/admin/attendance" 
            element={
              userRole === "admin" ? (
                <AdminAttendancePage />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            } 
          />

          {/* 💡 교육/자격 이력 (관리자/사원 공통) */}
          <Route path="/training-record" element={<TrainingRecordPage />} />

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

          {/* 💡 관리자 전용 공지 작성 페이지 */}
          <Route 
            path="/admin/notices" 
            element={
              userRole === "admin" ? (
                <AdminNoticeEditor />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            } 
          />

          {/* 💡 공지사항 전체보기 (관리자/사원 공통) */}
          <Route path="/notices" element={<NoticeBoardPage />} />
        </Route>

        {/* 4. 잘못된 경로(404) 예외 처리 */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;