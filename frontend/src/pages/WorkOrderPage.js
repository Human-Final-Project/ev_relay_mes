import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";

const emptyOrder = { itemCode:"", targetQty:"", plannedStartAt:"", plannedEndAt:"" };

export default function WorkOrderPage({ currentUser }) {
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [form, setForm] = useState(null);
  const [lotOrder, setLotOrder] = useState(null);
  const [lotQty, setLotQty] = useState("");
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState(null);
  const result = useApiData(() => MesApi.getWorkOrders({ status }), [status]);
  const items = useApiData(MesApi.getItems, []);
  const orders = (result.data || []).filter(o => `${o.orderNo} ${o.itemCode} ${o.itemName}`.toLowerCase().includes(search.toLowerCase()));
  const products = (items.data || []).filter(item => item.itemType === "FG" && (item.useYn === "Y" || item.itemCode === form?.itemCode));
  const canManage = ["ADMIN", "MANAGER"].includes(currentUser?.role);
  const run = async (action) => { setSaving(true); setActionError(null); try { await action(); await result.reload(); return true; } catch(e) { setActionError(e); return false; } finally { setSaving(false); } };
  const submit = async () => {
    const payload = { ...form, targetQty:Number(form.targetQty), plannedStartAt:form.plannedStartAt || null, plannedEndAt:form.plannedEndAt || null };
    if (await run(() => form.workOrderId ? MesApi.updateWorkOrder(form.workOrderId,payload) : MesApi.createWorkOrder(payload))) setForm(null);
  };
  const createLot = async () => { if(await run(()=>MesApi.createLot(lotOrder.workOrderId,lotQty))) { setLotOrder(null); setLotQty(""); } };
  return <div className="mes-page">
    <PageHeader title="작업지시" description="작업지시를 확정하고 최초·보충 LOT를 생성합니다." actions={canManage&&<button className="btn" disabled={items.loading} onClick={()=>setForm({...emptyOrder})}>작업지시 생성</button>} />
    <div className="mes-card mes-filter"><Field label="상태"><select value={status} onChange={e=>setStatus(e.target.value)}><option value="">전체</option>{["CREATED","RELEASED","RUNNING","COMPLETED","CANCELED"].map(v=><option key={v}>{v}</option>)}</select></Field><Field label="검색"><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="작업지시·품목"/></Field><button className="btn secondary" onClick={result.reload}>새로고침</button></div>
    {actionError && <ErrorState error={actionError}/>} {result.loading ? <LoadingState/> : result.error ? <ErrorState error={result.error} onRetry={result.reload}/> : orders.length===0 ? <EmptyState/> :
    <div className="mes-table-wrap"><table className="mes-table"><thead><tr><th>작업지시</th><th>품목</th><th>목표</th><th>완료 OK</th><th>잔여</th><th>상태</th><th>계획 기간</th><th>작업</th></tr></thead><tbody>{orders.map(o=><tr key={o.workOrderId}><td><strong>{o.orderNo}</strong><br/><span className="mono">#{o.workOrderId}</span></td><td>{o.itemName}<br/><span className="mono">{o.itemCode}</span></td><td>{o.targetQty}</td><td>{o.completedOkQty}</td><td>{o.remainingQty}</td><td><StatusBadge value={o.status}/></td><td>{formatDate(o.plannedStartAt)}<br/>{formatDate(o.plannedEndAt)}</td><td>{canManage&&<div className="mes-actions">
      {o.status==="CREATED" && <button className="btn small" disabled={saving} onClick={()=>run(()=>MesApi.updateWorkOrderStatus(o.workOrderId,"RELEASED"))}>확정</button>}
      {o.status==="RELEASED" && <button className="btn small" onClick={()=>{setLotOrder(o);setLotQty(o.targetQty)}}>LOT 생성</button>}
      {o.status==="RUNNING" && o.supplementRequired && <button className="btn small" onClick={()=>run(()=>MesApi.createSupplementLot(o.workOrderId))}>보충 LOT</button>}
      {["CREATED","RELEASED"].includes(o.status) && <button className="btn small secondary" onClick={()=>setForm({...o,plannedStartAt:toInput(o.plannedStartAt),plannedEndAt:toInput(o.plannedEndAt)})}>수정</button>}
      {o.status==="CREATED" && <button className="btn small danger" onClick={()=>window.confirm(`${o.orderNo}를 삭제할까요?`)&&run(()=>MesApi.deleteWorkOrder(o.workOrderId))}>삭제</button>}
    </div>}</td></tr>)}</tbody></table></div>}
    {form && <Modal title={form.workOrderId?"작업지시 수정":"작업지시 생성"} onClose={()=>setForm(null)} footer={<><button className="btn secondary" onClick={()=>setForm(null)}>취소</button><button className="btn" disabled={saving||!form.itemCode||Number(form.targetQty)<=0} onClick={submit}>저장</button></>}><div className="mes-form-grid"><Field label="제품(코드)"><select value={form.itemCode} onChange={e=>setForm({...form,itemCode:e.target.value})}><option value="">제품 선택</option>{products.map(item=><option key={item.itemCode} value={item.itemCode}>{item.itemName} ({item.itemCode})</option>)}</select></Field><Field label="목표 수량"><input type="number" min="1" value={form.targetQty} onChange={e=>setForm({...form,targetQty:e.target.value})}/></Field><Field label="계획 시작"><input type="datetime-local" value={form.plannedStartAt||""} onChange={e=>setForm({...form,plannedStartAt:e.target.value})}/></Field><Field label="계획 종료"><input type="datetime-local" value={form.plannedEndAt||""} onChange={e=>setForm({...form,plannedEndAt:e.target.value})}/></Field></div>{items.error&&<ErrorState error={items.error}/>} {actionError&&<ErrorState error={actionError}/>}</Modal>}
    {lotOrder && <Modal title="최초 LOT 생성" onClose={()=>setLotOrder(null)} footer={<><button className="btn secondary" onClick={()=>setLotOrder(null)}>취소</button><button className="btn" disabled={saving||Number(lotQty)<=0} onClick={createLot}>생성</button></>}><p><strong>{lotOrder.orderNo}</strong> · 최초 LOT 투입수량은 작업지시 목표수량과 같아야 합니다.</p><Field label="투입 수량"><input type="number" value={lotQty} onChange={e=>setLotQty(e.target.value)}/></Field>{actionError&&<ErrorState error={actionError}/>}</Modal>}
  </div>;
}

function toInput(value){ return value ? value.slice(0,16) : ""; }
