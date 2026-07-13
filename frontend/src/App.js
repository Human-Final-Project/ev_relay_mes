import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import MesLayout from "./layouts/MesLayout"; // 경로를 components에서 layouts로 수정 완료
import DashboardPage from "./pages/DashboardPage";
import WorkOrderPage from "./pages/WorkOrderPage";
import MaterialPage from "./pages/MaterialPage";
import LoginPage from "./pages/LoginPage";
import QualityPage from "./pages/QualityPage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 1. 로그인 페이지 */}
        <Route path="/login" element={<LoginPage />} />

        {/* 2. 메인 서비스 레이아웃 그룹 */}
        <Route element={<MesLayout />}>
          {/* 기본 루트 경로('/') 접근 시 대시보드로 자동 리다이렉트 */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />

          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/order" element={<WorkOrderPage />} />
          <Route path="/material" element={<MaterialPage />} />
          <Route path="/quality" element={<QualityPage />} />
        </Route>

        {/* 3. 잘못된 경로(404) 예외 처리 */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

// ⚠️ index.js에서 정상적으로 App을 가져올 수 있도록 export 문을 확실히 추가합니다.
export default App;
