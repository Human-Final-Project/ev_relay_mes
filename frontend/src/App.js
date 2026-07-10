import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import MesLayout from "./layouts/MesLayout";
import DashboardPage from "./pages/DashboardPage";
import WorkOrderPage from "./pages/WorkOrderPage";
import MaterialPage from "./pages/MaterialPage";
import LoginPage from "./pages/LoginPage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 로그인 */}
        <Route path="/" element={<LoginPage />} />

        {/* 로그인 후 사용하는 화면 */}
        <Route element={<MesLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />

          <Route path="/order" element={<WorkOrderPage />} />

          <Route path="/material" element={<MaterialPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
