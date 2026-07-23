import React, { useEffect, useState } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AuthApi from "./api/AuthApi";
import MesLayout from "./layouts/MesLayout";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import WorkOrderPage from "./pages/WorkOrderPage";
import LotPage from "./pages/LotPage";
import ProductionPage from "./pages/ProductionPage";
import QualityPage from "./pages/QualityPage";
import MaterialPage from "./pages/MaterialPage";
import MasterDataPage from "./pages/MasterDataPage";
import WorkerAssignmentPage from "./pages/WorkerAssignmentPage";
import AdminEmployeePage from "./pages/AdminEmployeePage";
import AccountPage from "./pages/AccountPage";
import { MachineManagementPage, ProcessManagementPage } from "./pages/MachineProcessManagementPage";
import ProductionHistoryPage from "./pages/ProductionHistoryPage";
import { MaterialStockPage, MaterialTransactionPage, ProductStockPage, ProductTransactionPage } from "./pages/InventoryQueryPages";

function App() {
  const [currentUser,setCurrentUser]=useState(null); const [checking,setChecking]=useState(true);
  useEffect(()=>{let active=true;AuthApi.getCurrentUser().then(r=>active&&setCurrentUser(r.data)).catch(()=>active&&setCurrentUser(null)).finally(()=>active&&setChecking(false));return()=>{active=false}},[]);
  const logout=async()=>{try{await AuthApi.logout()}catch(e){if(e.response?.status!==401)console.error(e)}finally{setCurrentUser(null)}};
  if(checking)return <div role="status" className="mes-state">로그인 상태를 확인하고 있습니다.</div>;
  return <BrowserRouter><Routes>
    <Route path="/login" element={currentUser?<Navigate to="/dashboard" replace/>:<LoginPage onLoginSuccess={setCurrentUser}/>}/>
    <Route element={currentUser?<MesLayout currentUser={currentUser} onLogout={logout}/>:<Navigate to="/login" replace/>}>
      <Route path="/dashboard" element={<DashboardPage/>}/>
      <Route path="/work-orders" element={<Navigate to="/production/work-orders" replace/>}/>
      <Route path="/lots" element={<Navigate to="/production/lots" replace/>}/>
      <Route path="/production" element={<ProductionPage currentUser={currentUser}/>}/>
      <Route path="/alarms" element={<Navigate to="/master/machines?tab=alarms" replace/>}/>
      <Route path="/quality" element={<QualityPage currentUser={currentUser}/>}/>
      <Route path="/materials" element={<Navigate to="/inventory/material-lots" replace/>}/>
      <Route path="/master-data" element={<MasterDataPage currentUser={currentUser}/>}/>
      <Route path="/workers" element={<Navigate to="/master/workers" replace/>}/>
      <Route path="/members" element={currentUser?.role==="ADMIN"?<AdminEmployeePage/>:<Navigate to="/dashboard" replace/>}/>
      <Route path="/account" element={<AccountPage currentUser={currentUser} onLoggedOut={()=>setCurrentUser(null)}/>}/>
      <Route path="/master/machines" element={<MachineManagementPage currentUser={currentUser}/>}/>
      <Route path="/master/processes" element={<ProcessManagementPage currentUser={currentUser}/>}/>
      <Route path="/master/products" element={<MasterDataPage currentUser={currentUser} initialTab="items" singleTab itemTypes={["SA","FG"]} pageTitle="제품 관리"/>}/>
      <Route path="/master/materials" element={<MasterDataPage currentUser={currentUser} initialTab="items" singleTab itemTypes={["RM"]} pageTitle="자재 관리"/>}/>
      <Route path="/master/boms" element={<MasterDataPage currentUser={currentUser} initialTab="boms" singleTab pageTitle="BOM 관리"/>}/>
      <Route path="/master/workers" element={<WorkerAssignmentPage currentUser={currentUser}/>}/>
      <Route path="/production/work-orders" element={<WorkOrderPage currentUser={currentUser}/>}/>
      <Route path="/production/history" element={<ProductionHistoryPage/>}/>
      <Route path="/production/lots" element={<LotPage currentUser={currentUser}/>}/>
      <Route path="/quality/inspections" element={<QualityPage currentUser={currentUser} initialTab="inspections" singleTab/>}/>
      <Route path="/quality/defects" element={<QualityPage currentUser={currentUser} initialTab="defects" singleTab/>}/>
      <Route path="/inventory/material-lots" element={<MaterialPage currentUser={currentUser}/>}/>
      <Route path="/inventory/material-stock" element={<MaterialStockPage/>}/>
      <Route path="/inventory/material-transactions" element={<MaterialTransactionPage/>}/>
      <Route path="/inventory/product-stock" element={<ProductStockPage/>}/>
      <Route path="/inventory/product-transactions" element={<ProductTransactionPage/>}/>
      <Route path="/reports/production" element={<ProductionHistoryPage report/>}/>
      <Route path="/reports/traceability" element={<LotPage currentUser={currentUser}/>}/>
    </Route>
    <Route path="/" element={<Navigate to={currentUser?"/dashboard":"/login"} replace/>}/>
    <Route path="*" element={<Navigate to="/" replace/>}/>
  </Routes></BrowserRouter>;
}

export default App;
