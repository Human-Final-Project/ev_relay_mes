import React, { useState, useEffect } from "react";
import MesApi from "../api/MesApi";

const products = [
  { code: "EV-RELAY-001", label: "EV-RELAY-001 (Standard)" },
  { code: "EV-RELAY-PRO", label: "EV-RELAY-PRO (High-End)" },
  { code: "BATTERY-PACK", label: "BATTERY-PACK (Module)" },
];

function WorkOrderPage() {
  const [orders, setOrders] = useState([]);

  // 모달 제어 상태 관리
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [productCode, setProductCode] = useState("EV-RELAY-001");
  const [targetQty, setTargetQty] = useState("");
  const [isOrderDropdownOpen, setIsOrderDropdownOpen] = useState(false);

  const selectedProductLabel =
    products.find((p) => p.code === productCode)?.label || "";

  const fetchOrders = async () => {
    try {
      const res = await MesApi.getOrders();
      setOrders(res.data || []);
    } catch (e) {
      console.error("생산 오더 목록 로드 실패", e);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  const handleCreateOrder = async () => {
    if (targetQty <= 0) return alert("수량을 확인하세요.");
    try {
      const response = await MesApi.createOrder(productCode, targetQty);
      if (response.data.status === "WAITING") {
        alert("주문 접수 성공! 현재 대기 중입니다.");
        setIsModalOpen(false);
        setTargetQty("");
        setProductCode("EV-RELAY-001");
        fetchOrders();
      }
    } catch (e) {
      alert("서버 통신 오류!!!");
    }
  };

  const styles = `
    .mesdash .card { background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 8px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
    .mesdash .card h3 { margin: 0; font-size: 20px; font-weight: 600; line-height: 28px; color: var(--on-surface); }
    .mesdash .card-header-flex { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--md); }
    
    /* 새 생산지시 버튼 스타일 */
    .mesdash .action-btn { display: flex; align-items: center; gap: var(--xs); padding: var(--sm) var(--md); background-color: var(--primary); color: #fff; border: none; border-radius: 4px; font-weight: 700; cursor: pointer; }
    .mesdash .action-btn:hover { opacity: 0.9; }

    .mesdash .data-table { width: 100%; text-align: left; border-collapse: collapse; }
    .mesdash .data-table th { padding: var(--sm) var(--md); border-bottom: 1px solid var(--outline-variant); font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); background-color: var(--surface-container-low); }
    .mesdash .data-table td { padding: var(--md); font-size: 14px; border-bottom: 1px solid rgba(197, 198, 205, 0.3); }
    .mesdash .font-code { font-family: "JetBrains Mono", monospace; font-size: 13px; }
    
    /* 진행 배지 규칙 */
    .mesdash .status-badge { padding: var(--xs) var(--sm); border-radius: 4px; font-size: 12px; font-weight: bold; }
    .mesdash .status-badge.waiting { background-color: var(--primary-container); color: var(--primary); }
    .mesdash .status-badge.completed { background-color: #d1e7dd; color: var(--tertiary); }

    /* 모달 윈도우 스타일 */
    .mesdash .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 200; }
    .mesdash .modal-window { background: #fff; padding: var(--lg); border-radius: 8px; width: 400px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
    .mesdash .form-group { display: flex; flex-direction: column; gap: var(--xs); margin-bottom: var(--md); position: relative; }
    .mesdash .form-group label { font-size: 12px; font-weight: 700; color: var(--on-surface-variant); }
    .mesdash .form-input { padding: var(--sm) var(--md); border: 1px solid var(--outline-variant); border-radius: 4px; font-size: 14px; outline: none; width: 100%; box-sizing: border-box; }
    .mesdash .dropdown-list { position: absolute; top: 100%; left: 0; width: 100%; background: #fff; border: 1px solid var(--outline-variant); border-radius: 4px; list-style: none; padding: 0; margin: 4px 0 0 0; z-index: 210; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
    .mesdash .dropdown-item { padding: var(--sm) var(--md); cursor: pointer; font-size: 14px; }
    .mesdash .dropdown-item:hover { background-color: var(--surface-container-low); }
    .mesdash .modal-actions { display: flex; justify-content: flex-end; gap: var(--sm); margin-top: var(--lg); }
    .mesdash .btn-cancel { padding: var(--sm) var(--md); background: #eee; border: none; border-radius: 4px; cursor: pointer; }
  `;

  return (
    <>
      <style>{styles}</style>

      {/* 본문 콘텐츠만 단독 배치 */}
      <div className="card">
        <div className="card-header-flex">
          <h3>⚙️ 활성 생산 오더 관리</h3>
          <button
            type="button"
            className="action-btn"
            onClick={() => setIsModalOpen(true)}
          >
            <span className="material-symbols-outlined">add</span>새 생산 지시
          </button>
        </div>

        <table className="data-table">
          <thead>
            <tr>
              <th>LOT ID</th>
              <th>생산 제품</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id || order.lotId}>
                <td className="font-code text-primary">
                  {order.lotId ||
                    (() => {
                      const now = new Date();
                      const yy = String(now.getFullYear()).slice(-2); // "26"
                      const mm = String(now.getMonth() + 1).padStart(2, "0"); // "07"
                      const dd = String(now.getDate()).padStart(2, "0"); // "13"
                      return `LOT-${yy}${mm}${dd}-${order.id}`; // LOT-260713-1 구조 생성
                    })()}
                </td>
                <td>
                  <span
                    className={`status-badge ${order.status === "COMPLETED" ? "completed" : "waiting"}`}
                  >
                    {order.status === "COMPLETED" ? "완료" : "진행 중"}
                  </span>
                </td>
              </tr>
            ))}
            {orders.length === 0 && (
              <tr>
                <td
                  colSpan="3"
                  style={{
                    textAlign: "center",
                    padding: "24px",
                    color: "var(--outline)",
                  }}
                >
                  가동 중인 생산 오더가 없습니다. 새 생산 지시를 추가하세요.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* 생산 요청 팝업 오버레이 모달 */}
      {isModalOpen && (
        <div className="modal-overlay">
          <div className="modal-window">
            <h3>신규 생산 작업 지시</h3>
            <div className="form-group">
              <label>생산 제품 선택</label>
              <input
                type="text"
                className="form-input"
                readOnly
                value={selectedProductLabel}
                onClick={() => setIsOrderDropdownOpen(!isOrderDropdownOpen)}
                style={{ cursor: "pointer" }}
              />
              {isOrderDropdownOpen && (
                <ul className="dropdown-list">
                  {products.map((p) => (
                    <li
                      key={p.code}
                      className="dropdown-item"
                      onClick={() => {
                        setProductCode(p.code);
                        setIsOrderDropdownOpen(false);
                      }}
                    >
                      {p.label}
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div className="form-group">
              <label>목표 생산 수량</label>
              <input
                type="number"
                className="form-input"
                placeholder="수량을 입력하세요"
                value={targetQty}
                onChange={(e) => setTargetQty(e.target.value)}
              />
            </div>
            <div className="modal-actions">
              <button
                type="button"
                className="btn-cancel"
                onClick={() => setIsModalOpen(false)}
              >
                취소
              </button>
              <button
                type="button"
                className="action-btn"
                onClick={handleCreateOrder}
              >
                전송 🚀
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default WorkOrderPage;
