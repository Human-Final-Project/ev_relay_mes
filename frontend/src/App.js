import React, { useEffect, useState } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import AuthApi from "./api/AuthApi";
import MesLayout from "./layouts/MesLayout";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import WorkOrderPage from "./pages/WorkOrderPage";
import LotPage from "./pages/LotPage";
import ProductionPage from "./pages/ProductionPage";
import ProductionResultPage from "./pages/ProductionResultPage";
import AlarmHistoryPage from "./pages/AlarmHistoryPage";
import QualityPage from "./pages/QualityPage";
import MaterialPage from "./pages/MaterialPage";
import MasterDataPage from "./pages/MasterDataPage";
import WorkerAssignmentPage from "./pages/WorkerAssignmentPage";
import AdminEmployeePage from "./pages/AdminEmployeePage";
import AccountPage from "./pages/AccountPage";
import NoticePage from "./pages/NoticePage";
import NotificationPage from "./pages/NotificationPage";

function App() {
  const [currentUser,setCurrentUser]=useState(null); const [checking,setChecking]=useState(true);
  const canManageWorkers = ["ADMIN", "MANAGER"].includes(currentUser?.role);
  useEffect(()=>{let active=true;AuthApi.getCurrentUser().then(r=>active&&setCurrentUser(r.data)).catch(()=>active&&setCurrentUser(null)).finally(()=>active&&setChecking(false));return()=>{active=false}},[]);
  const logout=async()=>{try{await AuthApi.logout()}catch(e){if(e.response?.status!==401)console.error(e)}finally{setCurrentUser(null)}};
  if(checking)return <div role="status" className="mes-state">로그인 상태를 확인하고 있습니다.</div>;
  return <BrowserRouter><Routes>
    <Route path="/login" element={<LoginRoute currentUser={currentUser} onLoginSuccess={setCurrentUser}/>}/>
    <Route element={<ProtectedLayout currentUser={currentUser} onLogout={logout}/>}>
      <Route path="/dashboard" element={<DashboardPage currentUser={currentUser}/>}/>
      <Route path="/work-orders" element={<WorkOrderPage currentUser={currentUser}/>}/>
      <Route path="/lots" element={<LotPage currentUser={currentUser}/>}/>
      <Route path="/production" element={<ProductionPage currentUser={currentUser}/>}/>
      <Route path="/production-results" element={<ProductionResultPage/>}/>
      <Route path="/alarms" element={<AlarmHistoryPage currentUser={currentUser}/>}/>
      <Route path="/quality" element={<QualityPage currentUser={currentUser}/>}/>
      <Route path="/materials" element={<MaterialPage currentUser={currentUser}/>}/>
      <Route path="/master-data" element={<MasterDataPage currentUser={currentUser}/>}/>
      <Route path="/workers" element={canManageWorkers?<WorkerAssignmentPage currentUser={currentUser}/>:<Navigate to="/dashboard" replace/>}/>
      <Route path="/members" element={currentUser?.role==="ADMIN"?<AdminEmployeePage/>:<Navigate to="/dashboard" replace/>}/>
      <Route path="/account" element={<AccountPage currentUser={currentUser} onLoggedOut={()=>setCurrentUser(null)}/>}/>
      <Route path="/notices" element={<NoticePage currentUser={currentUser}/>}/>
      <Route path="/notifications" element={<NotificationPage/>}/>
    </Route>
    <Route path="/" element={<Navigate to={currentUser?"/dashboard":"/login"} replace/>}/>
    <Route path="*" element={<Navigate to="/" replace/>}/>
  </Routes></BrowserRouter>;
}

function safeLocalPath(value){
  return typeof value==="string"&&value.startsWith("/")&&!value.startsWith("//")?value:"/dashboard";
}

function LoginRoute({currentUser,onLoginSuccess}){
  const location=useLocation();
  const destination=safeLocalPath(location.state?.from);
  return currentUser?<Navigate to={destination} replace/>:<LoginPage onLoginSuccess={onLoginSuccess} redirectTo={destination}/>;
}

function ProtectedLayout({currentUser,onLogout}){
  const location=useLocation();
  if(currentUser)return <MesLayout currentUser={currentUser} onLogout={onLogout}/>;
  return <Navigate to="/login" replace state={{from:`${location.pathname}${location.search}`}}/>;
}

export default App;
