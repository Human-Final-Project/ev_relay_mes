import React, { useMemo, useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";

const emptyFilters = {
  workOrderId: "",
  lotNo: "",
  conditionType: "process",
  conditionValue: "",
  result: "",
  defectCode: "",
};

export default function QualityPage({ currentUser }) {
  const [tab, setTab] = useState("inspections");
  const [filters, setFilters] = useState(emptyFilters);
  const [applied, setApplied] = useState({});
  const [edit, setEdit] = useState(null);
  const [actionError, setActionError] = useState(null);

  const inspections = useApiData(() => MesApi.getInspections(applied), [JSON.stringify(applied)]);
  const defects = useApiData(() => MesApi.getDefects(applied), [JSON.stringify(applied)]);
  const standards = useApiData(MesApi.getInspectionStandards, []);
  const workOrders = useApiData(() => MesApi.getWorkOrders({}), []);
  const lots = useApiData(
    () => filters.workOrderId
      ? MesApi.getLots({ workOrderId: filters.workOrderId })
      : Promise.resolve({ data: [] }),
    [filters.workOrderId]
  );
  const machines = useApiData(MesApi.getMachines, []);
  const processes = useApiData(MesApi.getProcesses, []);
  const defectCodes = useApiData(MesApi.getDefectCodes, []);

  const active = tab === "inspections" ? inspections : tab === "defects" ? defects : standards;
  const canEdit = ["ADMIN", "MANAGER"].includes(currentUser?.role);
  const conditionOptions = filters.conditionType === "machine"
    ? (machines.data || []).map((machine) => ({ value: machine.machineId, label: `${machine.machineId} · ${machine.machineName}` }))
    : (processes.data || []).map((process) => ({ value: process.processCode, label: `${process.processCode} · ${process.processName}` }));

  const lotOptions = useMemo(() => {
    const seen = new Set();
    return (lots.data || []).filter((lot) => {
      if (seen.has(lot.lotNo)) return false;
      seen.add(lot.lotNo);
      return true;
    });
  }, [lots.data]);

  const applyFilters = () => {
    const next = { workOrderId: filters.workOrderId || undefined, lotNo: filters.lotNo || undefined };
    if (filters.conditionValue) {
      next[filters.conditionType === "machine" ? "machineId" : "processCode"] = filters.conditionValue;
    }
    if (tab === "inspections" && filters.result) next.result = filters.result;
    if (tab === "defects" && filters.defectCode) next.defectCode = filters.defectCode;
    setApplied(next);
  };

  const resetFilters = () => {
    setFilters(emptyFilters);
    setApplied({});
  };

  const changeTab = (nextTab) => {
    setTab(nextTab);
    setApplied({});
    setFilters(emptyFilters);
  };

  const saveLimits = async () => {
    setActionError(null);
    try {
      await MesApi.updateInspectionLimits(edit.standardId, {
        lowerLimit: Number(edit.lowerLimit),
        upperLimit: Number(edit.upperLimit),
      });
      setEdit(null);
      standards.reload();
    } catch (error) {
      setActionError(error);
    }
  };

  return <div className="mes-page">
    <PageHeader title="품질 관리" description="작업지시를 먼저 선택하고 그 작업지시에 포함된 LOT의 검사 결과와 불량 이력을 조회합니다." actions={<button className="btn secondary" onClick={active.reload}>새로고침</button>}/>

    <div className="tabs">
      {[["inspections", "검사 결과"], ["defects", "불량 이력"], ["standards", "검사 기준"]].map(([key, label]) =>
        <button key={key} className={`tab ${tab === key ? "active" : ""}`} onClick={() => changeTab(key)}>{label}</button>
      )}
    </div>

    {tab !== "standards" && <div className="mes-card mes-filter">
      <Field label="작업지시">
        <select
          value={filters.workOrderId}
          onChange={(event) => setFilters({ ...filters, workOrderId: event.target.value, lotNo: "" })}
        >
          <option value="">전체 작업지시</option>
          {(workOrders.data || []).map((order) =>
            <option key={order.workOrderId} value={order.workOrderId}>
              {order.orderNo} · {order.itemName}
            </option>
          )}
        </select>
      </Field>
      <Field label="LOT">
        <select
          value={filters.lotNo}
          disabled={!filters.workOrderId || lots.loading}
          onChange={(event) => setFilters({ ...filters, lotNo: event.target.value })}
        >
          <option value="">{filters.workOrderId ? "해당 작업지시 전체 LOT" : "작업지시를 먼저 선택하세요"}</option>
          {lotOptions.map((lot) =>
            <option key={lot.lotId} value={lot.lotNo}>
              {lot.lotNo} · {lot.lotType === "SUPPLEMENT" ? `보충 ${lot.productionRound - 1}차` : "최초"}
            </option>
          )}
        </select>
      </Field>
      <Field label="조건">
        <select value={filters.conditionType} onChange={(event) => setFilters({ ...filters, conditionType: event.target.value, conditionValue: "" })}>
          <option value="process">공정</option>
          <option value="machine">설비</option>
        </select>
      </Field>
      <Field label={filters.conditionType === "machine" ? "설비 선택" : "공정 선택"}>
        <select value={filters.conditionValue} onChange={(event) => setFilters({ ...filters, conditionValue: event.target.value })}>
          <option value="">전체</option>
          {conditionOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </Field>
      {tab === "inspections" && <Field label="결과">
        <select value={filters.result} onChange={(event) => setFilters({ ...filters, result: event.target.value })}>
          <option value="">전체</option><option value="OK">OK</option><option value="NG">NG</option>
        </select>
      </Field>}
      {tab === "defects" && <Field label="불량 유형">
        <select value={filters.defectCode} onChange={(event) => setFilters({ ...filters, defectCode: event.target.value })}>
          <option value="">전체</option>
          {(defectCodes.data || []).map((code) => <option key={code.defectCode} value={code.defectCode}>{code.defectName} ({code.defectCode})</option>)}
        </select>
      </Field>}
      <button className="btn" onClick={applyFilters}>조회</button>
      <button className="btn secondary" onClick={resetFilters}>초기화</button>
    </div>}

    {active.loading ? <LoadingState/> : active.error ? <ErrorState error={active.error} onRetry={active.reload}/> : tab === "inspections"
      ? <InspectionTable rows={inspections.data}/>
      : tab === "defects"
        ? <DefectTable rows={defects.data}/>
        : <StandardTable rows={standards.data} canEdit={canEdit} onEdit={setEdit}/>}

    {edit && <Modal title="검사 기준 수정" onClose={() => setEdit(null)} footer={<><button className="btn secondary" onClick={() => setEdit(null)}>취소</button><button className="btn" onClick={saveLimits}>저장</button></>}>
      <p><strong>{edit.inspectionItem}</strong> · {edit.unit}</p>
      <div className="mes-form-grid">
        <Field label="하한"><input type="number" step="any" value={edit.lowerLimit} onChange={(event) => setEdit({ ...edit, lowerLimit: event.target.value })}/></Field>
        <Field label="상한"><input type="number" step="any" value={edit.upperLimit} onChange={(event) => setEdit({ ...edit, upperLimit: event.target.value })}/></Field>
      </div>
      {actionError && <ErrorState error={actionError}/>} 
    </Modal>}
  </div>;
}

function InspectionTable({ rows = [] }) {
  if (!rows.length) return <EmptyState/>;
  return <div className="mes-table-wrap"><table className="mes-table">
    <thead><tr><th>LOT/제품 순번</th><th>설비·공정</th><th>검사 항목</th><th>측정값</th><th>기준 범위</th><th>버전</th><th>결과</th><th>검사 시각</th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.inspectionId}>
      <td><span className="mono">{row.lotNo}</span><br/>#{row.unitSeq}</td>
      <td>{row.machineName}<br/><span className="mono">{row.machineId} · {row.processCode}</span></td>
      <td>{row.inspectionItem}</td><td><strong>{row.measuredValue}</strong> {row.unit}</td><td>{row.lowerLimit} ~ {row.upperLimit}</td><td>v{row.standardVersion}</td><td><StatusBadge value={row.result}/></td><td>{formatDate(row.inspectedAt)}</td>
    </tr>)}</tbody>
  </table></div>;
}

function DefectTable({ rows = [] }) {
  if (!rows.length) return <EmptyState/>;
  return <div className="mes-table-wrap"><table className="mes-table">
    <thead><tr><th>LOT</th><th>설비·공정</th><th>불량 코드/명</th><th>수량</th><th>불량 설명</th><th>발생 시각</th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.defectHistoryId}>
      <td className="mono">{row.lotNo}</td><td>{row.machineName}<br/><span className="mono">{row.machineId} · {row.processCode}</span></td><td>{row.defectName}<br/><span className="mono">{row.defectCode}</span></td><td>{row.defectQty}</td><td>{row.defectDescription || "-"}</td><td>{formatDate(row.occurredAt)}</td>
    </tr>)}</tbody>
  </table></div>;
}

function StandardTable({ rows = [], canEdit, onEdit }) {
  if (!rows.length) return <EmptyState/>;
  return <div className="mes-table-wrap"><table className="mes-table">
    <thead><tr><th>공정</th><th>품목</th><th>검사 항목</th><th>단위</th><th>하한</th><th>상한</th><th>버전</th><th></th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.standardId}>
      <td>{row.processName}<br/><span className="mono">{row.processCode}</span></td><td>{row.itemName}</td><td>{row.inspectionItem}</td><td>{row.unit}</td><td>{row.lowerLimit}</td><td>{row.upperLimit}</td><td>v{row.standardVersion}</td><td>{canEdit && <button className="btn small secondary" onClick={() => onEdit({ ...row })}>기준 수정</button>}</td>
    </tr>)}</tbody>
  </table></div>;
}
