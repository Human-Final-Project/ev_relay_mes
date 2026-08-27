import React, { useMemo, useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";

export default function MaterialPage({ currentUser }) {
  const [tab, setTab] = useState("lots");
  const [lotForm, setLotForm] = useState(null);
  const [itemForm, setItemForm] = useState(null);
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState(null);
  const lots = useApiData(MesApi.getMaterialLots, []);
  const items = useApiData(MesApi.getItems, []);
  const itemRows = items.data || [];
  const receivableItems = itemRows.filter((item) => ["RM", "SA"].includes(item.itemType) && item.useYn === "Y");
  const canReceive = ["ADMIN", "MANAGER"].includes(currentUser?.role);
  const canCreateItems = ["ADMIN", "MANAGER", "OPERATOR"].includes(currentUser?.role);
  const canManageItems = ["ADMIN", "MANAGER"].includes(currentUser?.role);

  const summary = useMemo(() => ({
    itemCount: itemRows.length,
    lotCount: (lots.data || []).length,
    receivedQty: (lots.data || []).reduce((sum, lot) => sum + (Number(lot.receivedQty) || 0), 0),
  }), [itemRows, lots.data]);

  const run = async (action, reload) => {
    setSaving(true);
    setActionError(null);
    try {
      await action();
      await reload();
      return true;
    } catch (error) {
      setActionError(error);
      return false;
    } finally {
      setSaving(false);
    }
  };

  const createLot = async () => {
    const succeeded = await run(() => MesApi.createMaterialLot({
      ...lotForm,
      receivedQty: Number(lotForm.receivedQty),
      receivedBy: currentUser.memberId,
    }), lots.reload);
    if (succeeded) setLotForm(null);
  };

  const saveItem = async () => {
    const payload = {
      itemCode: itemForm.itemCode,
      itemName: itemForm.itemName,
      itemType: itemForm.itemType,
    };
    const succeeded = await run(
      () => itemForm.originalCode
        ? MesApi.updateItem(itemForm.originalCode, payload)
        : MesApi.createItem(payload),
      items.reload
    );
    if (succeeded) setItemForm(null);
  };

  const setItemActive = (item, active) => run(
    () => MesApi.setItemActive(item.itemCode, active),
    items.reload
  );

  const active = tab === "lots" ? lots : items;
  return <div className="mes-page">
    <PageHeader
      title="원자재 관리"
      description="품목 등록과 원자재 입고 LOT을 한 화면에서 관리합니다. LOT 상태는 생산 사용량에 따라 자동 변경됩니다."
      actions={<>
        <button className="btn secondary" onClick={() => { lots.reload(); items.reload(); }}>새로고침</button>
        {tab === "lots" && canReceive && <button className="btn" disabled={items.loading} onClick={() => setLotForm({ materialLotNo: "", itemCode: "", receivedQty: "" })}>원자재 입고</button>}
        {tab === "items" && canCreateItems && <button className="btn" onClick={() => setItemForm({ itemCode: "", itemName: "", itemType: "RM" })}>품목 등록</button>}
      </>}
    />

    <div className="mes-grid kpis material-summary-grid">
      <Kpi label="등록 품목" value={summary.itemCount}/>
      <Kpi label="입고 LOT" value={summary.lotCount}/>
      <Kpi label="총 입고 수량" value={summary.receivedQty}/>
    </div>

    <div className="tabs">
      <button className={`tab ${tab === "lots" ? "active" : ""}`} onClick={() => setTab("lots")}>원자재 입고 LOT</button>
      <button className={`tab ${tab === "items" ? "active" : ""}`} onClick={() => setTab("items")}>품목 관리</button>
    </div>

    {actionError && <ErrorState error={actionError}/>} 
    {active.loading ? <LoadingState/> : active.error ? <ErrorState error={active.error} onRetry={active.reload}/> : tab === "lots"
      ? <MaterialLotTable rows={lots.data}/>
      : <ItemTable rows={itemRows} canEdit={canManageItems} onEdit={(item) => setItemForm({ ...item, originalCode: item.itemCode })} onActive={setItemActive}/>
    }

    {lotForm && <Modal title="원자재 입고" onClose={() => setLotForm(null)} footer={<><button className="btn secondary" onClick={() => setLotForm(null)}>취소</button><button className="btn" disabled={saving || !lotForm.materialLotNo || !lotForm.itemCode || Number(lotForm.receivedQty) <= 0} onClick={createLot}>등록</button></>}>
      <div className="mes-form-grid">
        <Field label="원자재 LOT 번호"><input value={lotForm.materialLotNo} onChange={(event) => setLotForm({ ...lotForm, materialLotNo: event.target.value })}/></Field>
        <Field label="품목(코드)">
          <select value={lotForm.itemCode} onChange={(event) => setLotForm({ ...lotForm, itemCode: event.target.value })}>
            <option value="">품목 선택</option>
            {receivableItems.map((item) => <option key={item.itemCode} value={item.itemCode}>{item.itemName} ({item.itemCode})</option>)}
          </select>
        </Field>
        <Field label="입고 수량"><input type="number" min="1" value={lotForm.receivedQty} onChange={(event) => setLotForm({ ...lotForm, receivedQty: event.target.value })}/></Field>
      </div>
      {items.error && <ErrorState error={items.error}/>} 
      {actionError && <ErrorState error={actionError}/>} 
    </Modal>}

    {itemForm && <Modal title={itemForm.originalCode ? "품목 수정" : "품목 등록"} onClose={() => setItemForm(null)} footer={<><button className="btn secondary" onClick={() => setItemForm(null)}>취소</button><button className="btn" disabled={saving || !itemForm.itemCode || !itemForm.itemName} onClick={saveItem}>저장</button></>}>
      <div className="mes-form-grid">
        <Field label="품목 코드"><input value={itemForm.itemCode} disabled={Boolean(itemForm.originalCode)} onChange={(event) => setItemForm({ ...itemForm, itemCode: event.target.value })}/></Field>
        <Field label="품목명"><input value={itemForm.itemName} onChange={(event) => setItemForm({ ...itemForm, itemName: event.target.value })}/></Field>
        <Field label="유형">
          <select value={itemForm.itemType} onChange={(event) => setItemForm({ ...itemForm, itemType: event.target.value })}>
            <option value="RM">RM · 원자재</option><option value="SA">SA · 반제품</option><option value="FG">FG · 완제품</option>
          </select>
        </Field>
      </div>
      {actionError && <ErrorState error={actionError}/>} 
    </Modal>}
  </div>;
}

function MaterialLotTable({ rows = [] }) {
  if (!rows.length) return <EmptyState/>;
  return <div className="mes-table-wrap"><table className="mes-table">
    <thead><tr><th>원자재 LOT</th><th>품목</th><th>입고 수량</th><th>잔여 수량</th><th>상태</th><th>입고 처리</th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.materialLotId}>
      <td><strong>{row.materialLotNo}</strong><br/><span className="mono">#{row.materialLotId}</span></td>
      <td>{row.itemName}<br/><span className="mono">{row.itemCode}</span></td>
      <td>{row.receivedQty}</td><td>{row.currentQty}</td><td><StatusBadge value={row.status}/></td><td>{row.receivedBy}<br/>{formatDate(row.receivedAt)}</td>
    </tr>)}</tbody>
  </table></div>;
}

function ItemTable({ rows = [], canEdit, onEdit, onActive }) {
  if (!rows.length) return <EmptyState/>;
  return <div className="mes-table-wrap"><table className="mes-table">
    <thead><tr><th>품목 코드</th><th>품목명</th><th>유형</th><th>사용 여부</th><th>작업</th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.itemCode}>
      <td className="mono">{row.itemCode}</td><td>{row.itemName}</td><td><StatusBadge value={row.itemType}/></td><td>{row.useYn}</td>
      <td>{canEdit && <div className="mes-actions"><button className="btn small secondary" onClick={() => onEdit(row)}>수정</button><button className="btn small secondary" onClick={() => onActive(row, row.useYn !== "Y")}>{row.useYn === "Y" ? "비활성" : "활성"}</button></div>}</td>
    </tr>)}</tbody>
  </table></div>;
}

function Kpi({ label, value }) {
  return <div className="mes-card mes-kpi"><span className="label">{label}</span><strong className="value">{Number(value).toLocaleString()}</strong></div>;
}
