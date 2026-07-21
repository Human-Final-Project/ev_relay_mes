import React, { useState } from "react";

// -------------------------------------------------------------
// [데이터 정의]
// -------------------------------------------------------------
const STATUS_OPTIONS = ["활성", "초안", "보류"];
const UNIT_OPTIONS = ["PC", "GR", "KG", "M", "EA"];
const PART_STATUS_OPTIONS = ["재고충분", "할당완료", "재고부족"];

const initialProducts = [
  {
    id: "A",
    name: "EV 릴레이 모델 A",
    sku: "REL-2024-XA",
    status: "활성",
    bomVersion: "V3.4",
    specUpdated: "2일 전",
    description: "상용차 전력 관리용 고전압 솔리드 스테이트 릴레이.",
    image: "🔌",
    visualFile: "REV_3.4_MODEL_A.cad",
    specs: { maxVoltage: "800V DC", ratedCurrent: "250A", insulation: "5.0 kV", responseTime: "< 15ms", heatLimit: "125°C", weight: "340g" },
    bom: [
      { partId: "CP-2093-X", name: "메인 하우징 (FR-4 등급)", qty: "1", unit: "PC", supplier: "Industrial Molding Corp", status: "재고충분" },
      { partId: "EL-9001-S", name: "은합금 접점", qty: "2", unit: "PC", supplier: "Metals Tech Global", status: "재고충분" },
      { partId: "CL-0042-C", name: "전자기 코일 어셈블리", qty: "1", unit: "PC", supplier: "Precision Windings Ltd", status: "할당완료" },
      { partId: "SC-8812-B", name: "M4 잠금 나사 (방진형)", qty: "4", unit: "PC", supplier: "Fastener Solutions", status: "재고부족" },
      { partId: "TC-5520-P", name: "열전달 인터페이스 페이스트", qty: "0.5", unit: "GR", supplier: "Chemical Labs Co", status: "재고충분" },
      { partId: "RS-3301-K", name: "방열판 (알루미늄)", qty: "1", unit: "PC", supplier: "ThermalTech Korea", status: "재고충분" },
      { partId: "WS-1190-D", name: "배선 하네스 커넥터", qty: "1", unit: "PC", supplier: "WireConn Systems", status: "재고충분" },
      { partId: "PB-7745-M", name: "PCB 제어 기판", qty: "1", unit: "PC", supplier: "CircuitPro Inc", status: "할당완료" },
    ],
  },
  {
    id: "B",
    name: "EV 릴레이 모델 B",
    sku: "REL-2024-XB",
    status: "활성",
    bomVersion: "V2.1",
    specUpdated: "5일 전",
    description: "경량형 저전력 EV 배터리 관리용 릴레이.",
    image: "🔋",
    visualFile: "REV_2.1_MODEL_B.cad",
    specs: { maxVoltage: "720V DC", ratedCurrent: "200A", insulation: "4.5 kV", responseTime: "< 18ms", heatLimit: "120°C", weight: "310g" },
    bom: [
      { partId: "CP-2094-X", name: "메인 하우징 (PA6 등급)", qty: "1", unit: "PC", supplier: "Industrial Molding Corp", status: "재고충분" },
      { partId: "EL-9002-S", name: "은합금 접점", qty: "2", unit: "PC", supplier: "Metals Tech Global", status: "재고충분" },
      { partId: "CL-0043-C", name: "전자기 코일 어셈블리", qty: "1", unit: "PC", supplier: "Precision Windings Ltd", status: "재고부족" },
      { partId: "SC-8813-B", name: "M3 잠금 나사", qty: "4", unit: "PC", supplier: "Fastener Solutions", status: "재고충분" },
    ],
  },
  {
    id: "P",
    name: "EV 릴레이 모델 P",
    sku: "REL-2024-XP",
    status: "초안",
    bomVersion: "V0.9 (초안)",
    specUpdated: "방금 전",
    description: "차세대 고출력 프로토타입 릴레이 (개발 진행 중).",
    image: "🧪",
    visualFile: "REV_0.9_MODEL_P.cad",
    specs: { maxVoltage: "800V DC", ratedCurrent: "300A", insulation: "5.5 kV", responseTime: "< 12ms", heatLimit: "130°C", weight: "360g" },
    bom: [
      { partId: "CP-2099-X", name: "메인 하우징 (시제품)", qty: "1", unit: "PC", supplier: "Industrial Molding Corp", status: "할당완료" },
      { partId: "EL-9005-S", name: "은합금 접점 (고내구)", qty: "2", unit: "PC", supplier: "Metals Tech Global", status: "재고부족" },
    ],
  },
  {
    id: "S",
    name: "EV 릴레이 모델 S",
    sku: "REL-2024-XS",
    status: "보류",
    bomVersion: "V1.5",
    specUpdated: "40일 전",
    description: "구형 플랫폼 호환 릴레이 (단종 검토 중).",
    image: "📦",
    visualFile: "REV_1.5_MODEL_S.cad",
    specs: { maxVoltage: "650V DC", ratedCurrent: "180A", insulation: "4.0 kV", responseTime: "< 20ms", heatLimit: "110°C", weight: "290g" },
    bom: [
      { partId: "CP-2080-X", name: "메인 하우징 (구형)", qty: "1", unit: "PC", supplier: "Industrial Molding Corp", status: "재고충분" },
      { partId: "EL-8990-S", name: "은합금 접점 (구형)", qty: "2", unit: "PC", supplier: "Metals Tech Global", status: "재고충분" },
      { partId: "SC-8800-B", name: "M4 잠금 나사", qty: "4", unit: "PC", supplier: "Fastener Solutions", status: "재고충분" },
    ],
  },
];

const STATUS_CLASS = { "활성": "status-active", "초안": "status-draft", "보류": "status-hold" };
const PART_STATUS_CLASS = { "재고충분": "part-ok", "할당완료": "part-allocated", "재고부족": "part-low" };

const emptyProductForm = {
  name: "", sku: "", status: "초안", bomVersion: "V0.1 (초안)", description: "", image: "🔌",
  maxVoltage: "", ratedCurrent: "", insulation: "", responseTime: "", heatLimit: "", weight: "",
};

const emptyBomForm = { partId: "", name: "", qty: "", unit: "PC", supplier: "", status: "재고충분" };

// -------------------------------------------------------------
// [CSS 스타일 정의] - 프로젝트 공통 톤(#0566d9 포인트 컬러)에 맞춤
// -------------------------------------------------------------
const styles = `
  .master-container { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; display: flex; gap: 20px; align-items: flex-start; }

  .list-panel { width: 320px; flex-shrink: 0; }
  .list-panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
  .list-panel-header h3 { margin: 0; font-size: 18px; font-weight: 800; color: #0f172a; }
  .btn-primary { background: #0566d9; color: #ffffff; border: none; padding: 9px 14px; border-radius: 8px; font-size: 12px; font-weight: 700; cursor: pointer; transition: 0.2s; white-space: nowrap; display: inline-flex; align-items: center; gap: 4px; }
  .btn-primary:hover { opacity: 0.85; }

  .search-box-wrap { position: relative; margin-bottom: 14px; }
  .search-box-wrap .icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: #94a3b8; font-size: 13px; }
  .search-box-full { width: 100%; box-sizing: border-box; border: 1px solid #cbd5e1; padding: 10px 12px 10px 32px; border-radius: 8px; font-size: 13px; outline: none; }
  .search-box-full:focus { border-color: #0566d9; }

  .product-list { display: flex; flex-direction: column; gap: 10px; }
  .product-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px; cursor: pointer; transition: all 0.15s; }
  .product-card:hover { border-color: #93c5fd; }
  .product-card.active { border-color: #0566d9; background: #f0f9ff; box-shadow: 0 0 0 1px #0566d9; }
  .product-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; margin-bottom: 6px; }
  .product-card-top .p-name { font-size: 14px; font-weight: 800; color: #0f172a; }
  .product-card .p-sku { font-size: 12px; color: #64748b; font-family: monospace; margin-bottom: 8px; }
  .product-card .p-meta { font-size: 11px; color: #94a3b8; display: flex; gap: 10px; }

  .status-badge { font-size: 11px; font-weight: 700; padding: 3px 9px; border-radius: 12px; white-space: nowrap; }
  .status-active { background: #dcfce7; color: #16803d; }
  .status-draft { background: #f1f5f9; color: #64748b; }
  .status-hold { background: #fee2e2; color: #b91c1c; }

  .detail-panel { flex: 1; min-width: 0; }
  .empty-detail { background: #ffffff; border: 1px dashed #cbd5e1; border-radius: 12px; padding: 60px; text-align: center; color: #94a3b8; font-size: 13px; }

  .breadcrumb { font-size: 12px; color: #94a3b8; font-weight: 600; margin-bottom: 10px; }
  .breadcrumb .current { color: #0566d9; font-weight: 700; }

  .detail-title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
  .detail-title-row h2 { margin: 0 0 6px 0; font-size: 24px; font-weight: 800; color: #0f172a; }
  .detail-title-row p { margin: 0; font-size: 13px; color: #64748b; max-width: 560px; line-height: 1.5; }
  .detail-actions { display: flex; gap: 10px; flex-shrink: 0; }
  .btn-outline { background: #ffffff; border: 1px solid #cbd5e1; padding: 10px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; color: #475569; cursor: pointer; white-space: nowrap; }
  .btn-outline:hover { background: #f1f5f9; }
  .btn-edit { background: #0566d9; color: #fff; border: none; padding: 10px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; cursor: pointer; white-space: nowrap; }
  .btn-edit:hover { opacity: 0.85; }

  .spec-row { display: grid; grid-template-columns: 2.2fr 1fr; gap: 20px; margin-bottom: 20px; align-items: stretch; }
  @media (max-width: 900px) { .spec-row { grid-template-columns: 1fr; } }

  .card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .card-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 800; color: #0f172a; margin-bottom: 18px; }

  .spec-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); row-gap: 22px; column-gap: 16px; }
  .spec-item .s-label { font-size: 12px; color: #64748b; font-weight: 600; margin-bottom: 6px; }
  .spec-item .s-value { font-size: 20px; font-weight: 800; color: #0566d9; }

  .visual-card { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
  .visual-thumb { width: 100%; aspect-ratio: 1.3 / 1; background: #f0f9ff; border: 1px solid #dbeafe; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 46px; margin-bottom: 12px; }
  .visual-card .v-title { font-size: 13px; font-weight: 700; color: #0f172a; }
  .visual-card .v-file { font-size: 11px; color: #94a3b8; font-family: monospace; margin-top: 2px; }

  .bom-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
  .bom-header-right { display: flex; align-items: center; gap: 14px; }
  .bom-count { font-size: 12px; color: #64748b; font-weight: 600; }
  .link-btn { background: none; border: none; color: #0566d9; font-size: 12px; font-weight: 700; cursor: pointer; padding: 0; }
  .link-btn:hover { text-decoration: underline; }

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
  .form-row3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
  .form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
  .form-group label { font-size: 12px; font-weight: 700; color: #334155; }
  .form-group input, .form-group select, .form-group textarea { padding: 10px; border: 1px solid #cbd5e1; border-radius: 8px; outline: none; font-size: 13px; background: #ffffff; font-family: inherit; }
  .form-group input:focus, .form-group select:focus, .form-group textarea:focus { border-color: #0566d9; }
  .form-section-label { font-size: 12px; font-weight: 800; color: #0566d9; margin: 4px 0 10px 0; text-transform: uppercase; letter-spacing: 0.02em; }

  .modal-footer { display: flex; gap: 10px; margin-top: 6px; }
  .submit-btn { flex: 1; padding: 12px; background: #0566d9; color: white; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; transition: 0.2s; font-size: 13px; }
  .submit-btn:hover { opacity: 0.85; }
  .danger-btn { padding: 12px 16px; background: #fee2e2; color: #dc2626; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; font-size: 13px; }
  .danger-btn:hover { background: #fca5a5; }

  .readonly-tag { font-size: 11px; font-weight: 700; color: #64748b; background: #f1f5f9; padding: 4px 10px; border-radius: 12px; }
`;

function MasterDataPage() {
  const [products, setProducts] = useState(initialProducts);
  const [selectedId, setSelectedId] = useState(initialProducts[0].id);
  const [searchTerm, setSearchTerm] = useState("");

  // ---- 관리자 인증 상태: 실제 로그인 권한(localStorage)과 연동 ----
  // 사원(userRole !== "admin")으로 로그인하면 등록/수정/삭제 버튼이 자동으로 숨겨지고
  // 제품 사양·BOM 조회는 그대로 가능한 읽기 전용 화면이 됩니다.
  const isAdmin = localStorage.getItem("userRole") === "admin";

  const [showProductModal, setShowProductModal] = useState(false);
  const [productModalMode, setProductModalMode] = useState("add");
  const [productForm, setProductForm] = useState(emptyProductForm);

  const [showBomModal, setShowBomModal] = useState(false);
  const [bomForm, setBomForm] = useState(emptyBomForm);

  const selectedProduct = products.find((p) => p.id === selectedId) || null;

  const filteredProducts = products.filter((p) =>
    p.name.toLowerCase().includes(searchTerm.trim().toLowerCase())
  );

  const generateProductId = () => {
    let base = "N";
    let idx = 1;
    while (products.some((p) => p.id === `${base}${idx}`)) idx += 1;
    return `${base}${idx}`;
  };

  const openAddProductModal = () => {
    setProductModalMode("add");
    setProductForm(emptyProductForm);
    setShowProductModal(true);
  };

  const openEditProductModal = () => {
    if (!selectedProduct) return;
    setProductModalMode("edit");
    setProductForm({
      name: selectedProduct.name,
      sku: selectedProduct.sku,
      status: selectedProduct.status,
      bomVersion: selectedProduct.bomVersion,
      description: selectedProduct.description,
      image: selectedProduct.image,
      ...selectedProduct.specs,
    });
    setShowProductModal(true);
  };

  const closeProductModal = () => setShowProductModal(false);

  const handleProductFormChange = (e) => {
    const { name, value } = e.target;
    setProductForm({ ...productForm, [name]: value });
  };

  const handleProductSubmit = (e) => {
    e.preventDefault();
    if (!isAdmin) return;
    if (!productForm.name || !productForm.sku) {
      alert("제품명과 SKU는 필수 입력 항목입니다.");
      return;
    }

    const specs = {
      maxVoltage: productForm.maxVoltage,
      ratedCurrent: productForm.ratedCurrent,
      insulation: productForm.insulation,
      responseTime: productForm.responseTime,
      heatLimit: productForm.heatLimit,
      weight: productForm.weight,
    };

    if (productModalMode === "add") {
      const newId = generateProductId();
      const newProduct = {
        id: newId,
        name: productForm.name,
        sku: productForm.sku,
        status: productForm.status,
        bomVersion: productForm.bomVersion,
        specUpdated: "방금 전",
        description: productForm.description,
        image: productForm.image || "🔌",
        visualFile: "미등록",
        specs,
        bom: [],
      };
      setProducts([...products, newProduct]);
      setSelectedId(newId);
      alert("신규 제품이 등록되었습니다.");
    } else {
      setProducts(
        products.map((p) =>
          p.id === selectedId
            ? { ...p, name: productForm.name, sku: productForm.sku, status: productForm.status, bomVersion: productForm.bomVersion, description: productForm.description, specs, specUpdated: "방금 전" }
            : p
        )
      );
      alert("제품 정보가 수정되었습니다.");
    }
    setShowProductModal(false);
  };

  const handleDeleteProduct = () => {
    if (!isAdmin) return;
    if (!selectedProduct) return;
    if (!window.confirm(`${selectedProduct.name}을(를) 삭제하시겠습니까?`)) return;
    const remaining = products.filter((p) => p.id !== selectedProduct.id);
    setProducts(remaining);
    setSelectedId(remaining.length > 0 ? remaining[0].id : null);
    setShowProductModal(false);
  };

  const openAddBomModal = () => {
    setBomForm(emptyBomForm);
    setShowBomModal(true);
  };

  const closeBomModal = () => setShowBomModal(false);

  const handleBomFormChange = (e) => {
    const { name, value } = e.target;
    setBomForm({ ...bomForm, [name]: value });
  };

  const handleBomSubmit = (e) => {
    e.preventDefault();
    if (!isAdmin) return;
    if (!bomForm.partId || !bomForm.name) {
      alert("부품 ID와 품명은 필수 입력 항목입니다.");
      return;
    }
    setProducts(
      products.map((p) =>
        p.id === selectedId ? { ...p, bom: [...p.bom, bomForm] } : p
      )
    );
    setShowBomModal(false);
  };

  const handleDeleteBomPart = (partId) => {
    if (!isAdmin) return;
    if (!window.confirm(`부품 ${partId}를 BOM에서 삭제하시겠습니까?`)) return;
    setProducts(
      products.map((p) =>
        p.id === selectedId ? { ...p, bom: p.bom.filter((b) => b.partId !== partId) } : p
      )
    );
  };

  return (
    <div>
      <style>{styles}</style>

      <div className="master-container">
        {/* 좌측: 제품 목록 */}
        <div className="list-panel">
          <div className="list-panel-header">
            <h3>제품 목록</h3>
            {isAdmin ? (
              <button className="btn-primary" onClick={openAddProductModal}>+ 신규 제품 등록</button>
            ) : (
              <span className="readonly-tag">조회 전용</span>
            )}
          </div>

          <div className="search-box-wrap">
            <span className="icon">🔍</span>
            <input
              type="text"
              className="search-box-full"
              placeholder="모델명 검색..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <div className="product-list">
            {filteredProducts.map((p) => (
              <div
                key={p.id}
                className={`product-card ${p.id === selectedId ? "active" : ""}`}
                onClick={() => setSelectedId(p.id)}
              >
                <div className="product-card-top">
                  <span className="p-name">{p.name}</span>
                  <span className={`status-badge ${STATUS_CLASS[p.status]}`}>{p.status}</span>
                </div>
                <div className="p-sku">SKU: {p.sku}</div>
                <div className="p-meta">
                  <span>BOM {p.bomVersion}</span>
                  <span>사양 업데이트: {p.specUpdated}</span>
                </div>
              </div>
            ))}
            {filteredProducts.length === 0 && (
              <div className="empty-detail" style={{ padding: "30px" }}>검색 결과가 없습니다.</div>
            )}
          </div>
        </div>

        {/* 우측: 상세 정보 */}
        <div className="detail-panel">
          {!selectedProduct ? (
            <div className="empty-detail">좌측에서 제품을 선택하거나 신규 제품을 등록하세요.</div>
          ) : (
            <>
              <div className="breadcrumb">
                기준정보 관리 / 제품 목록 / <span className="current">{selectedProduct.name.replace("EV 릴레이 ", "")}</span>
              </div>

              <div className="detail-title-row">
                <div>
                  <h2>{selectedProduct.name}</h2>
                  <p>{selectedProduct.description}</p>
                </div>
                <div className="detail-actions">
                  {isAdmin && (
                    <button className="btn-outline" onClick={() => alert("스키마를 내보냈습니다. (Mock)")}>⭳ 스키마 내보내기</button>
                  )}
                  {isAdmin && (
                    <button className="btn-edit" onClick={openEditProductModal}>✎ 정보 수정</button>
                  )}
                </div>
              </div>

              <div className="spec-row">
                <div className="card">
                  <div className="card-title">🧭 주요 기술 사양</div>
                  <div className="spec-grid">
                    <div className="spec-item"><div className="s-label">최대전압</div><div className="s-value">{selectedProduct.specs.maxVoltage}</div></div>
                    <div className="spec-item"><div className="s-label">정격전류</div><div className="s-value">{selectedProduct.specs.ratedCurrent}</div></div>
                    <div className="spec-item"><div className="s-label">절연</div><div className="s-value">{selectedProduct.specs.insulation}</div></div>
                    <div className="spec-item"><div className="s-label">응답 시간</div><div className="s-value">{selectedProduct.specs.responseTime}</div></div>
                    <div className="spec-item"><div className="s-label">내열 한계</div><div className="s-value">{selectedProduct.specs.heatLimit}</div></div>
                    <div className="spec-item"><div className="s-label">무게</div><div className="s-value">{selectedProduct.specs.weight}</div></div>
                  </div>
                </div>

                <div className="card visual-card">
                  <div className="visual-thumb">{selectedProduct.image}</div>
                  <div className="v-title">제품 시각 자료</div>
                  <div className="v-file">{selectedProduct.visualFile}</div>
                </div>
              </div>

              <div className="card">
                <div className="bom-card-header">
                  <div className="card-title" style={{ marginBottom: 0 }}>🗂 BOM (자재 명세서)</div>
                  <div className="bom-header-right">
                    <span className="bom-count">총 부품 수: {String(selectedProduct.bom.length).padStart(2, "0")}</span>
                    <button className="link-btn" onClick={() => alert("이력 보기 기능은 준비 중입니다.")}>이력 보기</button>
                  </div>
                </div>

                <table className="bom-table">
                  <thead>
                    <tr>
                      <th>부품 ID</th>
                      <th>품명/설명</th>
                      <th>수량</th>
                      <th>단위</th>
                      <th>공급사</th>
                      <th>상태</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selectedProduct.bom.map((part) => (
                      <tr key={part.partId}>
                        <td className="part-id">{part.partId}</td>
                        <td>{part.name}</td>
                        <td>{part.qty}</td>
                        <td>{part.unit}</td>
                        <td>{part.supplier}</td>
                        <td>
                          <div className="part-actions">
                            <span className={`status-badge ${PART_STATUS_CLASS[part.status]}`}>{part.status}</span>
                            {isAdmin && (
                              <button className="btn-del-part" onClick={() => handleDeleteBomPart(part.partId)} title="삭제">✕</button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                    {selectedProduct.bom.length === 0 && (
                      <tr>
                        <td colSpan="6" style={{ textAlign: "center", padding: "30px", color: "#94a3b8" }}>등록된 부품이 없습니다.</td>
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

        {/* 제품 등록/수정 모달 */}
        {showProductModal && isAdmin && (
          <div className="modal-overlay" onClick={closeProductModal}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <div className="modal-header">
                <h3>{productModalMode === "add" ? "신규 제품 등록" : "제품 정보 수정"}</h3>
                <button className="btn-close" onClick={closeProductModal}>✕</button>
              </div>
              <form onSubmit={handleProductSubmit}>
                <div className="form-row2">
                  <div className="form-group">
                    <label>제품명</label>
                    <input type="text" name="name" placeholder="예: EV 릴레이 모델 C" value={productForm.name} onChange={handleProductFormChange} />
                  </div>
                  <div className="form-group">
                    <label>SKU</label>
                    <input type="text" name="sku" placeholder="예: REL-2024-XC" value={productForm.sku} onChange={handleProductFormChange} />
                  </div>
                </div>

                <div className="form-group">
                  <label>설명</label>
                  <textarea name="description" rows="2" placeholder="제품 설명을 입력하세요" value={productForm.description} onChange={handleProductFormChange} />
                </div>

                <div className="form-row2">
                  <div className="form-group">
                    <label>상태</label>
                    <select name="status" value={productForm.status} onChange={handleProductFormChange}>
                      {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>BOM 버전</label>
                    <input type="text" name="bomVersion" placeholder="예: V1.0" value={productForm.bomVersion} onChange={handleProductFormChange} />
                  </div>
                </div>

                <div className="form-section-label">주요 기술 사양</div>
                <div className="form-row3">
                  <div className="form-group">
                    <label>최대전압</label>
                    <input type="text" name="maxVoltage" placeholder="800V DC" value={productForm.maxVoltage} onChange={handleProductFormChange} />
                  </div>
                  <div className="form-group">
                    <label>정격전류</label>
                    <input type="text" name="ratedCurrent" placeholder="250A" value={productForm.ratedCurrent} onChange={handleProductFormChange} />
                  </div>
                  <div className="form-group">
                    <label>절연</label>
                    <input type="text" name="insulation" placeholder="5.0 kV" value={productForm.insulation} onChange={handleProductFormChange} />
                  </div>
                </div>
                <div className="form-row3">
                  <div className="form-group">
                    <label>응답 시간</label>
                    <input type="text" name="responseTime" placeholder="< 15ms" value={productForm.responseTime} onChange={handleProductFormChange} />
                  </div>
                  <div className="form-group">
                    <label>내열 한계</label>
                    <input type="text" name="heatLimit" placeholder="125°C" value={productForm.heatLimit} onChange={handleProductFormChange} />
                  </div>
                  <div className="form-group">
                    <label>무게</label>
                    <input type="text" name="weight" placeholder="340g" value={productForm.weight} onChange={handleProductFormChange} />
                  </div>
                </div>

                <div className="modal-footer">
                  {productModalMode === "edit" && (
                    <button type="button" className="danger-btn" onClick={handleDeleteProduct}>삭제</button>
                  )}
                  <button type="submit" className="submit-btn">
                    {productModalMode === "add" ? "제품 등록하기" : "변경사항 저장"}
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
                <div className="form-row2">
                  <div className="form-group">
                    <label>부품 ID</label>
                    <input type="text" name="partId" placeholder="예: XX-0000-X" value={bomForm.partId} onChange={handleBomFormChange} />
                  </div>
                  <div className="form-group">
                    <label>공급사</label>
                    <input type="text" name="supplier" placeholder="공급사명" value={bomForm.supplier} onChange={handleBomFormChange} />
                  </div>
                </div>
                <div className="form-group">
                  <label>품명/설명</label>
                  <input type="text" name="name" placeholder="부품 명칭" value={bomForm.name} onChange={handleBomFormChange} />
                </div>
                <div className="form-row3">
                  <div className="form-group">
                    <label>수량</label>
                    <input type="text" name="qty" placeholder="1" value={bomForm.qty} onChange={handleBomFormChange} />
                  </div>
                  <div className="form-group">
                    <label>단위</label>
                    <select name="unit" value={bomForm.unit} onChange={handleBomFormChange}>
                      {UNIT_OPTIONS.map((u) => <option key={u} value={u}>{u}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>상태</label>
                    <select name="status" value={bomForm.status} onChange={handleBomFormChange}>
                      {PART_STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                </div>
                <div className="modal-footer">
                  <button type="submit" className="submit-btn">부품 추가하기</button>
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