import React, { useState, useEffect } from "react";
import MesApi from "../api/MesApi";
import { pushNotification } from "../utils/notificationBus";

function MaterialPage() {
  const [materials, setMaterials] = useState([]);
  const [form, setForm] = useState({
    code: "",
    name: "",
    amount: "",
    type: "IN",
    reason: "",
  });
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  // ---- 관리자 여부 (실제 로그인된 역할 기준) ----
  const isAdmin = localStorage.getItem("userRole") === "admin";

  // ---- 자재 입고 모달 상태 ----
  const [showInboundModal, setShowInboundModal] = useState(false);

  // ---- 사원용 자재 요청 모달 상태 ----
  const [showRequestModal, setShowRequestModal] = useState(false);
  const [requestForm, setRequestForm] = useState({ code: "", name: "", amount: "", reason: "" });
  const [isRequestDropdownOpen, setIsRequestDropdownOpen] = useState(false);

  // ---- 상단 요약 카드 (금일 입고 / 금일 출고 / 총 재고) ----
  const [todayInbound, setTodayInbound] = useState(500);
  const [todayOutbound, setTodayOutbound] = useState(2350);

  const fetchMaterials = async () => {
    try {
      const res = await MesApi.getMaterials();
      setMaterials(res.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    fetchMaterials();
  }, []);

  const openInboundModal = () => {
    setForm({ code: "", name: "", amount: "", type: "IN", reason: "" });
    setIsDropdownOpen(false);
    setShowInboundModal(true);
  };

  const closeInboundModal = () => {
    setShowInboundModal(false);
    setIsDropdownOpen(false);
  };

  const fallbackMaterials = [
    { id: "fallback-1", code: "MAT-SCREW", name: "티타늄 나사", currentStock: 4500 },
    { id: "fallback-2", code: "MAT-PCB", name: "제어 회로 기판", currentStock: 5 },
    { id: "fallback-3", code: "MAT-CASE", name: "강화 플라스틱 케이스", currentStock: 850 },
  ];

  const displayMaterials = materials.length > 0 ? materials : fallbackMaterials;

  const totalStock = displayMaterials.reduce(
    (sum, m) => sum + (Number(m.currentStock) || 0),
    0
  );

  const handleInbound = async () => {
    if (!form.code || form.amount <= 0) return alert("입력 값을 확인하세요.");
    try {
      await MesApi.inboundMaterial({
        code: form.code,
        name: form.name,
        amount: form.amount,
        type: form.type,
        reason: form.reason,
      });
      alert(`✅ ${form.name} ${form.amount}개 수불 처리 완료`);
      fetchMaterials();
      if (form.type === "OUT") {
        setTodayOutbound((prev) => prev + Number(form.amount));
      } else {
        setTodayInbound((prev) => prev + Number(form.amount));
      }
      setForm({ code: "", name: "", amount: "", type: "IN", reason: "" });
      setShowInboundModal(false);
    } catch (e) {
      alert("처리 실패");
    }
  };

  const materialPresets = [
    { code: "MAT-SCREW", name: "티타늄 나사" },
    { code: "MAT-PCB", name: "제어 회로 기판" },
    { code: "MAT-CASE", name: "강화 플라스틱 케이스" },
  ];

  const handleCodeChange = (codeValue) => {
    const matched = materialPresets.find((item) => item.code === codeValue);
    setForm((prev) => ({
      ...prev,
      code: codeValue,
      name: matched ? matched.name : prev.name,
    }));
  };

  const openRequestModal = () => {
    setRequestForm({ code: "", name: "", amount: "", reason: "" });
    setIsRequestDropdownOpen(false);
    setShowRequestModal(true);
  };

  const closeRequestModal = () => {
    setShowRequestModal(false);
    setIsRequestDropdownOpen(false);
  };

  const handleRequestCodeChange = (codeValue) => {
    const matched = materialPresets.find((item) => item.code === codeValue);
    setRequestForm((prev) => ({
      ...prev,
      code: codeValue,
      name: matched ? matched.name : prev.name,
    }));
  };

  const handleRequestSubmit = () => {
    if (!requestForm.code || !requestForm.amount || requestForm.amount <= 0) {
      alert("자재와 요청 수량을 확인해주세요.");
      return;
    }
    // TODO: 백엔드 API 연동 (자재 요청 접수)
    console.log("자재 요청:", requestForm);

    // 관리자에게 실시간 알림 전달 (Header 알림 종에 표시됨)
    pushNotification({
      targetRole: "admin",
      type: "info",
      title: `자재 요청 · ${requestForm.name || requestForm.code}`,
      desc: `요청 수량 ${requestForm.amount}개${requestForm.reason ? ` · 사유: ${requestForm.reason}` : ""}`,
    });

    alert(`📩 ${requestForm.name} ${requestForm.amount}개 요청이 관리자에게 전달되었습니다.`);
    setRequestForm({ code: "", name: "", amount: "", reason: "" });
    setShowRequestModal(false);
  };

  // 본문 콘텐츠에만 필요한 CSS 스타일
  const contentStyles = `
    .mesdash .material-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--md); }
    .mesdash .grid-full { grid-column: span 12 / span 12; display: flex; flex-direction: column; gap: var(--md); }

    .mesdash .stat-cards-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--md); margin-bottom: var(--md); }
    .mesdash .stat-card { display: flex; align-items: center; gap: var(--md); background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 10px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
    .mesdash .stat-icon { flex-shrink: 0; width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; color: #ffffff; }
    .mesdash .stat-icon .material-symbols-outlined { font-size: 20px; }
    .mesdash .stat-icon.icon-in { background-color: var(--tertiary, #16a34a); }
    .mesdash .stat-icon.icon-out { background-color: var(--error, #dc2626); }
    .mesdash .stat-icon.icon-total { background-color: var(--primary, #02639a); }
    .mesdash .stat-body { display: flex; flex-direction: column; gap: 2px; }
    .mesdash .stat-label { font-size: 12px; font-weight: 600; color: var(--on-surface-variant); }
    .mesdash .stat-value { font-size: 20px; font-weight: 800; color: var(--on-surface); }
    .mesdash .stat-value .stat-unit { font-size: 12px; font-weight: 700; color: var(--on-surface-variant); margin-left: 3px; }

    .mesdash .card { background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 8px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
    .mesdash .card-header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--md); }
    .mesdash .card-header-row h3 { margin: 0; font-size: 20px; font-weight: 600; line-height: 28px; color: var(--on-surface); }
    .mesdash .card h3 { margin: 0 0 var(--md) 0; font-size: 20px; font-weight: 600; line-height: 28px; color: var(--on-surface); }

    .mesdash .inbound-trigger-btn { display: inline-flex; align-items: center; gap: var(--xs); padding: var(--sm) var(--md); border: none; border-radius: 6px; font-size: 13px; font-weight: 700; color: #ffffff; background-color: var(--primary); cursor: pointer; transition: opacity 0.2s; white-space: nowrap; }
    .mesdash .inbound-trigger-btn:hover { opacity: 0.9; }
    .mesdash .request-trigger-btn { display: inline-flex; align-items: center; gap: var(--xs); padding: var(--sm) var(--md); border: none; border-radius: 6px; font-size: 13px; font-weight: 700; color: #ffffff; background-color: var(--tertiary, #16a34a); cursor: pointer; transition: opacity 0.2s; white-space: nowrap; }
    .mesdash .request-trigger-btn:hover { opacity: 0.9; }

    .mesdash .switch-tab { display: flex; background-color: var(--surface-container-low); padding: var(--xs); border-radius: 6px; margin-bottom: var(--md); }
    .mesdash .switch-tab button { flex: 1; border: none; padding: var(--sm) 0; font-size: 13px; font-weight: 700; border-radius: 4px; cursor: pointer; background: transparent; color: var(--on-surface-variant); transition: all 0.2s; }
    .mesdash .switch-tab button.in-active { background-color: var(--surface-container-lowest); color: var(--primary); box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
    .mesdash .switch-tab button.out-active { background-color: var(--surface-container-lowest); color: var(--error); box-shadow: 0 1px 2px rgba(0,0,0,0.05); }

    .mesdash .form-group { display: flex; flex-direction: column; gap: var(--xs); margin-bottom: var(--md); position: relative; }
    .mesdash .form-group label { font-size: 12px; font-weight: 700; color: var(--on-surface-variant); }
    .mesdash .form-input { padding: var(--sm) var(--md); border: 1px solid var(--outline-variant); border-radius: 4px; font-size: 14px; outline: none; background-color: var(--surface-container-lowest); transition: border-color 0.2s; width: 100%; box-sizing: border-box; }
    .mesdash .form-input:focus { border-color: var(--primary); }
    .mesdash .form-input[readonly] { background-color: var(--surface-container-low); color: var(--outline); }

    .mesdash .chevron-btn { position: absolute; right: 8px; top: 28px; background: none; border: none; cursor: pointer; color: var(--outline); transition: transform 0.2s; }
    .mesdash .chevron-btn.open { transform: rotate(180deg); }
    .mesdash .dropdown-list { position: absolute; top: 100%; left: 0; width: 100%; background: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 4px; margin: var(--xs) 0 0 0; padding: 0; list-style: none; z-index: 100; box-shadow: 0 4px 6px rgba(0,0,0,0.05); max-height: 160px; overflow-y: auto; }
    .mesdash .dropdown-item { padding: var(--sm) var(--md); font-size: 14px; cursor: pointer; }
    .mesdash .dropdown-item:hover { background-color: var(--surface-container-low); }

    .mesdash .data-table { width: 100%; text-align: left; border-collapse: collapse; }
    .mesdash .data-table th { padding: var(--sm) var(--md); border-bottom: 1px solid var(--outline-variant); font-size: 11px; font-weight: 700; letter-spacing: 0.05em; color: var(--on-surface-variant); background-color: var(--surface-container-low); }
    .mesdash .data-table td { padding: var(--md); font-size: 14px; border-bottom: 1px solid rgba(197, 198, 205, 0.3); }
    .mesdash .data-table tbody tr:hover { background-color: var(--surface-container); }
    .mesdash .font-code { font-family: "JetBrains Mono", monospace; font-size: 13px; }

    .mesdash .submit-btn { width: 100%; padding: var(--md) 0; border: none; border-radius: 6px; font-size: 14px; font-weight: 700; color: #ffffff; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: var(--sm); transition: opacity 0.2s; }
    .mesdash .submit-btn:hover { opacity: 0.9; }

    .mesdash .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: var(--md); }
    .mesdash .modal-content { background-color: var(--surface-container-lowest); width: 400px; max-width: 100%; border-radius: 10px; padding: var(--lg, 24px); box-shadow: 0 10px 25px rgba(0,0,0,0.15); max-height: 88vh; overflow-y: auto; }
    .mesdash .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--md); }
    .mesdash .modal-header h3 { margin: 0; font-size: 17px; font-weight: 700; color: var(--on-surface); }
    .mesdash .btn-close { background: none; border: none; font-size: 18px; cursor: pointer; color: var(--on-surface-variant); line-height: 1; }
  `;

  return (
    <>
      <style>{contentStyles}</style>

      {isAdmin && (
        <div className="stat-cards-row">
          <div className="stat-card">
            <div className="stat-icon icon-in">
              <span className="material-symbols-outlined">move_to_inbox</span>
            </div>
            <div className="stat-body">
              <span className="stat-label">금일 입고</span>
              <span className="stat-value">
                {todayInbound.toLocaleString()}
                <span className="stat-unit">건</span>
              </span>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon icon-out">
              <span className="material-symbols-outlined">outbox</span>
            </div>
            <div className="stat-body">
              <span className="stat-label">금일 출고</span>
              <span className="stat-value">
                {todayOutbound.toLocaleString()}
                <span className="stat-unit">건</span>
              </span>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon icon-total">
              <span className="material-symbols-outlined">layers</span>
            </div>
            <div className="stat-body">
              <span className="stat-label">총 재고</span>
              <span className="stat-value">
                {totalStock.toLocaleString()}
                <span className="stat-unit">EA</span>
              </span>
            </div>
          </div>
        </div>
      )}

      <div className="material-grid">
        {/* 자재 재고 현황표 */}
        <div className="grid-full">
          <div className="card">
            <div className="card-header-row">
              <h3>📦 자재 재고 현황표</h3>
              {isAdmin ? (
                <button type="button" className="inbound-trigger-btn" onClick={openInboundModal}>
                  <span className="material-symbols-outlined" style={{ fontSize: "18px" }}>add_box</span>
                  <span>자재 입고</span>
                </button>
              ) : (
                <button type="button" className="request-trigger-btn" onClick={openRequestModal}>
                  <span className="material-symbols-outlined" style={{ fontSize: "18px" }}>send</span>
                  <span>자재 요청</span>
                </button>
              )}
            </div>
            <table className="data-table">
              <thead>
                <tr>
                  <th>자재 코드</th>
                  <th>자재명</th>
                  <th style={{ textAlign: "right" }}>현재 재고</th>
                </tr>
              </thead>
              <tbody>
                {displayMaterials.map((m) => (
                  <tr key={m.id}>
                    <td className="font-code text-primary">{m.code}</td>
                    <td style={{ fontWeight: "600" }}>{m.name}</td>
                    <td
                      style={{
                        textAlign: "right",
                        fontWeight: "bold",
                        color:
                          m.currentStock < 10
                            ? "var(--error)"
                            : "var(--tertiary)",
                      }}
                    >
                      {Number(m.currentStock).toLocaleString()} ea
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* 자재 입고/출고 모달 (관리자 전용 버튼으로만 열림) */}
      {isAdmin && showInboundModal && (
        <div className="modal-overlay" onClick={closeInboundModal}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>빠른 재고 수불 관리</h3>
              <button type="button" className="btn-close" onClick={closeInboundModal}>✕</button>
            </div>

            <div className="switch-tab">
              <button
                type="button"
                className={form.type === "IN" ? "in-active" : ""}
                onClick={() => setForm({ ...form, type: "IN" })}
              >
                입고 (In)
              </button>
              <button
                type="button"
                className={form.type === "OUT" ? "out-active" : ""}
                onClick={() => setForm({ ...form, type: "OUT" })}
              >
                출고 (Out)
              </button>
            </div>

            <div className="form-group">
              <label>자재 코드</label>
              <input
                type="text"
                className="form-input"
                placeholder="자재코드 입력 (예: MAT-SCREW)"
                value={form.code}
                onChange={(e) => handleCodeChange(e.target.value)}
              />
              <button
                type="button"
                className={`material-symbols-outlined chevron-btn ${isDropdownOpen ? "open" : ""}`}
                onClick={() => setIsDropdownOpen(!isDropdownOpen)}
              >
                expand_more
              </button>
              {isDropdownOpen && (
                <ul className="dropdown-list custom-scrollbar">
                  {materialPresets.map((item) => (
                    <li
                      key={item.code}
                      className="dropdown-item"
                      onClick={() => {
                        setForm({ ...form, code: item.code, name: item.name });
                        setIsDropdownOpen(false);
                      }}
                    >
                      {item.code} ({item.name})
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="form-group">
              <label>자재명</label>
              <input
                type="text"
                className="form-input"
                value={form.name}
                placeholder="자동 매핑 자재명"
                readOnly
              />
            </div>
            <div className="form-group">
              <label>수량 (ea)</label>
              <input
                type="number"
                className="form-input"
                placeholder="0"
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>처리 사유</label>
              <input
                type="text"
                className="form-input"
                placeholder="사유를 입력하세요"
                value={form.reason}
                onChange={(e) => setForm({ ...form, reason: e.target.value })}
              />
            </div>

            <button
              type="button"
              className="submit-btn"
              style={{
                backgroundColor:
                  form.type === "OUT" ? "var(--error)" : "var(--primary)",
              }}
              onClick={handleInbound}
            >
              <span className="material-symbols-outlined">save</span>
              <span>업데이트 저장</span>
            </button>
          </div>
        </div>
      )}

      {/* 자재 요청 모달 (사원 전용 버튼으로만 열림) */}
      {!isAdmin && showRequestModal && (
        <div className="modal-overlay" onClick={closeRequestModal}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>자재 요청</h3>
              <button type="button" className="btn-close" onClick={closeRequestModal}>✕</button>
            </div>

            <div className="form-group">
              <label>자재 코드</label>
              <input
                type="text"
                className="form-input"
                placeholder="자재코드 입력 (예: MAT-SCREW)"
                value={requestForm.code}
                onChange={(e) => handleRequestCodeChange(e.target.value)}
              />
              <button
                type="button"
                className={`material-symbols-outlined chevron-btn ${isRequestDropdownOpen ? "open" : ""}`}
                onClick={() => setIsRequestDropdownOpen(!isRequestDropdownOpen)}
              >
                expand_more
              </button>
              {isRequestDropdownOpen && (
                <ul className="dropdown-list custom-scrollbar">
                  {materialPresets.map((item) => (
                    <li
                      key={item.code}
                      className="dropdown-item"
                      onClick={() => {
                        setRequestForm({ ...requestForm, code: item.code, name: item.name });
                        setIsRequestDropdownOpen(false);
                      }}
                    >
                      {item.code} ({item.name})
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="form-group">
              <label>자재명</label>
              <input
                type="text"
                className="form-input"
                value={requestForm.name}
                placeholder="자동 매핑 자재명"
                readOnly
              />
            </div>
            <div className="form-group">
              <label>요청 수량 (ea)</label>
              <input
                type="number"
                className="form-input"
                placeholder="0"
                value={requestForm.amount}
                onChange={(e) => setRequestForm({ ...requestForm, amount: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>요청 사유</label>
              <input
                type="text"
                className="form-input"
                placeholder="사유를 입력하세요 (예: 재고 부족, 파손 등)"
                value={requestForm.reason}
                onChange={(e) => setRequestForm({ ...requestForm, reason: e.target.value })}
              />
            </div>

            <button
              type="button"
              className="submit-btn"
              style={{ backgroundColor: "var(--tertiary, #16a34a)" }}
              onClick={handleRequestSubmit}
            >
              <span className="material-symbols-outlined">send</span>
              <span>요청 보내기</span>
            </button>
          </div>
        </div>
      )}
    </>
  );
}

export default MaterialPage;