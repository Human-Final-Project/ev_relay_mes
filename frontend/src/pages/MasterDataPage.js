import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader } from "../components/MesComponents";

export default function MasterDataPage({ currentUser }) {
  const [tab, setTab] = useState("boms");
  const [bomForm, setBomForm] = useState(null);
  const [error, setError] = useState(null);
  const canEdit = ["ADMIN", "MANAGER"].includes(currentUser?.role);
  const items = useApiData(MesApi.getItems, []);
  const boms = useApiData(MesApi.getBoms, []);
  const processes = useApiData(MesApi.getProcesses, []);
  const defects = useApiData(MesApi.getDefectCodes, []);
  const alarms = useApiData(MesApi.getAlarmCodes, []);

  const itemRows = items.data || [];
  const selectableItems = bomForm?.bomId ? itemRows : itemRows.filter((item) => item.useYn === "Y");
  const parentItems = selectableItems.filter((item) => item.itemType !== "RM" || item.itemCode === bomForm?.parentItemCode);
  const childItems = selectableItems.filter((item) => item.itemCode !== bomForm?.parentItemCode);
  const validBom = Boolean(
    bomForm?.parentItemCode
      && bomForm?.childItemCode
      && bomForm.parentItemCode !== bomForm.childItemCode
      && Number(bomForm.quantity) > 0
      && bomForm.processCode
  );
  const active = tab === "boms" ? boms : tab === "processes" ? processes : defects;

  const run = async (action, reload, close) => {
    setError(null);
    try {
      await action();
      await reload();
      close();
    } catch (requestError) {
      setError(requestError);
    }
  };

  const saveBom = () => {
    const data = {
      parentItemCode: bomForm.parentItemCode,
      childItemCode: bomForm.childItemCode,
      quantity: Number(bomForm.quantity),
      processCode: bomForm.processCode,
    };
    return run(
      () => bomForm.bomId ? MesApi.updateBom(bomForm.bomId, data) : MesApi.createBom(data),
      boms.reload,
      () => setBomForm(null)
    );
  };

  return <div className="mes-page">
    <PageHeader title="기준정보" description="BOM, 공정, 불량·알람 코드 마스터를 관리합니다. 품목 등록은 원자재 관리 메뉴에서 처리합니다." actions={<button className="btn secondary" onClick={active.reload}>새로고침</button>}/>

    <div className="tabs">
      {[["boms", "BOM"], ["processes", "공정"], ["codes", "불량·알람 코드"]].map(([key, label]) =>
        <button key={key} className={`tab ${tab === key ? "active" : ""}`} onClick={() => setTab(key)}>{label}</button>
      )}
    </div>

    {tab === "boms" && <section className="mes-card">
      <div className="mes-page-header">
        <div><h2>BOM 구성</h2><p className="section-description">상위 품목 1개를 생산할 때 필요한 구성품과 적용 공정을 관리합니다.</p></div>
        {canEdit && <button className="btn" disabled={items.loading || processes.loading} onClick={() => setBomForm({ parentItemCode: "", childItemCode: "", quantity: "1", processCode: "" })}>구성품 추가</button>}
      </div>
      {boms.loading ? <LoadingState/> : boms.error ? <ErrorState error={boms.error}/> : <BomTable rows={boms.data} items={itemRows} canEdit={canEdit} onEdit={setBomForm} onActive={(row, activeValue) => run(() => MesApi.setBomActive(row.bomId, activeValue), boms.reload, () => {})}/>} 
    </section>}

    {tab === "processes" && <section className="mes-card">
      <h2>공정</h2>
      {processes.loading ? <LoadingState/> : <SimpleTable heads={["순서", "공정 코드", "공정명", "설명"]} rows={(processes.data || []).map((row) => [row.processOrder, row.processCode, row.processName, row.description || "-"])}/>} 
    </section>}

    {tab === "codes" && <div className="mes-grid two">
      <section className="mes-card"><h2>불량 코드</h2><SimpleTable heads={["코드", "불량명", "공정", "설명"]} rows={(defects.data || []).map((row) => [row.defectCode, row.defectName, row.processName || row.processCode, row.description || "-"])}/></section>
      <section className="mes-card"><h2>알람 코드</h2><SimpleTable heads={["코드", "알람명", "설비 유형", "설명"]} rows={(alarms.data || []).map((row) => [row.alarmCode, row.alarmName, row.machineType, row.description || "-"])}/></section>
    </div>}

    {error && <ErrorState error={error}/>} 
    {bomForm && <Modal title={bomForm.bomId ? "BOM 구성품 수정" : "BOM 구성품 추가"} onClose={() => setBomForm(null)} footer={<><button className="btn secondary" onClick={() => setBomForm(null)}>취소</button><button className="btn" disabled={!validBom} onClick={saveBom}>저장</button></>}>
      <div className="mes-form-grid">
        <Field label="상위 품목">
          <select value={bomForm.parentItemCode} onChange={(event) => setBomForm({ ...bomForm, parentItemCode: event.target.value, childItemCode: event.target.value === bomForm.childItemCode ? "" : bomForm.childItemCode })}>
            <option value="">반제품·완제품 선택</option>
            {parentItems.map((item) => <option key={item.itemCode} value={item.itemCode}>{itemLabel(item)}</option>)}
          </select>
        </Field>
        <Field label="하위 품목">
          <select value={bomForm.childItemCode} onChange={(event) => setBomForm({ ...bomForm, childItemCode: event.target.value })}>
            <option value="">구성품 선택</option>
            {childItems.map((item) => <option key={item.itemCode} value={item.itemCode}>{itemLabel(item)}</option>)}
          </select>
        </Field>
        <Field label="필요 수량"><input type="number" min="0.001" step="0.001" value={bomForm.quantity} onChange={(event) => setBomForm({ ...bomForm, quantity: event.target.value })}/></Field>
        <Field label="적용 공정">
          <select value={bomForm.processCode} onChange={(event) => setBomForm({ ...bomForm, processCode: event.target.value })}>
            <option value="">공정 선택</option>
            {(processes.data || []).map((process) => <option key={process.processCode} value={process.processCode}>{process.processCode} · {process.processName}</option>)}
          </select>
        </Field>
      </div>
      <p className="form-help">원자재(RM)는 상위 품목에서 제외되며, 상위 품목과 같은 품목은 구성품으로 선택할 수 없습니다.</p>
      {error && <ErrorState error={error}/>} 
    </Modal>}
  </div>;
}

function BomTable({ rows = [], items = [], canEdit, onEdit, onActive }) {
  if (!rows.length) return <EmptyState/>;
  const names = Object.fromEntries(items.map((item) => [item.itemCode, item.itemName]));
  return <SimpleTable heads={["상위 품목", "하위 품목", "수량", "적용 공정", "사용", "작업"]} rows={rows.map((row) => [
    <><strong>{names[row.parentItemCode] || "-"}</strong><br/><span className="mono">{row.parentItemCode}</span></>,
    <><strong>{names[row.childItemCode] || "-"}</strong><br/><span className="mono">{row.childItemCode}</span></>,
    row.quantity,
    row.processCode,
    row.useYn,
    canEdit && <div className="mes-actions"><button className="btn small secondary" onClick={() => onEdit({ ...row })}>수정</button><button className="btn small secondary" onClick={() => onActive(row, row.useYn !== "Y")}>{row.useYn === "Y" ? "비활성" : "활성"}</button></div>,
  ])}/>;
}

function itemLabel(item) {
  return `${item.itemCode} · ${item.itemName} (${item.itemType})${item.useYn === "Y" ? "" : " · 비활성"}`;
}

function SimpleTable({ heads, rows = [] }) {
  if (!rows.length) return <EmptyState/>;
  return <div className="mes-table-wrap"><table className="mes-table"><thead><tr>{heads.map((head) => <th key={head}>{head}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={index}>{row.map((value, cellIndex) => <td key={cellIndex}>{value}</td>)}</tr>)}</tbody></table></div>;
}
