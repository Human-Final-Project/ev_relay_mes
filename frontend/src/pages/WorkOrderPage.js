import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge } from "../components/MesComponents";

const emptyOrder = { itemCode: "", targetQty: "" };

export default function WorkOrderPage({ currentUser }) {
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState(null);
  const result = useApiData(() => MesApi.getWorkOrders({ status }), [status]);
  const items = useApiData(MesApi.getItems, []);
  const orders = (result.data || []).filter((order) =>
    `${order.orderNo} ${order.itemCode} ${order.itemName}`
      .toLowerCase()
      .includes(search.toLowerCase())
  );
  const products = (items.data || []).filter(
    (item) => item.itemType === "FG" && (item.useYn === "Y" || item.itemCode === form?.itemCode)
  );
  const canManage = ["ADMIN", "MANAGER"].includes(currentUser?.role);

  const run = async (action) => {
    setSaving(true);
    setActionError(null);
    try {
      await action();
      await result.reload();
      return true;
    } catch (error) {
      setActionError(error);
      return false;
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    const payload = {
      itemCode: form.itemCode,
      targetQty: Number(form.targetQty),
    };
    const succeeded = await run(() =>
      form.workOrderId
        ? MesApi.updateWorkOrder(form.workOrderId, payload)
        : MesApi.createWorkOrder(payload)
    );
    if (succeeded) setForm(null);
  };

  return <div className="mes-page">
    <PageHeader
      title="작업지시"
      description="생산 품목과 목표 수량만 입력해 작업지시를 생성합니다. 확정하면 최초 LOT가 자동 생성됩니다."
      actions={canManage && <button className="btn" disabled={items.loading} onClick={() => setForm({ ...emptyOrder })}>작업지시 생성</button>}
    />

    <div className="mes-card mes-filter">
      <Field label="상태">
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="">전체</option>
          {["CREATED", "RELEASED", "RUNNING", "COMPLETED", "CANCELED"].map((value) => <option key={value}>{value}</option>)}
        </select>
      </Field>
      <Field label="검색">
        <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="작업지시·품목"/>
      </Field>
      <button className="btn secondary" onClick={result.reload}>새로고침</button>
    </div>

    {actionError && <ErrorState error={actionError}/>} 
    {result.loading ? <LoadingState/> : result.error ? <ErrorState error={result.error} onRetry={result.reload}/> : orders.length === 0 ? <EmptyState/> :
      <div className="mes-table-wrap"><table className="mes-table">
        <thead><tr><th>작업지시</th><th>품목</th><th>목표</th><th>완료 OK</th><th>잔여</th><th>상태</th><th>자동 생산 상태</th><th>작업</th></tr></thead>
        <tbody>{orders.map((order) => <tr key={order.workOrderId}>
          <td><strong>{order.orderNo}</strong><br/><span className="mono">#{order.workOrderId}</span></td>
          <td>{order.itemName}<br/><span className="mono">{order.itemCode}</span></td>
          <td>{order.targetQty}</td>
          <td>{order.completedOkQty}</td>
          <td>{order.remainingQty}</td>
          <td><StatusBadge value={order.status}/></td>
          <td>{automationLabel(order.automationStatus)}</td>
          <td>{canManage && <div className="mes-actions">
            {order.status === "CREATED" && <button className="btn small" disabled={saving} onClick={() => run(() => MesApi.releaseWorkOrder(order.workOrderId))}>확정 및 생산 시작</button>}
            {order.status === "CREATED" && <button className="btn small secondary" disabled={saving} onClick={() => setForm({ workOrderId: order.workOrderId, itemCode: order.itemCode, targetQty: order.targetQty })}>수정</button>}
            {order.status === "CREATED" && <button className="btn small danger" disabled={saving} onClick={() => window.confirm(`${order.orderNo}를 삭제할까요?`) && run(() => MesApi.deleteWorkOrder(order.workOrderId))}>삭제</button>}
          </div>}</td>
        </tr>)}</tbody>
      </table></div>}

    {form && <Modal
      title={form.workOrderId ? "작업지시 수정" : "작업지시 생성"}
      onClose={() => setForm(null)}
      footer={<><button className="btn secondary" onClick={() => setForm(null)}>취소</button><button className="btn" disabled={saving || !form.itemCode || Number(form.targetQty) <= 0} onClick={submit}>저장</button></>}
    >
      <div className="mes-form-grid">
        <Field label="제품(코드)">
          <select value={form.itemCode} onChange={(event) => setForm({ ...form, itemCode: event.target.value })}>
            <option value="">제품 선택</option>
            {products.map((item) => <option key={item.itemCode} value={item.itemCode}>{item.itemName} ({item.itemCode})</option>)}
          </select>
        </Field>
        <Field label="목표 수량"><input type="number" min="1" value={form.targetQty} onChange={(event) => setForm({ ...form, targetQty: event.target.value })}/></Field>
      </div>
      {items.error && <ErrorState error={items.error}/>} 
      {actionError && <ErrorState error={actionError}/>} 
    </Modal>}
  </div>;
}

function automationLabel(value) {
  return ({
    DRAFT: "확정 대기",
    INITIAL_LOT_PENDING: "최초 LOT 자재 대기",
    PIPELINE_ACTIVE: "자동 생산 중",
    LOT_HOLD: "설비 복구 대기",
    AUTO_SUPPLEMENT_PENDING: "보충 LOT 자재 대기",
    AUTO_SUPPLEMENT_ACTIVE: "자동 보충 생산 중",
    AUTO_SUPPLEMENT_LIMIT_REACHED: "보충 생산 한도 도달",
    COMPLETED: "목표 수량 달성",
    CANCELED: "취소",
  })[value] || value || "-";
}
