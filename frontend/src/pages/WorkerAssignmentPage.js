import React, { useEffect, useMemo, useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import {
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  Modal,
  PageHeader,
  SortableTh,
  StatusBadge,
  formatDate,
  useSortableRows,
} from "../components/MesComponents";

const ASSIGNMENT_SORTERS = {
  worker: (row) => `${row.workerName || ""} ${row.workerNo || ""}`,
  organization: (row) => `${row.department || ""} ${row.position || ""}`,
  role: (row) => row.assignmentRole,
  assignedAt: (row) => row.assignedAt,
};
const WORKER_SORTERS = {
  worker: (row) => `${row.workerNo || ""} ${row.workerName || ""}`,
  type: (row) => row.memberId ? "ACCOUNT" : "FIELD",
  department: (row) => row.department,
  position: (row) => row.position,
  status: (row) => row.status,
};

export default function WorkerAssignmentPage({ currentUser }) {
  const machines = useApiData(MesApi.getMachines, []);
  const workers = useApiData(() => MesApi.getWorkers({}), []);
  const [machineId, setMachineId] = useState("");
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [workerId, setWorkerId] = useState("");
  const [mode, setMode] = useState("worker");
  const [workerForm, setWorkerForm] = useState(null);
  const canEdit = ["ADMIN", "MANAGER"].includes(currentUser?.role);
  const sortedAssignments = useSortableRows(assignments, ASSIGNMENT_SORTERS);
  const sortedWorkers = useSortableRows(workers.data || [], WORKER_SORTERS);

  const assignableWorkers = useMemo(
    () => (workers.data || []).filter((worker) =>
      worker.status === "ACTIVE" && (mode !== "responsible" || worker.memberId)
    ),
    [mode, workers.data]
  );

  useEffect(() => {
    setWorkerId("");
  }, [mode]);

  useEffect(() => {
    if (!machineId) {
      setAssignments([]);
      return;
    }
    setLoading(true);
    setError(null);
    MesApi.getMachineAssignments(machineId)
      .then((response) => setAssignments(response.data || []))
      .catch(setError)
      .finally(() => setLoading(false));
  }, [machineId]);

  const reload = async () => {
    if (machineId) {
      const response = await MesApi.getMachineAssignments(machineId);
      setAssignments(response.data || []);
    }
    await workers.reload();
  };

  const assign = async () => {
    setError(null);
    try {
      if (mode === "responsible") {
        await MesApi.assignResponsible(machineId, workerId);
      } else {
        await MesApi.addMachineWorker(machineId, workerId);
      }
      setWorkerId("");
      await reload();
    } catch (assignError) {
      setError(assignError);
    }
  };

  const saveWorker = async () => {
    setError(null);
    try {
      const data = { ...workerForm, status: workerForm.status || "ACTIVE" };
      if (workerForm.workerId) {
        await MesApi.updateWorker(workerForm.workerId, data);
      } else {
        await MesApi.createWorker(data);
      }
      setWorkerForm(null);
      await workers.reload();
    } catch (saveError) {
      setError(saveError);
    }
  };

  return <div className="mes-page">
    <PageHeader
      title="작업자 배정"
      description="책임자는 로그인 사용자 중에서, 일반 작업자는 사용자 등록 없이 현장 명단에서 바로 배정합니다."
      actions={canEdit && <button
        className="btn"
        onClick={() => setWorkerForm({
          workerNo: "",
          workerName: "",
          department: "",
          position: "",
          status: "ACTIVE",
        })}
      >일반 작업자 등록</button>}
    />

    <div className="mes-grid two">
      <section className="mes-card">
        <h2>설비별 배정</h2>
        <p className="section-description">
          책임자가 없는 설비는 생산 시작과 알람 해제 후 재가동이 제한됩니다.
        </p>
        <div className="mes-filter">
          <Field label="설비">
            <select value={machineId} onChange={(event) => setMachineId(event.target.value)}>
              <option value="">설비 선택</option>
              {(machines.data || []).map((machine) =>
                <option key={machine.machineId} value={machine.machineId}>
                  {machine.machineId} · {machine.processName}
                </option>
              )}
            </select>
          </Field>
          {canEdit && <>
            <Field label="배정 유형">
              <select value={mode} onChange={(event) => setMode(event.target.value)}>
                <option value="worker">일반 작업자</option>
                <option value="responsible">책임자</option>
              </select>
            </Field>
            <Field label={mode === "responsible" ? "사용자 등록 책임자" : "작업자"}>
              <select value={workerId} onChange={(event) => setWorkerId(event.target.value)}>
                <option value="">
                  {mode === "responsible" ? "로그인 사용자 선택" : "작업자 선택"}
                </option>
                {assignableWorkers.map((worker) =>
                  <option key={worker.workerId} value={worker.workerId}>
                    {worker.workerName} ({worker.workerNo})
                    {worker.loginId ? ` · ${worker.loginId}` : ""}
                  </option>
                )}
              </select>
            </Field>
            <button className="btn" disabled={!machineId || !workerId} onClick={assign}>배정</button>
          </>}
        </div>
        {mode === "responsible" && assignableWorkers.length === 0 && !workers.loading &&
          <p className="form-hint">배정할 계정이 없습니다. 먼저 사용자 관리에서 OPERATOR를 등록하세요.</p>}
        {error && <ErrorState error={error}/>}
        {loading ? <LoadingState/> : !machineId ? <EmptyState message="설비를 선택하세요."/> :
          !assignments.length ? <EmptyState message="배정된 작업자가 없습니다."/> :
            <div className="mes-table-wrap"><table className="mes-table">
              <thead><tr>
                <SortableTh label="작업자" sortKey="worker" {...sortedAssignments}/>
                <SortableTh label="부서/직급" sortKey="organization" {...sortedAssignments}/>
                <SortableTh label="역할" sortKey="role" {...sortedAssignments}/>
                <SortableTh label="배정" sortKey="assignedAt" {...sortedAssignments}/>
                <th>작업</th>
              </tr></thead>
              <tbody>{sortedAssignments.rows.map((assignment) => <tr key={assignment.assignmentId}>
                <td>{assignment.workerName}<br/><span className="mono">{assignment.workerNo}</span></td>
                <td>{assignment.department || "-"} / {assignment.position || "-"}</td>
                <td><StatusBadge value={assignment.assignmentRole}/></td>
                <td>{formatDate(assignment.assignedAt)}</td>
                <td>{canEdit && <button
                  className="btn small danger"
                  onClick={async () => {
                    await MesApi.removeMachineWorker(machineId, assignment.workerId);
                    await reload();
                  }}
                >해제</button>}</td>
              </tr>)}</tbody>
            </table></div>}
      </section>

      <section className="mes-card">
        <h2>작업자 목록</h2>
        <p className="section-description">
          계정 연동 표시는 책임자 후보이며, 미연동 작업자는 일반 작업에만 배정할 수 있습니다.
        </p>
        {workers.loading ? <LoadingState/> : workers.error ?
          <ErrorState error={workers.error}/> :
          <div className="mes-table-wrap"><table className="mes-table">
            <thead><tr>
              <SortableTh label="사번/이름" sortKey="worker" {...sortedWorkers}/>
              <SortableTh label="구분" sortKey="type" {...sortedWorkers}/>
              <SortableTh label="부서" sortKey="department" {...sortedWorkers}/>
              <SortableTh label="직급" sortKey="position" {...sortedWorkers}/>
              <SortableTh label="상태" sortKey="status" {...sortedWorkers}/>
              <th>작업</th>
            </tr></thead>
            <tbody>{sortedWorkers.rows.map((worker) => <tr key={worker.workerId}>
              <td><span className="mono">{worker.workerNo}</span><br/>{worker.workerName}</td>
              <td>{worker.memberId
                ? <><StatusBadge value="ACCOUNT"/><br/><small>{worker.loginId}</small></>
                : <StatusBadge value="FIELD"/>}</td>
              <td>{worker.department || "-"}</td>
              <td>{worker.position || "-"}</td>
              <td><StatusBadge value={worker.status}/></td>
              <td>{canEdit && !worker.memberId && <button
                className="btn small secondary"
                onClick={() => setWorkerForm({ ...worker })}
              >수정</button>}</td>
            </tr>)}</tbody>
          </table></div>}
      </section>
    </div>

    {workerForm && <Modal
      title={workerForm.workerId ? "일반 작업자 수정" : "일반 작업자 등록"}
      onClose={() => setWorkerForm(null)}
      footer={<>
        <button className="btn secondary" onClick={() => setWorkerForm(null)}>취소</button>
        <button
          className="btn"
          disabled={!/^EVR\d{8}$/.test(workerForm.workerNo || "")
            || !workerForm.workerName?.trim()
            || !workerForm.department?.trim()
            || !workerForm.position?.trim()}
          onClick={saveWorker}
        >저장</button>
      </>}
    >
      <div className="mes-form-grid">
        <Field label="사번">
          <input
            value={workerForm.workerNo}
            maxLength={11}
            placeholder="EVR00000001"
            onChange={(event) => setWorkerForm({
              ...workerForm,
              workerNo: event.target.value.toUpperCase(),
            })}
          />
        </Field>
        <Field label="이름"><input value={workerForm.workerName} onChange={(event) => setWorkerForm({ ...workerForm, workerName: event.target.value })}/></Field>
        <Field label="부서 (필수)"><input value={workerForm.department || ""} onChange={(event) => setWorkerForm({ ...workerForm, department: event.target.value })}/></Field>
        <Field label="직급 (필수)"><input value={workerForm.position || ""} onChange={(event) => setWorkerForm({ ...workerForm, position: event.target.value })}/></Field>
      </div>
      <p className="form-hint">사번은 EVR + 숫자 8자리로 입력합니다.</p>
      {error && <ErrorState error={error}/>}
    </Modal>}
  </div>;
}
