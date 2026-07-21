import React, { useState, useEffect, useCallback } from "react";
import MesApi from "../api/MesApi";

// -------------------------------------------------------------
// [상수 정의] - 백엔드 Machine.Status / MachineAlarm.alarmLevel 그대로 사용
// -------------------------------------------------------------
const STATUS_LABEL = { IDLE: "대기", RUNNING: "가동중", ERROR: "이상", STOPPED: "정지" };
const STATUS_TAG_CLASS = { IDLE: "tag-idle", RUNNING: "tag-running", ERROR: "tag-error", STOPPED: "tag-stopped" };
const STATUS_OPTIONS = ["IDLE", "RUNNING", "ERROR", "STOPPED"];
const ROLE_LABEL = { RESPONSIBLE: "책임자", WORKER: "작업자" };
const ALARM_LEVEL_CLASS = { INFO: "alarm-blue", WARN: "alarm-yellow", ERROR: "alarm-red" };

// -------------------------------------------------------------
// [CSS 스타일 정의]
// -------------------------------------------------------------
const styles = `
  .monitor-container { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; background-color: #f8fafc; padding: 24px; min-height: 100vh; box-sizing: border-box; }
  .monitor-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 24px; border-bottom: 1px solid #e2e8f0; padding-bottom: 20px; }
  .title-area h2 { margin: 0 0 6px 0; font-size: 24px; font-weight: 800; color: #0f172a; }
  .title-area p { margin: 0; font-size: 14px; color: #64748b; }

  .metrics-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 24px; }
  .metric-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.02); }
  .metric-card .card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  .metric-card .label { font-size: 13px; font-weight: 700; color: #64748b; }
  .metric-card .icon-badge { font-size: 16px; width: 28px; height: 28px; background: #f1f5f9; border-radius: 6px; display: flex; align-items: center; justify-content: center; }
  .metric-card .value { font-size: 26px; font-weight: 800; color: #0f172a; margin-bottom: 6px; }
  .metric-card .value span { font-size: 13px; font-weight: 500; color: #64748b; }
  .sub-label { font-size: 12px; color: #94a3b8; font-weight: 600; }

  .status-section { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .section-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
  .section-title-row h3 { margin: 0; font-size: 16px; font-weight: 700; }
  .lines-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
  @media (max-width: 1100px) { .lines-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  .line-status-card { border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; background: #ffffff; transition: box-shadow 0.2s; cursor: pointer; }
  .line-status-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
  .line-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
  .line-name { font-size: 15px; font-weight: 800; }
  .line-name span { font-size: 11px; color: #64748b; display: block; font-weight: 600; margin-top: 2px; }
  .status-tag { font-size: 11px; font-weight: 700; padding: 3px 8px; border-radius: 12px; }
  .tag-running { background-color: #dcfce7; color: #16803d; }
  .tag-idle { background-color: #f1f5f9; color: #64748b; }
  .tag-error { background-color: #fee2e2; color: #b91c1c; }
  .tag-stopped { background-color: #fef3c7; color: #92400e; }

  .operator-info { margin-top: 14px; padding-top: 10px; border-top: 1px dashed #e2e8f0; display: flex; align-items: center; gap: 8px; font-size: 12px; }
  .operator-avatar { width: 24px; height: 24px; border-radius: 50%; background: #e2e8f0; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 11px; flex-shrink: 0; }

  .side-alarm-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin-top: 24px; }
  .alarm-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; border-bottom: 1px solid #f1f5f9; padding-bottom: 12px; }
  .alarm-badge { background: #fee2e2; color: #dc2626; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 12px; }
  .alarm-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
  @media (max-width: 900px) { .alarm-list { grid-template-columns: 1fr; } }
  .alarm-item { padding: 14px; border-radius: 8px; border-left: 4px solid transparent; }
  .alarm-red { background: #fff5f5; border-left-color: #ef4444; }
  .alarm-yellow { background: #fffbeb; border-left-color: #f59e0b; }
  .alarm-blue { background: #eff6ff; border-left-color: #3b82f6; }
  .alarm-item-title { font-size: 13px; font-weight: 700; }
  .alarm-item-desc { font-size: 12px; color: #64748b; margin-top: 4px; }
  .alarm-item-time { font-size: 11px; color: #94a3b8; margin-top: 6px; display: flex; justify-content: space-between; align-items: center; }
  .empty-panel { text-align: center; color: #94a3b8; font-size: 13px; padding: 30px 10px; }

  .detail-view { animation: fadeIn 0.3s ease-in-out; }
  @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

  .detail-top-nav { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; font-size: 13px; flex-wrap: wrap; }
  .back-link { font-weight: 700; color: #475569; cursor: pointer; background: #e2e8f0; padding: 6px 12px; border-radius: 6px; transition: background 0.2s; }
  .back-link:hover { background: #cbd5e1; }
  .nav-path { color: #94a3b8; font-weight: 600; }
  .active-path { color: #0566d9; font-weight: 700; }

  .detail-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
  .detail-title-area h2 { margin: 0; font-size: 26px; font-weight: 800; color: #0f172a; }
  .detail-title-area p { margin: 4px 0 0 0; font-size: 14px; color: #64748b; }
  .status-select { border: 1px solid #cbd5e1; border-radius: 8px; padding: 10px 14px; font-size: 13px; font-weight: 700; background: #ffffff; cursor: pointer; outline: none; }

  .detail-split-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 24px; margin-bottom: 24px; }
  @media (max-width: 992px) { .detail-split-grid { grid-template-columns: 1fr; } }

  .table-container { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin-bottom: 24px; }
  .table-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
  .table-header-row h3 { margin: 0; font-size: 16px; }
  .monitoring-table { width: 100%; border-collapse: collapse; }
  .monitoring-table th { background-color: #f1f5f9; color: #475569; font-size: 12px; font-weight: 700; padding: 12px 16px; border-bottom: 1px solid #e2e8f0; text-align: left; }
  .monitoring-table td { padding: 14px 16px; border-bottom: 1px solid #e2e8f0; font-size: 13px; }

  .operator-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .operator-card h3 { margin: 0 0 16px 0; font-size: 15px; }
  .operator-list { display: flex; flex-direction: column; gap: 12px; }
  .op-item { display: flex; align-items: center; gap: 12px; padding: 10px; border: 1px solid #f1f5f9; border-radius: 8px; background: #f8fafc; }
  .op-name { font-size: 13px; font-weight: 700; color: #0f172a; }
  .op-role { font-size: 11px; color: #64748b; }

  .btn-add-operator { margin-top: 10px; background: transparent; border: 1px dashed #cbd5e1; padding: 10px; border-radius: 8px; font-size: 12px; font-weight: 700; color: #64748b; cursor: pointer; transition: background 0.2s; width: 100%; }
  .btn-add-operator:hover { background: #f1f5f9; border-color: #94a3b8; }

  .status-badge { font-size: 11px; font-weight: 700; padding: 4px 8px; border-radius: 4px; }
  .badge-ok { background: #dcfce7; color: #15803d; }
  .badge-ng { background: #fee2e2; color: #b91c1c; }
  .btn-text-link { background: transparent; border: none; color: #0566d9; font-size: 12px; font-weight: 700; cursor: pointer; }
  .btn-text-link:hover { text-decoration: underline; }

  .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
  .modal-content { background: #ffffff; width: 440px; max-width: 90vw; border-radius: 12px; padding: 24px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); max-height: 85vh; overflow-y: auto; }
  .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
  .modal-header h3 { margin: 0; font-size: 18px; }
  .btn-close { background: none; border: none; font-size: 18px; cursor: pointer; color: #64748b; }
  .modal-employee-list { max-height: 350px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
  .employee-item { display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; }
  .emp-name { font-size: 14px; font-weight: 700; color: #0f172a; }
  .emp-role { font-size: 12px; color: #64748b; margin-top: 2px; }

  .btn-assign { background: #0566d9; color: white; border: none; padding: 6px 10px; border-radius: 6px; font-size: 11.5px; font-weight: 700; cursor: pointer; }
  .btn-assign:hover { opacity: 0.85; }
  .assign-actions { display: flex; gap: 6px; align-items: center; }
  .btn-remove { background: #fee2e2; color: #dc2626; border: none; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 700; cursor: pointer; }
  .btn-remove:hover { background: #fca5a5; }
`;

function ProductionPage() {
  const isAdmin = localStorage.getItem("userRole") === "admin";

  const [view, setView] = useState("main");
  const [machines, setMachines] = useState([]);
  const [unresolvedAlarms, setUnresolvedAlarms] = useState([]);
  const [runningLots, setRunningLots] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

  const [selectedMachineId, setSelectedMachineId] = useState(null);
  const [assignments, setAssignments] = useState([]);
  const [machineAlarms, setMachineAlarms] = useState([]);
  const [statusHistory, setStatusHistory] = useState([]);
  const [productionLogs, setProductionLogs] = useState([]);

  const [showAssignModal, setShowAssignModal] = useState(false);
  const [workers, setWorkers] = useState([]);

  const fetchOverview = useCallback(async () => {
    setIsLoading(true);
    try {
      const [machinesRes, alarmsRes, lotsRes] = await Promise.all([
        MesApi.getMachines(),
        MesApi.getMachineAlarms({ cleared: false }),
        MesApi.getLots("RUNNING"),
      ]);
      setMachines(machinesRes.data || []);
      setUnresolvedAlarms(alarmsRes.data || []);
      setRunningLots(lotsRes.data || []);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "생산 현황을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchOverview();
  }, [fetchOverview]);

  const selectedMachine = machines.find((m) => m.machineId === selectedMachineId) || null;

  const fetchMachineDetail = useCallback(async (machineId) => {
    if (!machineId) return;
    try {
      const [assignRes, alarmRes, historyRes, logsRes] = await Promise.all([
        MesApi.getMachineAssignments(machineId),
        MesApi.getMachineAlarms({ machineId }),
        MesApi.getMachineStatusHistory(machineId),
        MesApi.getProductionLogs({ machineId }),
      ]);
      setAssignments(assignRes.data || []);
      setMachineAlarms(alarmRes.data || []);
      setStatusHistory((historyRes.data || []).slice(0, 10));
      setProductionLogs((logsRes.data || []).slice(0, 10));
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "설비 상세 정보를 불러오지 못했습니다.");
    }
  }, []);

  const goToDetail = (machineId) => {
    setSelectedMachineId(machineId);
    setView("detail");
    fetchMachineDetail(machineId);
  };

  const handleStatusChange = async (newStatus) => {
    if (!selectedMachine) return;
    try {
      await MesApi.updateMachineStatus({ machineId: selectedMachine.machineId, status: newStatus });
      await Promise.all([fetchOverview(), fetchMachineDetail(selectedMachine.machineId)]);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "설비 상태 변경에 실패했습니다.");
    }
  };

  const handleClearAlarm = async (alarmId) => {
    try {
      await MesApi.clearMachineAlarm(alarmId);
      await Promise.all([fetchOverview(), selectedMachine && fetchMachineDetail(selectedMachine.machineId)]);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "알람 해제에 실패했습니다.");
    }
  };

  const openAssignModal = async () => {
    try {
      const res = await MesApi.getWorkers("ACTIVE");
      setWorkers(res.data || []);
      setShowAssignModal(true);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "작업자 목록을 불러오지 못했습니다.");
    }
  };

  const handleAssignResponsible = async (workerId) => {
    if (!selectedMachine) return;
    try {
      await MesApi.assignMachineResponsible(selectedMachine.machineId, workerId);
      await fetchMachineDetail(selectedMachine.machineId);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "책임자 지정에 실패했습니다.");
    }
  };

  const handleAddWorker = async (workerId) => {
    if (!selectedMachine) return;
    try {
      await MesApi.addMachineWorker(selectedMachine.machineId, workerId);
      await fetchMachineDetail(selectedMachine.machineId);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "작업자 추가에 실패했습니다.");
    }
  };

  const handleRemoveAssignment = async (workerId, workerName) => {
    if (!selectedMachine) return;
    if (!window.confirm(`${workerName}님을 이 설비에서 배정 해제하시겠습니까?`)) return;
    try {
      await MesApi.removeMachineAssignment(selectedMachine.machineId, workerId);
      await fetchMachineDetail(selectedMachine.machineId);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "배정 해제에 실패했습니다.");
    }
  };

  const runningCount = machines.filter((m) => m.status === "RUNNING").length;
  const errorCount = machines.filter((m) => m.status === "ERROR" || m.status === "STOPPED").length;
  const assignedWorkerIds = new Set(assignments.map((a) => a.workerId));

  return (
    <div className="monitor-container">
      <style>{styles}</style>

      {view === "main" ? (
        <div>
          <div className="monitor-header">
            <div className="title-area">
              <h2>생산 모니터링</h2>
              <p>설비별 가동 현황과 실시간 알람을 확인합니다.</p>
            </div>
          </div>

          <div className="metrics-grid">
            <div className="metric-card">
              <div className="card-top"><span className="label">전체 설비</span><span className="icon-badge">⚙️</span></div>
              <div className="value">{machines.length} <span>대</span></div>
            </div>
            <div className="metric-card">
              <div className="card-top"><span className="label">가동중인 설비</span><span className="icon-badge">▶️</span></div>
              <div className="value">{runningCount} <span>대</span></div>
            </div>
            <div className="metric-card">
              <div className="card-top"><span className="label">생산중인 LOT</span><span className="icon-badge">📦</span></div>
              <div className="value">{runningLots.length} <span>건</span></div>
            </div>
            <div className="metric-card">
              <div className="card-top"><span className="label">미해결 알람 / 이상설비</span><span className="icon-badge" style={{ background: "#fee2e2", color: "#dc2626" }}>⚠️</span></div>
              <div className="value" style={{ color: unresolvedAlarms.length > 0 ? "#dc2626" : undefined }}>{unresolvedAlarms.length} <span>/ {errorCount}대</span></div>
            </div>
          </div>

          <div className="status-section">
            <div className="section-title-row">
              <h3>설비 현황</h3>
            </div>
            {isLoading && <div className="empty-panel">불러오는 중...</div>}
            <div className="lines-grid">
              {machines.map((m) => (
                <div key={m.machineId} className="line-status-card" onClick={() => goToDetail(m.machineId)}>
                  <div className="line-card-header">
                    <div className="line-name">{m.machineName}<span>{m.machineId} · {m.processName}</span></div>
                    <span className={`status-tag ${STATUS_TAG_CLASS[m.status]}`}>{STATUS_LABEL[m.status]}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="side-alarm-card">
            <div className="alarm-card-header"><h3>🚨 미해결 설비 알람</h3><span className="alarm-badge">{unresolvedAlarms.length}건</span></div>
            {unresolvedAlarms.length === 0 ? (
              <div className="empty-panel">현재 미해결 알람이 없습니다.</div>
            ) : (
              <div className="alarm-list">
                {unresolvedAlarms.map((alarm) => (
                  <div key={alarm.machineAlarmHistoryId} className={`alarm-item ${ALARM_LEVEL_CLASS[alarm.alarmLevel] || "alarm-blue"}`}>
                    <div className="alarm-item-title">{alarm.machineName} · {alarm.alarmName}</div>
                    <div className="alarm-item-desc">{alarm.message || "-"}</div>
                    <div className="alarm-item-time">
                      <span>{new Date(alarm.occurredAt).toLocaleString()}</span>
                      {isAdmin && (
                        <button className="btn-text-link" onClick={() => handleClearAlarm(alarm.machineAlarmHistoryId)}>해제</button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="detail-view">
          <div className="detail-top-nav">
            <div className="back-link" onClick={() => setView("main")}>⬅ 목록으로</div>
            <div className="nav-path"><span>생산 관리</span> &gt; <span className="active-path">{selectedMachine?.machineName} 상세 현황</span></div>
          </div>

          {selectedMachine && (
            <>
              <div className="detail-header-row">
                <div className="detail-title-area">
                  <h2>{selectedMachine.machineName}</h2>
                  <p>{selectedMachine.machineId} · {selectedMachine.processName} ({selectedMachine.processCode})</p>
                </div>
                {isAdmin ? (
                  <select className="status-select" value={selectedMachine.status} onChange={(e) => handleStatusChange(e.target.value)}>
                    {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
                  </select>
                ) : (
                  <span className={`status-tag ${STATUS_TAG_CLASS[selectedMachine.status]}`}>{STATUS_LABEL[selectedMachine.status]}</span>
                )}
              </div>

              <div className="detail-split-grid">
                <div className="table-container" style={{ marginBottom: 0 }}>
                  <div className="table-header-row"><h3>최근 상태 이력</h3></div>
                  {statusHistory.length === 0 ? (
                    <div className="empty-panel">상태 이력이 없습니다.</div>
                  ) : (
                    <table className="monitoring-table">
                      <thead><tr><th>상태</th><th>LOT</th><th>공정</th><th>기록 시각</th></tr></thead>
                      <tbody>
                        {statusHistory.map((h) => (
                          <tr key={h.machineStatusHistoryId}>
                            <td><span className={`status-tag ${STATUS_TAG_CLASS[h.status]}`}>{STATUS_LABEL[h.status] || h.status}</span></td>
                            <td>{h.lotNo || "-"}</td>
                            <td>{h.processName || "-"}</td>
                            <td>{new Date(h.recordedAt).toLocaleString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>

                <div className="operator-card">
                  <h3>담당 인원</h3>
                  <div className="operator-list">
                    {assignments.length === 0 && <div className="empty-panel" style={{ padding: "10px" }}>배정된 인원이 없습니다.</div>}
                    {assignments.map((a) => (
                      <div className="op-item" key={a.assignmentId}>
                        <div className="operator-avatar">{a.workerName?.[0]}</div>
                        <div>
                          <div className="op-name">{a.workerName} <span style={{ color: "#0566d9", fontWeight: 700 }}>({ROLE_LABEL[a.assignmentRole]})</span></div>
                          <div className="op-role">{a.department} · {a.position}</div>
                        </div>
                        {isAdmin && (
                          <button
                            className="btn-text-link"
                            style={{ color: "#ef4444", marginLeft: "auto" }}
                            onClick={() => handleRemoveAssignment(a.workerId, a.workerName)}
                          >
                            해제
                          </button>
                        )}
                      </div>
                    ))}
                    {isAdmin && (
                      <button className="btn-add-operator" onClick={openAssignModal}>+ 인원 배정 관리</button>
                    )}
                  </div>
                </div>
              </div>

              <div className="table-container">
                <div className="table-header-row"><h3>이 설비의 알람 이력</h3></div>
                {machineAlarms.length === 0 ? (
                  <div className="empty-panel">알람 이력이 없습니다.</div>
                ) : (
                  <table className="monitoring-table">
                    <thead><tr><th>알람</th><th>레벨</th><th>발생 시각</th><th>상태</th><th></th></tr></thead>
                    <tbody>
                      {machineAlarms.map((a) => (
                        <tr key={a.machineAlarmHistoryId}>
                          <td>{a.alarmName}<div style={{ fontSize: "11px", color: "#94a3b8" }}>{a.message}</div></td>
                          <td>{a.alarmLevel}</td>
                          <td>{new Date(a.occurredAt).toLocaleString()}</td>
                          <td>
                            {a.cleared ? (
                              <span className="status-badge badge-ok">해제됨</span>
                            ) : (
                              <span className="status-badge badge-ng">미해결</span>
                            )}
                          </td>
                          <td>
                            {isAdmin && !a.cleared && (
                              <button className="btn-text-link" onClick={() => handleClearAlarm(a.machineAlarmHistoryId)}>해제</button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              <div className="table-container" style={{ marginBottom: 0 }}>
                <div className="table-header-row"><h3>최근 생산 실적</h3></div>
                {productionLogs.length === 0 ? (
                  <div className="empty-panel">등록된 생산 실적이 없습니다.</div>
                ) : (
                  <table className="monitoring-table">
                    <thead><tr><th>LOT</th><th>투입/양품/불량</th><th>상태</th><th>등록 시각</th></tr></thead>
                    <tbody>
                      {productionLogs.map((l) => (
                        <tr key={l.productionLogId}>
                          <td className="wo-num">{l.lotNo}</td>
                          <td>{l.inputQty} / {l.okQty} / {l.ngQty}</td>
                          <td><span className={`status-badge ${l.status === "COMPLETED" ? "badge-ok" : "badge-ng"}`}>{l.status}</span></td>
                          <td>{new Date(l.createdAt).toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </>
          )}
        </div>
      )}

      {showAssignModal && selectedMachine && (
        <div className="modal-overlay" onClick={() => setShowAssignModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{selectedMachine.machineName} 인원 배정</h3>
              <button className="btn-close" onClick={() => setShowAssignModal(false)}>✕</button>
            </div>

            <div className="modal-employee-list">
              {workers.map((w) => {
                const already = assignedWorkerIds.has(w.workerId);
                return (
                  <div key={w.workerId} className="employee-item">
                    <div>
                      <div className="emp-name">{w.workerName}</div>
                      <div className="emp-role">{w.department} · {w.position}</div>
                    </div>
                    <div className="assign-actions">
                      {already ? (
                        <span className="sub-label">이미 배정됨</span>
                      ) : (
                        <>
                          <button className="btn-assign" onClick={() => handleAssignResponsible(w.workerId)}>책임자로 지정</button>
                          <button className="btn-assign" onClick={() => handleAddWorker(w.workerId)}>작업자로 추가</button>
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
              {workers.length === 0 && (
                <div style={{ textAlign: "center", padding: "20px", color: "#64748b" }}>등록된 재직 작업자가 없습니다.</div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default ProductionPage;
