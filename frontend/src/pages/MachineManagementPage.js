import React, { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, StatusBadge, formatDate } from "../components/MesComponents";

const tabs = [
  ["equipment", "설비 현황", "precision_manufacturing"],
  ["alarms", "알람 관리", "warning"],
  ["assignments", "작업자 배정", "engineering"],
  ["workers", "작업자 관리", "badge"],
];

export default function MachineManagementPage({ currentUser }) {
  const [params, setParams] = useSearchParams();
  const requestedTab = params.get("tab");
  const activeTab = tabs.some(([key]) => key === requestedTab) ? requestedTab : "equipment";
  const machines = useApiData(MesApi.getMachines, []);
  const workers = useApiData(() => MesApi.getWorkers({}), []);
  const [selectedMachineId, setSelectedMachineId] = useState(params.get("machineId") || "");
  const [history, setHistory] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(null);
  const canManageWorkers = ["ADMIN", "MANAGER"].includes(currentUser?.role);

  useEffect(() => {
    if (!selectedMachineId && machines.data?.length) setSelectedMachineId(machines.data[0].machineId);
  }, [machines.data, selectedMachineId]);

  const loadDetail = useCallback(async () => {
    if (!selectedMachineId) return;
    setDetailLoading(true); setDetailError(null);
    try {
      const [historyResponse, assignmentResponse] = await Promise.all([
        MesApi.getMachineStatusHistory(selectedMachineId), MesApi.getMachineAssignments(selectedMachineId),
      ]);
      setHistory(historyResponse.data || []); setAssignments(assignmentResponse.data || []);
    } catch (error) { setDetailError(error); } finally { setDetailLoading(false); }
  }, [selectedMachineId]);

  useEffect(() => { loadDetail(); }, [loadDetail]);
  useEffect(() => { const timer = setInterval(machines.reload, 2000); return () => clearInterval(timer); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [machines.reload]);

  const selectedMachine = (machines.data || []).find((machine) => machine.machineId === selectedMachineId);
  const selectMachine = (machineId) => {
    setSelectedMachineId(machineId);
    const next = new URLSearchParams(params); next.set("machineId", machineId); setParams(next, { replace: true });
  };
  const selectTab = (tab) => {
    const next = new URLSearchParams(params); next.set("tab", tab); if (selectedMachineId) next.set("machineId", selectedMachineId); setParams(next, { replace: true });
  };

  return <div className="mes-page stitch-dashboard machine-management-page">
    <div className="dashboard-title-row">
      <div><h1>설비 관리</h1><p>설비 상태와 이력, 알람, 담당 작업자를 한 화면에서 관리합니다.</p></div>
      <div className="live-indicator"><span /> 설비 상태 · 2초 갱신</div>
    </div>
    <nav className="management-tabs" aria-label="설비 관리 메뉴">{tabs.map(([key, label, icon]) => <button key={key} className={activeTab === key ? "active" : ""} onClick={() => selectTab(key)}><span className="material-symbols-outlined">{icon}</span>{label}</button>)}</nav>
    {machines.loading && machines.data === null ? <LoadingState /> : machines.error && machines.data === null ? <ErrorState error={machines.error} onRetry={machines.reload} /> : activeTab === "equipment" ? <EquipmentTab machines={machines.data || []} selectedMachine={selectedMachine} selectMachine={selectMachine} history={history} assignments={assignments} loading={detailLoading} error={detailError} /> : activeTab === "alarms" ? <AlarmTab machines={machines.data || []} currentUser={currentUser} /> : activeTab === "assignments" ? <AssignmentTab machines={machines.data || []} workers={workers.data || []} machineId={selectedMachineId} selectMachine={selectMachine} assignments={assignments} reload={loadDetail} canEdit={canManageWorkers} /> : <WorkerTab workers={workers} canEdit={canManageWorkers} />}
  </div>;
}

function EquipmentTab({ machines, selectedMachine, selectMachine, history, assignments, loading, error }) {
  return <div className="equipment-layout">
    <section className="dashboard-panel equipment-list-panel"><div className="dashboard-section-heading"><h2>설비 목록</h2><span>{machines.length} EQUIP</span></div><div className="equipment-list">{machines.map((machine) => <button key={machine.machineId} className={`equipment-list-card ${selectedMachine?.machineId === machine.machineId ? "selected" : ""}`} onClick={() => selectMachine(machine.machineId)}><span><b>{machine.processCode}</b><StatusBadge value={machine.status} /></span><strong>{machine.machineId}</strong><small>{machine.processName || machine.machineName}</small></button>)}</div></section>
    <section className="dashboard-panel equipment-detail-panel">{!selectedMachine ? <EmptyState message="설비를 선택하세요." /> : <>
      <div className="equipment-detail-heading"><div><span>{selectedMachine.processCode}</span><h2>{selectedMachine.machineId}</h2><p>{selectedMachine.machineName} · {selectedMachine.processName}</p></div><StatusBadge value={selectedMachine.status} /></div>
      <div className="equipment-detail-kpis"><DetailKpi label="현재 LOT" value={selectedMachine.currentLotNo || "-"}/><DetailKpi label="생산 수량" value={`${selectedMachine.processedQty || 0} / ${selectedMachine.targetQty || 0}`}/><DetailKpi label="진행률" value={`${Number(selectedMachine.progressPercent || 0).toFixed(0)}%`}/><DetailKpi label="최근 갱신" value={formatDate(selectedMachine.updatedAt)}/></div>
      {Number(selectedMachine.targetQty) > 0 && <div className="equipment-progress"><div><span>공정 진행률</span><strong>{Number(selectedMachine.progressPercent || 0).toFixed(0)}%</strong></div><div className="progress-track"><span style={{ width: `${Math.min(100, Number(selectedMachine.progressPercent) || 0)}%` }} /></div></div>}
      {loading ? <LoadingState /> : error ? <ErrorState error={error} /> : <div className="equipment-detail-grid"><div><h3>배정 작업자</h3>{!assignments.length ? <EmptyState message="배정된 작업자가 없습니다." /> : assignments.map((assignment) => <div className="assignment-chip" key={assignment.assignmentId}><span className="material-symbols-outlined">person</span><div><strong>{assignment.workerName}</strong><small>{assignment.workerNo} · {assignment.assignmentRole}</small></div></div>)}</div><div><h3>최근 상태 이력</h3>{!history.length ? <EmptyState message="상태 이력이 없습니다." /> : <div className="status-timeline">{history.slice(0, 8).map((item) => <div key={item.machineStatusHistoryId}><i/><span><StatusBadge value={item.status}/><small>{item.lotNo || "LOT 없음"}</small></span><time>{formatDate(item.recordedAt)}</time></div>)}</div>}</div></div>}
    </>}</section>
  </div>;
}

function DetailKpi({ label, value }) { return <div><span>{label}</span><strong>{value}</strong></div>; }

function AlarmTab({ machines, currentUser }) {
  const [filter, setFilter] = useState({ machineId: "", cleared: "false" });
  const alarms = useApiData(() => MesApi.getMachineAlarms({ machineId: filter.machineId, cleared: filter.cleared }), [filter.machineId, filter.cleared]);
  const canClear = ["ADMIN", "MANAGER", "OPERATOR"].includes(currentUser?.role);
  return <section className="dashboard-panel dashboard-table-panel"><div className="dashboard-section-heading"><div><h2>설비 알람 이력</h2><p>활성 알람을 확인하고 원인 조치 후 해제합니다.</p></div></div><div className="mes-filter management-filter"><Field label="설비"><select value={filter.machineId} onChange={(event) => setFilter({ ...filter, machineId: event.target.value })}><option value="">전체</option>{machines.map((machine) => <option key={machine.machineId}>{machine.machineId}</option>)}</select></Field><Field label="상태"><select value={filter.cleared} onChange={(event) => setFilter({ ...filter, cleared: event.target.value })}><option value="false">활성</option><option value="true">해제됨</option><option value="">전체</option></select></Field></div>{alarms.loading && alarms.data === null ? <LoadingState/> : alarms.error && alarms.data === null ? <ErrorState error={alarms.error} onRetry={alarms.reload}/> : !(alarms.data || []).length ? <EmptyState message="조건에 맞는 알람이 없습니다."/> : <div className="dashboard-table-scroll"><table className="dashboard-table"><thead><tr><th>설비</th><th>알람 코드</th><th>레벨</th><th>메시지</th><th>발생</th><th>해제</th><th></th></tr></thead><tbody>{alarms.data.map((alarm) => <tr key={alarm.machineAlarmHistoryId}><td className="mono">{alarm.machineId}</td><td><strong>{alarm.alarmName || alarm.alarmCode}</strong><br/><small>{alarm.alarmCode}</small></td><td><StatusBadge value={alarm.alarmLevel}/></td><td>{alarm.message || "-"}</td><td className="mono">{formatDate(alarm.occurredAt)}</td><td className="mono">{formatDate(alarm.clearedAt)}</td><td>{canClear && !alarm.clearedAt && <button className="btn small danger" onClick={async () => { await MesApi.clearMachineAlarm(alarm.machineAlarmHistoryId); alarms.reload(); }}>해제</button>}</td></tr>)}</tbody></table></div>}</section>;
}

function AssignmentTab({ machines, workers, machineId, selectMachine, assignments, reload, canEdit }) {
  const [workerId, setWorkerId] = useState(""); const [role, setRole] = useState("worker"); const [error, setError] = useState(null);
  const assign = async () => { setError(null); try { if (role === "responsible") await MesApi.assignResponsible(machineId, workerId); else await MesApi.addMachineWorker(machineId, workerId); setWorkerId(""); await reload(); } catch (caught) { setError(caught); } };
  return <section className="dashboard-panel dashboard-table-panel"><div className="dashboard-section-heading"><div><h2>설비별 작업자 배정</h2><p>책임자와 공정 작업자를 설비 단위로 관리합니다.</p></div></div><div className="mes-filter management-filter"><Field label="설비"><select value={machineId} onChange={(event) => selectMachine(event.target.value)}><option value="">설비 선택</option>{machines.map((machine) => <option key={machine.machineId} value={machine.machineId}>{machine.machineId} · {machine.processName}</option>)}</select></Field>{canEdit && <><Field label="작업자"><select value={workerId} onChange={(event) => setWorkerId(event.target.value)}><option value="">작업자 선택</option>{workers.filter((worker) => worker.status === "ACTIVE").map((worker) => <option key={worker.workerId} value={worker.workerId}>{worker.workerName} ({worker.workerNo})</option>)}</select></Field><Field label="배정 유형"><select value={role} onChange={(event) => setRole(event.target.value)}><option value="worker">작업자</option><option value="responsible">책임자</option></select></Field><button className="btn" disabled={!machineId || !workerId} onClick={assign}>배정</button></>}</div>{error && <ErrorState error={error}/>} {!machineId ? <EmptyState message="설비를 선택하세요."/> : !assignments.length ? <EmptyState message="배정된 작업자가 없습니다."/> : <div className="dashboard-table-scroll"><table className="dashboard-table"><thead><tr><th>사번/이름</th><th>부서/직급</th><th>역할</th><th>배정 시각</th><th></th></tr></thead><tbody>{assignments.map((assignment) => <tr key={assignment.assignmentId}><td><span className="mono">{assignment.workerNo}</span><br/><strong>{assignment.workerName}</strong></td><td>{assignment.department || "-"} / {assignment.position || "-"}</td><td>{assignment.assignmentRole}</td><td className="mono">{formatDate(assignment.assignedAt)}</td><td>{canEdit && <button className="btn small danger" onClick={async () => { await MesApi.removeMachineWorker(machineId, assignment.workerId); reload(); }}>해제</button>}</td></tr>)}</tbody></table></div>}</section>;
}

function WorkerTab({ workers, canEdit }) {
  const [form, setForm] = useState(null); const [error, setError] = useState(null);
  const save = async () => { setError(null); try { if (form.workerId) await MesApi.updateWorker(form.workerId, form); else await MesApi.createWorker(form); setForm(null); workers.reload(); } catch (caught) { setError(caught); } };
  return <section className="dashboard-panel dashboard-table-panel"><div className="dashboard-section-heading"><div><h2>작업자 관리</h2><p>설비에 배정할 작업자 정보를 관리합니다.</p></div>{canEdit && <button className="btn" onClick={() => setForm({ workerNo: "", workerName: "", department: "", position: "", status: "ACTIVE" })}>작업자 등록</button>}</div>{workers.loading ? <LoadingState/> : workers.error ? <ErrorState error={workers.error} onRetry={workers.reload}/> : <div className="dashboard-table-scroll"><table className="dashboard-table"><thead><tr><th>사번/이름</th><th>부서</th><th>직급</th><th>상태</th><th></th></tr></thead><tbody>{(workers.data || []).map((worker) => <tr key={worker.workerId}><td><span className="mono">{worker.workerNo}</span><br/><strong>{worker.workerName}</strong></td><td>{worker.department || "-"}</td><td>{worker.position || "-"}</td><td><StatusBadge value={worker.status}/></td><td>{canEdit && <button className="btn small secondary" onClick={() => setForm({ ...worker })}>수정</button>}</td></tr>)}</tbody></table></div>}{form && <Modal title={form.workerId ? "작업자 수정" : "작업자 등록"} onClose={() => setForm(null)} footer={<><button className="btn secondary" onClick={() => setForm(null)}>취소</button><button className="btn" onClick={save}>저장</button></>}><div className="mes-form-grid"><Field label="사번"><input value={form.workerNo} onChange={(event) => setForm({ ...form, workerNo: event.target.value })}/></Field><Field label="이름"><input value={form.workerName} onChange={(event) => setForm({ ...form, workerName: event.target.value })}/></Field><Field label="부서"><input value={form.department || ""} onChange={(event) => setForm({ ...form, department: event.target.value })}/></Field><Field label="직급"><input value={form.position || ""} onChange={(event) => setForm({ ...form, position: event.target.value })}/></Field><Field label="상태"><select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}><option>ACTIVE</option><option>INACTIVE</option></select></Field></div>{error && <ErrorState error={error}/>}</Modal>}</section>;
}
