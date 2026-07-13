import React, { useState, useEffect } from "react";
import MesApi from "../api/MesApi";

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
      setForm({ code: "", name: "", amount: "", type: "IN", reason: "" });
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

  // 본문 콘텐츠에만 필요한 CSS 스타일
  const contentStyles = `
    .mesdash .material-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--md); }
    .mesdash .grid-left { grid-column: span 8 / span 8; display: flex; flex-direction: column; gap: var(--md); }
    .mesdash .grid-right { grid-column: span 4 / span 4; display: flex; flex-direction: column; gap: var(--md); }
    
    .mesdash .card { background-color: var(--surface-container-lowest); border: 1px solid var(--outline-variant); border-radius: 8px; padding: var(--md); box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
    .mesdash .card h3 { margin: 0 0 var(--md) 0; font-size: 20px; font-weight: 600; line-height: 28px; color: var(--on-surface); }
    
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
  `;

  return (
    <>
      <style>{contentStyles}</style>
      <div className="material-grid">
        {/* 왼쪽: 재고 현황표 */}
        <div className="grid-left">
          <div className="card">
            <h3>📦 자재 재고 현황표</h3>
            <table className="data-table">
              <thead>
                <tr>
                  <th>자재 코드</th>
                  <th>자재명</th>
                  <th style={{ textAlign: "right" }}>현재 재고</th>
                </tr>
              </thead>
              <tbody>
                {materials.map((m) => (
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
                      {m.currentStock} ea
                    </td>
                  </tr>
                ))}
                {materials.length === 0 && (
                  <>
                    <tr>
                      <td className="font-code text-primary">MAT-SCREW</td>
                      <td style={{ fontWeight: "600" }}>티타늄 나사</td>
                      <td
                        style={{
                          textAlign: "right",
                          fontWeight: "bold",
                          color: "var(--tertiary)",
                        }}
                      >
                        4,500 ea
                      </td>
                    </tr>
                    <tr>
                      <td className="font-code text-primary">MAT-PCB</td>
                      <td style={{ fontWeight: "600" }}>제어 회로 기판</td>
                      <td
                        style={{
                          textAlign: "right",
                          fontWeight: "bold",
                          color: "var(--error)",
                        }}
                      >
                        5 ea
                      </td>
                    </tr>
                    <tr>
                      <td className="font-code text-primary">MAT-CASE</td>
                      <td style={{ fontWeight: "600" }}>
                        강화 플라스틱 케이스
                      </td>
                      <td
                        style={{
                          textAlign: "right",
                          fontWeight: "bold",
                          color: "var(--tertiary)",
                        }}
                      >
                        850 ea
                      </td>
                    </tr>
                  </>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* 오른쪽: 빠른 수불 관리 */}
        <div className="grid-right">
          <div className="card">
            <h3>빠른 재고 수불 관리</h3>
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
      </div>
    </>
  );
}

export default MaterialPage;
