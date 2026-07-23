import React, { useEffect, useState } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AuthApi from "./api/AuthApi";
import MesLayout from "./layouts/MesLayout";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import WorkOrderPage from "./pages/WorkOrderPage";
import LotPage from "./pages/LotPage";
import ProductionPage from "./pages/ProductionPage";
import AlarmHistoryPage from "./pages/AlarmHistoryPage";
import QualityPage from "./pages/QualityPage";
import MaterialPage from "./pages/MaterialPage";
import MasterDataPage from "./pages/MasterDataPage";
import WorkerAssignmentPage from "./pages/WorkerAssignmentPage";
import AdminEmployeePage from "./pages/AdminEmployeePage";
import AccountPage from "./pages/AccountPage";

function App() {
  const [currentUser,setCurrentUser]=useState(null); const [checking,setChecking]=useState(true);
  useEffect(()=>{let active=true;AuthApi.getCurrentUser().then(r=>active&&setCurrentUser(r.data)).catch(()=>active&&setCurrentUser(null)).finally(()=>active&&setChecking(false));return()=>{active=false}},[]);
  const logout=async()=>{try{await AuthApi.logout()}catch(e){if(e.response?.status!==401)console.error(e)}finally{setCurrentUser(null)}};
  if(checking)return <div role="status" className="mes-state">로그인 상태를 확인하고 있습니다.</div>;
  return <BrowserRouter><Routes>
    <Route path="/login" element={currentUser?<Navigate to="/dashboard" replace/>:<LoginPage onLoginSuccess={setCurrentUser}/>}/>
    <Route element={currentUser?<MesLayout currentUser={currentUser} onLogout={logout}/>:<Navigate to="/login" replace/>}>
      <Route path="/dashboard" element={<DashboardPage/>}/>
      <Route path="/work-orders" element={<WorkOrderPage currentUser={currentUser}/>}/>
      <Route path="/lots" element={<LotPage currentUser={currentUser}/>}/>
      <Route path="/production" element={<ProductionPage currentUser={currentUser}/>}/>
      <Route path="/alarms" element={<AlarmHistoryPage currentUser={currentUser}/>}/>
      <Route path="/quality" element={<QualityPage currentUser={currentUser}/>}/>
      <Route path="/materials" element={<MaterialPage currentUser={currentUser}/>}/>
      <Route path="/master-data" element={<MasterDataPage currentUser={currentUser}/>}/>
      <Route path="/workers" element={<WorkerAssignmentPage currentUser={currentUser}/>}/>
      <Route path="/members" element={currentUser?.role==="ADMIN"?<AdminEmployeePage/>:<Navigate to="/dashboard" replace/>}/>
      <Route path="/account" element={<AccountPage currentUser={currentUser} onLoggedOut={()=>setCurrentUser(null)}/>}/>
    </Route>
    <Route path="/" element={<Navigate to={currentUser?"/dashboard":"/login"} replace/>}/>
    <Route path="*" element={<Navigate to="/" replace/>}/>
  </Routes></BrowserRouter>;
}

export default App;
