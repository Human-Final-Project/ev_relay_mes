import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, SortableTh, StatusBadge, formatDate, useSortableRows } from "../components/MesComponents";

const emptyOrder = { itemCode: "", targetQty: "", plannedStartAt: "", plannedEndAt: "" };
const ORDER_SORTERS = {
  order: (row) => row.orderNo,
  item: (row) => `${row.itemName || ""} ${row.itemCode || ""}`,
  target: (row) => row.targetQty,
  completed: (row) => row.completedOkQty,
  remaining: (row) => row.remainingQty,
  schedule: (row) => row.plannedEndAt || row.plannedStartAt,
  status: (row) => row.status,
  automation: (row) => automationLabel(row.automationStatus),
};

export default function WorkOrderPage({ currentUser }) {
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [assignmentIssue, setAssignmentIssue] = useState(null);
  const result = useApiData(() => MesApi.getWorkOrders({ status }), [status]);
  const items = useApiData(MesApi.getItems, []);
  const orders = (result.data || []).filter((order) =>
    `${order.orderNo} ${order.itemCode} ${order.itemName}`
      .toLowerCase()
      .includes(search.toLowerCase())
  );
  const sortedOrders = useSortableRows(orders, ORDER_SORTERS);
  const products = (items.data || []).filter(
    (item) => item.itemType === "FG" && (item.useYn === "Y" || item.itemCode === form?.itemCode)
  );
  const canManage = ["ADMIN", "MANAGER"].includes(currentUser?.role);

  const run = async (action) => {
    setSaving(true);
    setActionError(null);
    setAssignmentIssue(null);
    try {
      await action();
      await result.reload();
      return true;
    } catch (error) {
      if (error?.response?.data?.code === "WK008") {
        setAssignmentIssue(error.response.data);
      } else {
        setActionError(error);
      }
      return false;
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    const payload = {
      itemCode: form.itemCode,
      targetQty: Number(form.targetQty),
      plannedStartAt: form.plannedStartAt,
      plannedEndAt: form.plannedEndAt,
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
      description="작업지시 목표 수량은 양품 생산 완료와 자동 보충 생산의 기준입니다. 계획 일정은 대시보드 일정 위험에 반영됩니다."
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
        <thead><tr>
          <SortableTh label="작업지시" sortKey="order" {...sortedOrders}/>
          <SortableTh label="품목" sortKey="item" {...sortedOrders}/>
          <SortableTh label="목표" sortKey="target" {...sortedOrders}/>
          <SortableTh label="완료 OK" sortKey="completed" {...sortedOrders}/>
          <SortableTh label="잔여" sortKey="remaining" {...sortedOrders}/>
          <SortableTh label="계획 일정" sortKey="schedule" {...sortedOrders}/>
          <SortableTh label="상태" sortKey="status" {...sortedOrders}/>
          <SortableTh label="자동 생산 상태" sortKey="automation" {...sortedOrders}/>
          <th>작업</th>
        </tr></thead>
        <tbody>{sortedOrders.rows.map((order) => <tr key={order.workOrderId}>
          <td><strong>{order.orderNo}</strong><br/><span className="mono">#{order.workOrderId}</span></td>
          <td>{order.itemName}<br/><span className="mono">{order.itemCode}</span></td>
          <td>{order.targetQty}</td>
          <td>{order.completedOkQty}</td>
          <td>{order.remainingQty}</td>
          <td>{order.plannedStartAt && order.plannedEndAt
            ? <><span>{formatDate(order.plannedStartAt)}</span><br/><span>{formatDate(order.plannedEndAt)}</span></>
            : <span className="mes-status status-warn">일정 미지정</span>}</td>
          <td><StatusBadge value={order.status}/></td>
          <td>{automationLabel(order.automationStatus)}</td>
          <td>{canManage && <div className="mes-actions">
            {order.status === "CREATED" && <button className="btn small" disabled={saving} onClick={() => run(() => MesApi.releaseWorkOrder(order.workOrderId))}>확정 및 생산 시작</button>}
            {order.status === "CREATED" && <button className="btn small secondary" disabled={saving} onClick={() => setForm({
              workOrderId: order.workOrderId,
              itemCode: order.itemCode,
              targetQty: order.targetQty,
              plannedStartAt: toDateTimeInput(order.plannedStartAt),
              plannedEndAt: toDateTimeInput(order.plannedEndAt),
            })}>수정</button>}
            {order.status === "CREATED" && <button className="btn small danger" disabled={saving} onClick={() => window.confirm(`${order.orderNo}를 삭제할까요?`) && run(() => MesApi.deleteWorkOrder(order.workOrderId))}>삭제</button>}
          </div>}</td>
        </tr>)}</tbody>
      </table></div>}

    {form && <Modal
      title={form.workOrderId ? "작업지시 수정" : "작업지시 생성"}
      onClose={() => setForm(null)}
      footer={<><button className="btn secondary" onClick={() => setForm(null)}>취소</button><button className="btn" disabled={saving || !form.itemCode || Number(form.targetQty) <= 0 || !validSchedule(form)} onClick={submit}>저장</button></>}
    >
      <div className="mes-form-grid">
        <Field label="제품(코드)">
          <select value={form.itemCode} onChange={(event) => setForm({ ...form, itemCode: event.target.value })}>
            <option value="">제품 선택</option>
            {products.map((item) => <option key={item.itemCode} value={item.itemCode}>{item.itemName} ({item.itemCode})</option>)}
          </select>
        </Field>
        <Field label="목표 수량"><input type="number" min="1" value={form.targetQty} onChange={(event) => setForm({ ...form, targetQty: event.target.value })}/></Field>
        <Field label="계획 시작"><input type="datetime-local" value={form.plannedStartAt || ""} onChange={(event) => setForm({ ...form, plannedStartAt: event.target.value })}/></Field>
        <Field label="계획 종료"><input type="datetime-local" value={form.plannedEndAt || ""} min={form.plannedStartAt || undefined} onChange={(event) => setForm({ ...form, plannedEndAt: event.target.value })}/></Field>
      </div>
      <p className="form-hint">주간 생산 목표는 대시보드에서 별도로 설정합니다. 이 목표 수량은 해당 작업지시의 양품 생산 기준입니다.</p>
      {items.error && <ErrorState error={items.error}/>} 
      {actionError && <ErrorState error={actionError}/>} 
    </Modal>}

    {assignmentIssue && <Modal
      title="설비 책임자 배정 필요"
      onClose={() => setAssignmentIssue(null)}
      footer={<>
        <button className="btn secondary" onClick={() => setAssignmentIssue(null)}>닫기</button>
        <a className="btn" href="/workers">작업자 배정으로 이동</a>
      </>}
    >
      <p>생산을 시작하려면 모든 설비에 활성 책임자가 있어야 합니다.</p>
      <p className="form-hint">{assignmentIssue.message}</p>
    </Modal>}
  </div>;
}

function validSchedule(form) {
  if (!form.plannedStartAt || !form.plannedEndAt) return false;
  return new Date(form.plannedEndAt).getTime() > new Date(form.plannedStartAt).getTime();
}

function toDateTimeInput(value) {
  return value ? String(value).slice(0, 16) : "";
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
