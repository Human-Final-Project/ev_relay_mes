import React, { useState, useEffect } from "react";
import MesApi from "../api/MesApi";

// -------------------------------------------------------------
// [데이터 정의]
// -------------------------------------------------------------
const PRODUCT_OPTIONS = [
  "EV 릴레이 모델 A",
  "EV 릴레이 모델 B",
  "EV 릴레이 모델 P",
  "EV 릴레이 모델 S",
];

const LINE_OPTIONS = ["1라인", "2라인", "3라인"];
const PRIORITY_OPTIONS = ["높음", "보통", "낮음"];
const STATUS_OPTIONS = ["대기", "진행중", "완료", "보류"];

const STATUS_CLASS = {
  "대기": "status-pending",
  "진행중": "status-progress",
  "완료": "status-done",
  "보류": "status-hold",
};

const PRIORITY_CLASS = {
  "높음": "priority-high",
  "보통": "priority-mid",
  "낮음": "priority-low",
};

const fallbackWorkOrders = [
  {
    id: "wo-1",
    orderNo: "WO-20260716-01",
    product: "EV 릴레이 모델 A",
    qty: 500,
    line: "1라인",
    dueDate: "2026-07-20",
    priority: "높음",
    status: "진행중",
    note: "긴급 납품 건",
  },
  {
    id: "wo-2",
    orderNo: "WO-20260716-02",
    product: "EV 릴레이 모델 B",
    qty: 300,
    line: "2라인",
    dueDate: "2026-07-22",
    priority: "보통",
    status: "대기",
    note: "",
  },
  {
    id: "wo-3",
    orderNo: "WO-20260715-05",
    product: "EV 릴레이 모델 P",
    qty: 50,
    line: "3라인",
    dueDate: "2026-07-18",
    priority: "높음",
    status: "완료",
    note: "프로토타입 테스트 물량",
  },
  {
    id: "wo-4",
    orderNo: "WO-20260714-03",
    product: "EV 릴레이 모델 S",
    qty: 120,
    line: "1라인",
    dueDate: "2026-07-25",
    priority: "낮음",
    status: "보류",
    note: "단종 검토로 보류",
  },
];

const emptyForm = {
  product: PRODUCT_OPTIONS[0],
  qty: "",
  line: LINE_OPTIONS[0],
  dueDate: "",
  priority: "보통",
  note: "",
};

// -------------------------------------------------------------
// [CSS 스타일 정의]
// -------------------------------------------------------------
const contentStyles = `
  .mesdash .wo-denied { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--sm); background-color: var(--surface-container-lowest); border: 1px dashed var(--outline-variant); border-radius: 10px; padding: 60px var(--md); text-align: center; }
  .mesdash .wo-denied .material-symbols-outlined { font-size: 40px; color: var(--outline); }
  .mesdash .wo-denied h3 { margin: 0; font-size: 16px; color: var(--on-surface); }
  .mesdash .wo-denied p { margin: 0; font-size: 13px; color: var(--on-surface-variant); }

  .mesdash .wo-stats-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--md); margin-bottom: var(--md); }
  .mesdash .stat-card { display: flex; align-items: center; gap: var(--md); background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 10px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
  .mesdash .stat-icon { flex-shrink: 0; width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; color: #ffffff; }
  .mesdash .stat-icon .material-symbols-outlined { font-size: 20px; }
  .mesdash .stat-icon.icon-pending { background-color: var(--outline, #64748b); }
  .mesdash .stat-icon.icon-progress { background-color: var(--primary, #02639a); }
  .mesdash .stat-icon.icon-done { background-color: var(--tertiary, #16a34a); }
  .mesdash .stat-icon.icon-hold { background-color: var(--error, #dc2626); }
  .mesdash .stat-body { display: flex; flex-direction: column; gap: 2px; }
  .mesdash .stat-label { font-size: 12px; font-weight: 600; color: var(--on-surface-variant); }
  .mesdash .stat-value { font-size: 20px; font-weight: 800; color: var(--on-surface); }
  .mesdash .stat-value .stat-unit { font-size: 12px; font-weight: 700; color: var(--on-surface-variant); margin-left: 3px; }

  .mesdash .card { background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 8px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
  .mesdash .card-header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--md); gap: var(--md); flex-wrap: wrap; }
  .mesdash .card-header-row h3 { margin: 0; font-size: 20px; font-weight: 600; line-height: 28px; color: var(--on-surface); }
  .mesdash .card-header-actions { display: flex; align-items: center; gap: var(--sm); }

  .mesdash .wo-search { padding: 8px 12px; border: 1px solid var(--outline-variant); border-radius: 6px; font-size: 13px; outline: none; background-color: var(--surface-container-lowest); min-width: 200px; }
  .mesdash .wo-search:focus { border-color: var(--primary); }

  .mesdash .btn-add { display: inline-flex; align-items: center; gap: var(--xs); padding: var(--sm) var(--md); border: none; border-radius: 6px; font-size: 13px; font-weight: 700; color: #ffffff; background-color: var(--primary); cursor: pointer; transition: opacity 0.2s; white-space: nowrap; }
  .mesdash .btn-add:hover { opacity: 0.9; }

  .mesdash .data-table { width: 100%; text-align: left; border-collapse: collapse; }
  .mesdash .data-table th { padding: var(--sm) var(--md); border-bottom: 1px solid var(--outline-variant); font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); background-color: var(--surface-container-low); white-space: nowrap; }
  .mesdash .data-table td { padding: var(--md); font-size: 14px; border-bottom: 1px solid rgba(197, 198, 205, 0.3); vertical-align: middle; }
  .mesdash .data-table tbody tr:hover { background-color: var(--surface-container); }
  .mesdash .font-code { font-family: "JetBrains Mono", monospace; font-size: 13px; }

  .mesdash .badge { display: inline-flex; align-items: center; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 999px; white-space: nowrap; }
  .mesdash .status-pending { background: #f1f5f9; color: #64748b; }
  .mesdash .status-progress { background: #e0f2fe; color: #0369a1; }
  .mesdash .status-done { background: #dcfce7; color: #16803d; }
  .mesdash .status-hold { background: #fee2e2; color: #b91c1c; }
  .mesdash .priority-high { background: #fee2e2; color: #b91c1c; }
  .mesdash .priority-mid { background: #fef9c3; color: #a16207; }
  .mesdash .priority-low { background: #f1f5f9; color: #64748b; }

  .mesdash .status-select { border: 1px solid var(--outline-variant); border-radius: 6px; padding: 5px 8px; font-size: 12px; font-weight: 700; background-color: var(--surface-container-lowest); cursor: pointer; outline: none; }
  .mesdash .status-select:focus { border-color: var(--primary); }

  .mesdash .row-actions { display: flex; align-items: center; gap: 8px; }
  .mesdash .btn-del { background: none; border: none; color: var(--outline); font-size: 15px; cursor: pointer; padding: 0 2px; }
  .mesdash .btn-del:hover { color: var(--error); }

  .mesdash .empty-row { text-align: center; padding: 30px; color: var(--outline); }

  .mesdash .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: var(--md); }
  .mesdash .modal-content { background-color: var(--surface-container-lowest); width: 460px; max-width: 100%; border-radius: 10px; padding: 24px; box-shadow: 0 10px 25px rgba(0,0,0,0.15); max-height: 88vh; overflow-y: auto; }
  .mesdash .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--md); }
  .mesdash .modal-header h3 { margin: 0; font-size: 17px; font-weight: 700; color: var(--on-surface); }
  .mesdash .btn-close { background: none; border: none; font-size: 18px; cursor: pointer; color: var(--on-surface-variant); line-height: 1; }

  .mesdash .form-row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .mesdash .form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
  .mesdash .form-group label { font-size: 12px; font-weight: 700; color: var(--on-surface-variant); }
  .mesdash .form-group input, .mesdash .form-group select, .mesdash .form-group textarea { padding: 10px; border: 1px solid var(--outline-variant); border-radius: 8px; outline: none; font-size: 13px; background: var(--surface-container-lowest); font-family: inherit; }
  .mesdash .form-group input:focus, .mesdash .form-group select:focus, .mesdash .form-group textarea:focus { border-color: var(--primary); }

  .mesdash .submit-btn { width: 100%; padding: 12px; background: var(--primary); color: white; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; transition: 0.2s; font-size: 13px; }
  .mesdash .submit-btn:hover { opacity: 0.9; }
`;

function WorkOrderPage() {
  const [isAdmin] = useState(true);

  const [workOrders, setWorkOrders] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");

  const [showAddModal, setShowAddModal] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const fetchWorkOrders = async () => {
    try {
      const res = await MesApi.getWorkOrders();
      setWorkOrders(res.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    if (isAdmin) fetchWorkOrders();
  }, [isAdmin]);

  const displayOrders = workOrders.length > 0 ? workOrders : fallbackWorkOrders;

  const filteredOrders = displayOrders.filter(
    (o) =>
      o.product.toLowerCase().includes(searchTerm.trim().toLowerCase()) ||
      o.orderNo.toLowerCase().includes(searchTerm.trim().toLowerCase())
  );

  const countByStatus = (status) =>
    displayOrders.filter((o) => o.status === status).length;

  const generateOrderNo = () => {
    const today = new Date();
    const y = today.getFullYear();
    const m = String(today.getMonth() + 1).padStart(2, "0");
    const d = String(today.getDate()).padStart(2, "0");
    const prefix = `WO-${y}${m}${d}`;
    const todayCount = displayOrders.filter((o) => o.orderNo.startsWith(prefix)).length;
    return `${prefix}-${String(todayCount + 1).padStart(2, "0")}`;
  };

  const openAddModal = () => {
    setForm(emptyForm);
    setShowAddModal(true);
  };

  const closeAddModal = () => setShowAddModal(false);

  const handleFormChange = (e) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isAdmin) return;
    if (!form.qty || Number(form.qty) <= 0 || !form.dueDate) {
      alert("지시수량과 마감일을 확인하세요.");
      return;
    }

    const newOrder = {
      id: `wo-${Date.now()}`,
      orderNo: generateOrderNo(),
      product: form.product,
      qty: Number(form.qty),
      line: form.line,
      dueDate: form.dueDate,
      priority: form.priority,
      status: "대기",
      note: form.note,
    };

    try {
      await MesApi.createWorkOrder(newOrder);
    } catch (e) {
      // API가 아직 없거나 실패해도 화면에는 반영해 흐름을 확인할 수 있게 처리
      console.error(e);
    }

    setWorkOrders((prev) => [newOrder, ...(prev.length > 0 ? prev : fallbackWorkOrders)]);
    setShowAddModal(false);
  };

  const handleStatusChange = async (orderId, newStatus) => {
    if (!isAdmin) return;
    const base = workOrders.length > 0 ? workOrders : fallbackWorkOrders;
    const updated = base.map((o) => (o.id === orderId ? { ...o, status: newStatus } : o));
    setWorkOrders(updated);
    try {
      await MesApi.updateWorkOrderStatus(orderId, newStatus);
    } catch (e) {
      console.error(e);
    }
  };

  const handleDelete = async (orderId, orderNo) => {
    if (!isAdmin) return;
    if (!window.confirm(`${orderNo} 작업지시를 삭제하시겠습니까?`)) return;
    const base = workOrders.length > 0 ? workOrders : fallbackWorkOrders;
    setWorkOrders(base.filter((o) => o.id !== orderId));
    try {
      await MesApi.deleteWorkOrder(orderId);
    } catch (e) {
      console.error(e);
    }
  };

  // ---- 관리자가 아니면 페이지 전체를 막는다 ----
  if (!isAdmin) {
    return (
      <div className="mesdash">
        <style>{contentStyles}</style>
        <div className="wo-denied">
          <span className="material-symbols-outlined">lock</span>
          <h3>접근 권한이 없습니다</h3>
          <p>작업지시 페이지는 관리자만 열람할 수 있습니다.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="mesdash">
      <style>{contentStyles}</style>

      <div className="wo-stats-row">
        <div className="stat-card">
          <div className="stat-icon icon-pending">
            <span className="material-symbols-outlined">schedule</span>
          </div>
          <div className="stat-body">
            <span className="stat-label">대기</span>
            <span className="stat-value">
              {countByStatus("대기")}
              <span className="stat-unit">건</span>
            </span>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon icon-progress">
            <span className="material-symbols-outlined">autorenew</span>
          </div>
          <div className="stat-body">
            <span className="stat-label">진행중</span>
            <span className="stat-value">
              {countByStatus("진행중")}
              <span className="stat-unit">건</span>
            </span>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon icon-done">
            <span className="material-symbols-outlined">task_alt</span>
          </div>
          <div className="stat-body">
            <span className="stat-label">완료</span>
            <span className="stat-value">
              {countByStatus("완료")}
              <span className="stat-unit">건</span>
            </span>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon icon-hold">
            <span className="material-symbols-outlined">pause_circle</span>
          </div>
          <div className="stat-body">
            <span className="stat-label">보류</span>
            <span className="stat-value">
              {countByStatus("보류")}
              <span className="stat-unit">건</span>
            </span>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header-row">
          <h3>🛠 작업지시 목록</h3>
          <div className="card-header-actions">
            <input
              type="text"
              className="wo-search"
              placeholder="지시번호 또는 제품명 검색..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
            <button type="button" className="btn-add" onClick={openAddModal}>
              <span className="material-symbols-outlined" style={{ fontSize: "18px" }}>add</span>
              <span>작업지시 등록</span>
            </button>
          </div>
        </div>

        <table className="data-table">
          <thead>
            <tr>
              <th>지시번호</th>
              <th>제품</th>
              <th style={{ textAlign: "right" }}>지시수량</th>
              <th>생산라인</th>
              <th>마감일</th>
              <th>우선순위</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {filteredOrders.map((o) => (
              <tr key={o.id}>
                <td className="font-code">{o.orderNo}</td>
                <td style={{ fontWeight: 600 }}>{o.product}</td>
                <td style={{ textAlign: "right" }}>{o.qty.toLocaleString()} ea</td>
                <td>{o.line}</td>
                <td>{o.dueDate}</td>
                <td>
                  <span className={`badge ${PRIORITY_CLASS[o.priority]}`}>{o.priority}</span>
                </td>
                <td>
                  <select
                    className="status-select"
                    value={o.status}
                    onChange={(e) => handleStatusChange(o.id, e.target.value)}
                  >
                    {STATUS_OPTIONS.map((s) => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <div className="row-actions">
                    <button
                      type="button"
                      className="btn-del"
                      title="삭제"
                      onClick={() => handleDelete(o.id, o.orderNo)}
                    >
                      ✕
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {filteredOrders.length === 0 && (
              <tr>
                <td colSpan="8" className="empty-row">등록된 작업지시가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* 작업지시 등록 모달 */}
      {showAddModal && (
        <div className="modal-overlay" onClick={closeAddModal}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>작업지시 등록</h3>
              <button type="button" className="btn-close" onClick={closeAddModal}>✕</button>
            </div>
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>제품</label>
                <select name="product" value={form.product} onChange={handleFormChange}>
                  {PRODUCT_OPTIONS.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </div>

              <div className="form-row2">
                <div className="form-group">
                  <label>지시수량 (ea)</label>
                  <input
                    type="number"
                    name="qty"
                    placeholder="예: 500"
                    value={form.qty}
                    onChange={handleFormChange}
                  />
                </div>
                <div className="form-group">
                  <label>생산라인</label>
                  <select name="line" value={form.line} onChange={handleFormChange}>
                    {LINE_OPTIONS.map((l) => <option key={l} value={l}>{l}</option>)}
                  </select>
                </div>
              </div>

              <div className="form-row2">
                <div className="form-group">
                  <label>마감일</label>
                  <input
                    type="date"
                    name="dueDate"
                    value={form.dueDate}
                    onChange={handleFormChange}
                  />
                </div>
                <div className="form-group">
                  <label>우선순위</label>
                  <select name="priority" value={form.priority} onChange={handleFormChange}>
                    {PRIORITY_OPTIONS.map((p) => <option key={p} value={p}>{p}</option>)}
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label>비고</label>
                <textarea
                  name="note"
                  rows="2"
                  placeholder="특이사항을 입력하세요 (선택)"
                  value={form.note}
                  onChange={handleFormChange}
                />
              </div>

              <button type="submit" className="submit-btn">작업지시 등록하기</button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default WorkOrderPage;