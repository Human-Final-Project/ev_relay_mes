import React, { useState, useEffect, useCallback } from "react";
import MesApi from "../api/MesApi";

// -------------------------------------------------------------
// [상수 정의] - 백엔드 Item.ItemType(RM/SA/FG) 그대로 사용
// -------------------------------------------------------------
const TYPE_OPTIONS = ["RM", "SA", "FG"];
const TYPE_LABEL = { RM: "원자재", SA: "반제품", FG: "완제품" };
const TYPE_CLASS = { RM: "type-rm", SA: "type-sa", FG: "type-fg" };

const emptyItemForm = { itemCode: "", itemName: "", itemType: "FG" };
const emptyBomForm = { childItemCode: "", quantity: "1", processCode: "" };

// -------------------------------------------------------------
// [CSS 스타일 정의] - 프로젝트 공통 톤(#0566d9 포인트 컬러)에 맞춤
// -------------------------------------------------------------
const styles = `
  .master-container { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; display: flex; gap: 20px; align-items: flex-start; }

  .list-panel { width: 340px; flex-shrink: 0; }
  .list-panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
  .list-panel-header h3 { margin: 0; font-size: 18px; font-weight: 800; color: #0f172a; }
  .btn-primary { background: #0566d9; color: #ffffff; border: none; padding: 9px 14px; border-radius: 8px; font-size: 12px; font-weight: 700; cursor: pointer; transition: 0.2s; white-space: nowrap; display: inline-flex; align-items: center; gap: 4px; }
  .btn-primary:hover { opacity: 0.85; }

  .filter-row { display: flex; gap: 8px; margin-bottom: 14px; }
  .search-box-wrap { position: relative; flex: 1; }
  .search-box-wrap .icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: #94a3b8; font-size: 13px; }
  .search-box-full { width: 100%; box-sizing: border-box; border: 1px solid #cbd5e1; padding: 10px 12px 10px 32px; border-radius: 8px; font-size: 13px; outline: none; }
  .search-box-full:focus { border-color: #0566d9; }
  .type-filter { border: 1px solid #cbd5e1; border-radius: 8px; padding: 10px 8px; font-size: 13px; outline: none; background: #fff; }

  .product-list { display: flex; flex-direction: column; gap: 10px; max-height: 640px; overflow-y: auto; }
  .product-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px; cursor: pointer; transition: all 0.15s; }
  .product-card:hover { border-color: #93c5fd; }
  .product-card.active { border-color: #0566d9; background: #f0f9ff; box-shadow: 0 0 0 1px #0566d9; }
  .product-card.inactive { opacity: 0.55; }
  .product-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; margin-bottom: 6px; }
  .product-card-top .p-name { font-size: 14px; font-weight: 800; color: #0f172a; }
  .product-card .p-sku { font-size: 12px; color: #64748b; font-family: monospace; margin-bottom: 8px; }

  .status-badge { font-size: 11px; font-weight: 700; padding: 3px 9px; border-radius: 12px; white-space: nowrap; }
  .type-rm { background: #fef3c7; color: #92400e; }
  .type-sa { background: #dbeafe; color: #1d4ed8; }
  .type-fg { background: #dcfce7; color: #16803d; }
  .status-active { background: #dcfce7; color: #16803d; }
  .status-inactive { background: #f1f5f9; color: #64748b; }

  .detail-panel { flex: 1; min-width: 0; }
  .empty-detail { background: #ffffff; border: 1px dashed #cbd5e1; border-radius: 12px; padding: 60px; text-align: center; color: #94a3b8; font-size: 13px; }

  .breadcrumb { font-size: 12px; color: #94a3b8; font-weight: 600; margin-bottom: 10px; }
  .breadcrumb .current { color: #0566d9; font-weight: 700; }

  .detail-title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
  .detail-title-row h2 { margin: 0 0 6px 0; font-size: 24px; font-weight: 800; color: #0f172a; }
  .detail-title-row p { margin: 0; font-size: 13px; color: #64748b; font-family: monospace; }
  .detail-actions { display: flex; gap: 10px; flex-shrink: 0; }
  .btn-outline { background: #ffffff; border: 1px solid #cbd5e1; padding: 10px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; color: #475569; cursor: pointer; white-space: nowrap; }
  .btn-outline:hover { background: #f1f5f9; }
  .btn-edit { background: #0566d9; color: #fff; border: none; padding: 10px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; cursor: pointer; white-space: nowrap; }
  .btn-edit:hover { opacity: 0.85; }

  .card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin-bottom: 20px; }
  .card-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 800; color: #0f172a; margin-bottom: 18px; }

  .spec-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); row-gap: 22px; column-gap: 16px; }
  .spec-item .s-label { font-size: 12px; color: #64748b; font-weight: 600; margin-bottom: 6px; }
  .spec-item .s-value { font-size: 18px; font-weight: 800; color: #0566d9; }

  .bom-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
  .bom-header-right { display: flex; align-items: center; gap: 14px; }
  .bom-count { font-size: 12px; color: #64748b; font-weight: 600; }

  .bom-table { width: 100%; border-collapse: collapse; text-align: left; margin-top: 8px; }
  .bom-table th { font-size: 12px; color: #64748b; font-weight: 700; padding: 12px 10px; border-bottom: 1px solid #e2e8f0; }
  .bom-table td { padding: 16px 10px; border-bottom: 1px solid #f1f5f9; font-size: 13px; vertical-align: top; }
  .bom-table tbody tr:hover td { background: #f8fafc; }
  .part-id { color: #0566d9; font-weight: 700; font-family: monospace; font-size: 12.5px; }
  .part-actions { display: flex; align-items: center; gap: 8px; }
  .btn-del-part { background: none; border: none; color: #cbd5e1; font-size: 15px; cursor: pointer; padding: 0 2px; }
  .btn-del-part:hover { color: #dc2626; }

  .bom-footer { display: flex; justify-content: flex-end; padding-top: 14px; }
  .btn-add-part { background: none; border: 1px dashed #94a3b8; color: #475569; padding: 8px 14px; border-radius: 8px; font-size: 12px; font-weight: 700; cursor: pointer; }
  .btn-add-part:hover { border-color: #0566d9; color: #0566d9; }

  .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
  .modal-content { background: #ffffff; width: 460px; max-width: 100%; border-radius: 12px; padding: 24px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); max-height: 88vh; overflow-y: auto; }
  .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
  .modal-header h3 { margin: 0; font-size: 17px; color: #0f172a; }
  .btn-close { background: none; border: none; font-size: 18px; cursor: pointer; color: #64748b; }

  .form-row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
  .form-group label { font-size: 12px; font-weight: 700; color: #334155; }
  .form-group input, .form-group select, .form-group textarea { padding: 10px; border: 1px solid #cbd5e1; border-radius: 8px; outline: none; font-size: 13px; background: #ffffff; font-family: inherit; }
  .form-group input:disabled { background: #f1f5f9; color: #94a3b8; }
  .form-group input:focus, .form-group select:focus, .form-group textarea:focus { border-color: #0566d9; }

  .modal-footer { display: flex; gap: 10px; margin-top: 6px; }
  .submit-btn { flex: 1; padding: 12px; background: #0566d9; color: white; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; transition: 0.2s; font-size: 13px; }
  .submit-btn:hover { opacity: 0.85; }
  .submit-btn:disabled { opacity: 0.6; cursor: not-allowed; }
  .danger-btn { padding: 12px 16px; background: #fee2e2; color: #dc2626; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; font-size: 13px; }
  .danger-btn:hover { background: #fca5a5; }

  .readonly-tag { font-size: 11px; font-weight: 700; color: #64748b; background: #f1f5f9; padding: 4px 10px; border-radius: 12px; }
`;

function MasterDataPage() {
  const isAdmin = localStorage.getItem("userRole") === "admin";

  const [items, setItems] = useState([]);
  const [boms, setBoms] = useState([]);
  const [processes, setProcesses] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

  const [selectedCode, setSelectedCode] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");

  const [showItemModal, setShowItemModal] = useState(false);
  const [itemModalMode, setItemModalMode] = useState("add");
  const [itemForm, setItemForm] = useState(emptyItemForm);

  const [showBomModal, setShowBomModal] = useState(false);
  const [bomForm, setBomForm] = useState(emptyBomForm);

  const fetchAll = useCallback(async () => {
    setIsLoading(true);
    try {
      const [itemsRes, bomsRes, processesRes] = await Promise.all([
        MesApi.getItems(),
        MesApi.getBoms(),
        MesApi.getProcesses(),
      ]);
      setItems(itemsRes.data || []);
      setBoms(bomsRes.data || []);
      setProcesses(processesRes.data || []);
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "기준정보를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  useEffect(() => {
    if (!selectedCode && items.length > 0) {
      setSelectedCode(items[0].itemCode);
    }
  }, [items, selectedCode]);

  const itemNameByCode = Object.fromEntries(items.map((i) => [i.itemCode, i.itemName]));
  const processNameByCode = Object.fromEntries(processes.map((p) => [p.processCode, p.processName]));

  const selectedItem = items.find((i) => i.itemCode === selectedCode) || null;
  const childBoms = boms.filter((b) => b.parentItemCode === selectedCode);

  const filteredItems = items.filter((i) => {
    const matchesSearch =
      i.itemName.toLowerCase().includes(searchTerm.trim().toLowerCase()) ||
      i.itemCode.toLowerCase().includes(searchTerm.trim().toLowerCase());
    const matchesType = typeFilter === "ALL" || i.itemType === typeFilter;
    return matchesSearch && matchesType;
  });

  // ---- 품목 등록/수정 ----
  const openAddItemModal = () => {
    setItemModalMode("add");
    setItemForm(emptyItemForm);
    setShowItemModal(true);
  };

  const openEditItemModal = () => {
    if (!selectedItem) return;
    setItemModalMode("edit");
    setItemForm({
      itemCode: selectedItem.itemCode,
      itemName: selectedItem.itemName,
      itemType: selectedItem.itemType,
    });
    setShowItemModal(true);
  };

  const closeItemModal = () => setShowItemModal(false);

  const handleItemFormChange = (e) => {
    const { name, value } = e.target;
    setItemForm({ ...itemForm, [name]: value });
  };

  const handleItemSubmit = async (e) => {
    e.preventDefault();
    if (!itemForm.itemCode || !itemForm.itemName) {
      alert("품목 코드와 품목명은 필수 입력 항목입니다.");
      return;
    }
    try {
      if (itemModalMode === "add") {
        await MesApi.createItem(itemForm);
      } else {
        await MesApi.updateItem(itemForm.itemCode, itemForm);
      }
      setShowItemModal(false);
      setSelectedCode(itemForm.itemCode);
      await fetchAll();
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "품목 저장에 실패했습니다.");
    }
  };

  const handleToggleItemActive = async () => {
    if (!selectedItem) return;
    try {
      await MesApi.updateItemActive(selectedItem.itemCode, selectedItem.useYn !== "Y");
      await fetchAll();
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "상태 변경에 실패했습니다.");
    }
  };

  const handleDeleteItem = async () => {
    if (!selectedItem) return;
    if (!window.confirm(`${selectedItem.itemName}을(를) 삭제하시겠습니까?`)) return;
    try {
      await MesApi.deleteItem(selectedItem.itemCode);
      setSelectedCode(null);
      setShowItemModal(false);
      await fetchAll();
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "삭제에 실패했습니다. BOM이나 작업지시 등에서 사용 중인지 확인하세요.");
    }
  };

  // ---- BOM 등록 ----
  const openAddBomModal = () => {
    setBomForm({
      childItemCode: items.find((i) => i.itemCode !== selectedCode)?.itemCode || "",
      quantity: "1",
      processCode: processes[0]?.processCode || "",
    });
    setShowBomModal(true);
  };

  const closeBomModal = () => setShowBomModal(false);

  const handleBomFormChange = (e) => {
    const { name, value } = e.target;
    setBomForm({ ...bomForm, [name]: value });
  };

  const handleBomSubmit = async (e) => {
    e.preventDefault();
    if (!bomForm.childItemCode || !bomForm.processCode || !bomForm.quantity) {
      alert("하위 품목, 수량, 공정을 모두 선택하세요.");
      return;
    }
    try {
      await MesApi.createBom({
        parentItemCode: selectedCode,
        childItemCode: bomForm.childItemCode,
        quantity: Number(bomForm.quantity),
        processCode: bomForm.processCode,
      });
      setShowBomModal(false);
      await fetchAll();
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "BOM 등록에 실패했습니다.");
    }
  };

  const handleDeleteBom = async (bomId) => {
    if (!window.confirm("이 BOM 구성을 삭제하시겠습니까?")) return;
    try {
      await MesApi.deleteBom(bomId);
      await fetchAll();
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.message || "BOM 삭제에 실패했습니다.");
    }
  };

  return (
    <div>
      <style>{styles}</style>

      <div className="master-container">
        {/* 좌측: 품목 목록 */}
        <div className="list-panel">
          <div className="list-panel-header">
            <h3>품목 목록</h3>
            {isAdmin ? (
              <button className="btn-primary" onClick={openAddItemModal}>+ 신규 품목 등록</button>
            ) : (
              <span className="readonly-tag">조회 전용</span>
            )}
          </div>

          <div className="filter-row">
            <div className="search-box-wrap">
              <span className="icon">🔍</span>
              <input
                type="text"
                className="search-box-full"
                placeholder="품목명 또는 코드 검색..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
            <select className="type-filter" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
              <option value="ALL">전체</option>
              {TYPE_OPTIONS.map((t) => (
                <option key={t} value={t}>{TYPE_LABEL[t]}</option>
              ))}
            </select>
          </div>

          <div className="product-list">
            {isLoading && <div className="empty-detail" style={{ padding: "30px" }}>불러오는 중...</div>}
            {!isLoading && filteredItems.map((i) => (
              <div
                key={i.itemCode}
                className={`product-card ${i.itemCode === selectedCode ? "active" : ""} ${i.useYn !== "Y" ? "inactive" : ""}`}
                onClick={() => setSelectedCode(i.itemCode)}
              >
                <div className="product-card-top">
                  <span className="p-name">{i.itemName}</span>
                  <span className={`status-badge ${TYPE_CLASS[i.itemType]}`}>{TYPE_LABEL[i.itemType]}</span>
                </div>
                <div className="p-sku">{i.itemCode}</div>
              </div>
            ))}
            {!isLoading && filteredItems.length === 0 && (
              <div className="empty-detail" style={{ padding: "30px" }}>검색 결과가 없습니다.</div>
            )}
          </div>
        </div>

        {/* 우측: 상세 정보 */}
        <div className="detail-panel">
          {!selectedItem ? (
            <div className="empty-detail">좌측에서 품목을 선택하거나 신규 품목을 등록하세요.</div>
          ) : (
            <>
              <div className="breadcrumb">
                기준정보 관리 / 품목 목록 / <span className="current">{selectedItem.itemName}</span>
              </div>

              <div className="detail-title-row">
                <div>
                  <h2>{selectedItem.itemName}</h2>
                  <p>{selectedItem.itemCode}</p>
                </div>
                {isAdmin && (
                  <div className="detail-actions">
                    <button className="btn-outline" onClick={handleToggleItemActive}>
                      {selectedItem.useYn === "Y" ? "사용 중지" : "사용 재개"}
                    </button>
                    <button className="btn-edit" onClick={openEditItemModal}>✎ 정보 수정</button>
                  </div>
                )}
              </div>

              <div className="card">
                <div className="card-title">🧭 품목 정보</div>
                <div className="spec-grid">
                  <div className="spec-item"><div className="s-label">품목 코드</div><div className="s-value">{selectedItem.itemCode}</div></div>
                  <div className="spec-item"><div className="s-label">구분</div><div className="s-value">{TYPE_LABEL[selectedItem.itemType]}</div></div>
                  <div className="spec-item">
                    <div className="s-label">상태</div>
                    <span className={`status-badge ${selectedItem.useYn === "Y" ? "status-active" : "status-inactive"}`}>
                      {selectedItem.useYn === "Y" ? "사용중" : "미사용"}
                    </span>
                  </div>
                </div>
              </div>

              <div className="card">
                <div className="bom-card-header">
                  <div className="card-title" style={{ marginBottom: 0 }}>🗂 BOM (이 품목을 만드는 데 필요한 하위 품목)</div>
                  <div className="bom-header-right">
                    <span className="bom-count">총 {String(childBoms.length).padStart(2, "0")}건</span>
                  </div>
                </div>

                <table className="bom-table">
                  <thead>
                    <tr>
                      <th>하위 품목 코드</th>
                      <th>품명</th>
                      <th>소요 수량</th>
                      <th>공정</th>
                      <th>상태</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {childBoms.map((b) => (
                      <tr key={b.bomId}>
                        <td className="part-id">{b.childItemCode}</td>
                        <td>{itemNameByCode[b.childItemCode] || "-"}</td>
                        <td>{b.quantity}</td>
                        <td>{processNameByCode[b.processCode] || b.processCode || "-"}</td>
                        <td>
                          <span className={`status-badge ${b.useYn === "Y" ? "status-active" : "status-inactive"}`}>
                            {b.useYn === "Y" ? "사용중" : "미사용"}
                          </span>
                        </td>
                        <td>
                          {isAdmin && (
                            <div className="part-actions">
                              <button className="btn-del-part" onClick={() => handleDeleteBom(b.bomId)} title="삭제">✕</button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                    {childBoms.length === 0 && (
                      <tr>
                        <td colSpan="6" style={{ textAlign: "center", padding: "30px", color: "#94a3b8" }}>등록된 BOM 구성이 없습니다.</td>
                      </tr>
                    )}
                  </tbody>
                </table>

                {isAdmin && (
                  <div className="bom-footer">
                    <button className="btn-add-part" onClick={openAddBomModal}>⊕ BOM 부품 추가</button>
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        {/* 품목 등록/수정 모달 */}
        {showItemModal && isAdmin && (
          <div className="modal-overlay" onClick={closeItemModal}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <div className="modal-header">
                <h3>{itemModalMode === "add" ? "신규 품목 등록" : "품목 정보 수정"}</h3>
                <button className="btn-close" onClick={closeItemModal}>✕</button>
              </div>
              <form onSubmit={handleItemSubmit}>
                <div className="form-group">
                  <label>품목 코드</label>
                  <input
                    type="text"
                    name="itemCode"
                    placeholder="예: FG-EVR-002"
                    value={itemForm.itemCode}
                    onChange={handleItemFormChange}
                    disabled={itemModalMode === "edit"}
                  />
                </div>
                <div className="form-group">
                  <label>품목명</label>
                  <input
                    type="text"
                    name="itemName"
                    placeholder="예: EV Relay 완제품 (2세대)"
                    value={itemForm.itemName}
                    onChange={handleItemFormChange}
                  />
                </div>
                <div className="form-group">
                  <label>구분</label>
                  <select name="itemType" value={itemForm.itemType} onChange={handleItemFormChange}>
                    {TYPE_OPTIONS.map((t) => (
                      <option key={t} value={t}>{TYPE_LABEL[t]} ({t})</option>
                    ))}
                  </select>
                </div>

                <div className="modal-footer">
                  {itemModalMode === "edit" && (
                    <button type="button" className="danger-btn" onClick={handleDeleteItem}>삭제</button>
                  )}
                  <button type="submit" className="submit-btn">
                    {itemModalMode === "add" ? "품목 등록하기" : "변경사항 저장"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* BOM 부품 추가 모달 */}
        {showBomModal && isAdmin && (
          <div className="modal-overlay" onClick={closeBomModal}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <div className="modal-header">
                <h3>BOM 부품 추가</h3>
                <button className="btn-close" onClick={closeBomModal}>✕</button>
              </div>
              <form onSubmit={handleBomSubmit}>
                <div className="form-group">
                  <label>하위 품목</label>
                  <select name="childItemCode" value={bomForm.childItemCode} onChange={handleBomFormChange}>
                    {items.filter((i) => i.itemCode !== selectedCode).map((i) => (
                      <option key={i.itemCode} value={i.itemCode}>{i.itemName} ({i.itemCode})</option>
                    ))}
                  </select>
                </div>
                <div className="form-row2">
                  <div className="form-group">
                    <label>소요 수량</label>
                    <input
                      type="number"
                      name="quantity"
                      min="0"
                      step="0.001"
                      value={bomForm.quantity}
                      onChange={handleBomFormChange}
                    />
                  </div>
                  <div className="form-group">
                    <label>공정</label>
                    <select name="processCode" value={bomForm.processCode} onChange={handleBomFormChange}>
                      {processes.map((p) => (
                        <option key={p.processCode} value={p.processCode}>{p.processName} ({p.processCode})</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="modal-footer">
                  <button type="submit" className="submit-btn" disabled={items.length <= 1 || processes.length === 0}>
                    부품 추가하기
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default MasterDataPage;
