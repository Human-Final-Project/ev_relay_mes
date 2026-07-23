import React, { useState } from "react";
import { useSearchParams } from "react-router-dom";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge } from "../components/MesComponents";
import AlarmHistoryPage from "./AlarmHistoryPage";

const EDIT_ROLES = ["ADMIN", "MANAGER"];

export function MachineManagementPage({ currentUser }) {
  const [searchParams,setSearchParams]=useSearchParams();
  const activeTab=searchParams.get("tab")==="alarms"?"alarms":"machines";
  const machines = useApiData(MesApi.getMachines, []);
  const processes = useApiData(MesApi.getProcesses, []);
  const [form, setForm] = useState(null);
  const [error, setError] = useState(null);
  const canEdit = EDIT_ROLES.includes(currentUser?.role);
  const run = async (action, close = false) => {
    setError(null);
    try {
      await action();
      await machines.reload();
      if (close) setForm(null);
    } catch (e) { setError(e); }
  };
  const save = () => {
    const data={machineId:form.machineId,machineName:form.machineName,machineType:form.machineType,processCode:form.processCode};
    return run(
    () => form.originalId ? MesApi.updateMachine(form.originalId, data) : MesApi.createMachine(data),
    true
  )};
  return <div className="mes-page management-page">
    <PageHeader title="설비 관리" description="생산 설비의 기본정보, 담당 공정과 사용 여부를 관리합니다."
      actions={activeTab==="machines"?<><button className="btn secondary" onClick={()=>{machines.reload();processes.reload()}}>새로고침</button>{canEdit&&<button className="btn" onClick={()=>setForm({machineId:"",machineName:"",machineType:"",processCode:""})}>설비 등록</button>}</>:null}/>
    <div className="tabs">
      <button className={`tab ${activeTab==="machines"?"active":""}`} onClick={()=>setSearchParams({})}>설비 목록</button>
      <button className={`tab ${activeTab==="alarms"?"active":""}`} onClick={()=>setSearchParams({tab:"alarms"})}>알람 이력</button>
    </div>
    {activeTab==="alarms"?<AlarmHistoryPage currentUser={currentUser} embedded/>:<>
    {error&&<ErrorState error={error}/>}
    {machines.loading?<LoadingState/>:machines.error?<ErrorState error={machines.error} onRetry={machines.reload}/>:!(machines.data||[]).length?<EmptyState/>:
      <div className="ops-panel"><div className="compact-table-wrap"><table className="compact-dashboard-table management-table">
        <thead><tr><th>설비 ID</th><th>설비명</th><th>유형</th><th>담당 공정</th><th>운전 상태</th><th>사용 여부</th><th>작업</th></tr></thead>
        <tbody>{machines.data.map(row=><tr key={row.machineId}>
          <td><strong className="mono lot-link">{row.machineId}</strong></td><td>{row.machineName}</td><td>{row.machineType}</td>
          <td>{row.processName}<small>{row.processCode}</small></td><td><StatusBadge value={row.status}/></td>
          <td><StatusBadge value={row.useYn==="Y"?"ACTIVE":"INACTIVE"}/></td>
          <td>{canEdit&&<div className="mes-actions"><button className="btn small secondary" onClick={()=>setForm({...row,originalId:row.machineId})}>수정</button><button className="btn small secondary" disabled={row.status==="RUNNING"} onClick={()=>run(()=>MesApi.setMachineActive(row.machineId,row.useYn!=="Y"))}>{row.useYn==="Y"?"사용안함":"사용"}</button></div>}</td>
        </tr>)}</tbody>
      </table></div></div>}
    {form&&<Modal title={form.originalId?"설비 수정":"설비 등록"} onClose={()=>setForm(null)}
      footer={<><button className="btn secondary" onClick={()=>setForm(null)}>취소</button><button className="btn" disabled={!form.machineId||!form.machineName||!form.machineType||!form.processCode} onClick={save}>저장</button></>}>
      <div className="mes-form-grid">
        <Field label="설비 ID"><input value={form.machineId} disabled={!!form.originalId} onChange={e=>setForm({...form,machineId:e.target.value})}/></Field>
        <Field label="설비명"><input value={form.machineName} onChange={e=>setForm({...form,machineName:e.target.value})}/></Field>
        <Field label="설비 유형"><input value={form.machineType} placeholder="EQ-WIND" onChange={e=>setForm({...form,machineType:e.target.value})}/></Field>
        <Field label="담당 공정"><select value={form.processCode} onChange={e=>setForm({...form,processCode:e.target.value})}><option value="">공정 선택</option>{(processes.data||[]).filter(p=>p.useYn==="Y"||p.processCode===form.processCode).map(p=><option key={p.processCode} value={p.processCode}>{p.processCode} · {p.processName}</option>)}</select></Field>
      </div>{error&&<ErrorState error={error}/>}
    </Modal>}
    </>}
  </div>;
}

export function ProcessManagementPage({ currentUser }) {
  const result = useApiData(MesApi.getProcesses, []);
  const [form, setForm] = useState(null);
  const [error, setError] = useState(null);
  const canEdit = EDIT_ROLES.includes(currentUser?.role);
  const run = async (action, close = false) => {
    setError(null);
    try { await action(); await result.reload(); if(close)setForm(null); }
    catch(e){ setError(e); }
  };
  const save = () => {
    const data={processCode:form.processCode,processName:form.processName,processOrder:Number(form.processOrder),description:form.description||""};
    return run(
    () => form.originalCode ? MesApi.updateProcess(form.originalCode, data) : MesApi.createProcess(data),
    true
  )};
  return <div className="mes-page management-page">
    <PageHeader title="공정 관리" description="공정 순서와 설명, 생산 사용 여부를 관리합니다."
      actions={<><button className="btn secondary" onClick={result.reload}>새로고침</button>{canEdit&&<button className="btn" onClick={()=>setForm({processCode:"",processName:"",processOrder:"",description:""})}>공정 등록</button>}</>}/>
    {error&&<ErrorState error={error}/>}
    {result.loading?<LoadingState/>:result.error?<ErrorState error={result.error} onRetry={result.reload}/>:<div className="ops-panel"><div className="compact-table-wrap"><table className="compact-dashboard-table management-table">
      <thead><tr><th>순서</th><th>공정 코드</th><th>공정명</th><th>설명</th><th>사용 여부</th><th>작업</th></tr></thead>
      <tbody>{(result.data||[]).map(row=><tr key={row.processCode}><td>{row.processOrder}</td><td><strong className="mono lot-link">{row.processCode}</strong></td><td>{row.processName}</td><td>{row.description||"-"}</td><td><StatusBadge value={row.useYn==="Y"?"ACTIVE":"INACTIVE"}/></td><td>{canEdit&&<div className="mes-actions"><button className="btn small secondary" onClick={()=>setForm({...row,originalCode:row.processCode})}>수정</button><button className="btn small secondary" onClick={()=>run(()=>MesApi.setProcessActive(row.processCode,row.useYn!=="Y"))}>{row.useYn==="Y"?"사용안함":"사용"}</button></div>}</td></tr>)}</tbody>
    </table></div></div>}
    {form&&<Modal title={form.originalCode?"공정 수정":"공정 등록"} onClose={()=>setForm(null)}
      footer={<><button className="btn secondary" onClick={()=>setForm(null)}>취소</button><button className="btn" disabled={!form.processCode||!form.processName||Number(form.processOrder)<=0} onClick={save}>저장</button></>}>
      <div className="mes-form-grid"><Field label="공정 코드"><input value={form.processCode} disabled={!!form.originalCode} onChange={e=>setForm({...form,processCode:e.target.value})}/></Field><Field label="공정명"><input value={form.processName} onChange={e=>setForm({...form,processName:e.target.value})}/></Field><Field label="공정 순서"><input type="number" min="1" value={form.processOrder} onChange={e=>setForm({...form,processOrder:e.target.value})}/></Field><Field label="설명"><input value={form.description||""} onChange={e=>setForm({...form,description:e.target.value})}/></Field></div>{error&&<ErrorState error={error}/>}
    </Modal>}
  </div>;
}
