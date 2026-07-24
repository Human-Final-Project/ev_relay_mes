import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import MesApi from "../api/MesApi";

// 안전 재고 이하로 떨어진 자재 (API 연동 전 기본값)
const fallbackLowStockItems = [
  { code: "MAT-WIRE-BK", name: "Lead Wire (Black)", currentStock: 0, safetyStock: 300 },
  { code: "MAT-HOUSING-P", name: "Relay Housing (Plastic)", currentStock: 120, safetyStock: 200 },
];

const contentStyles = `
  .lsa-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 2000; padding: 20px; }
  .lsa-modal { background: #ffffff; width: 560px; max-width: 100%; border-radius: 14px; box-shadow: 0 12px 30px rgba(0,0,0,0.18); overflow: hidden; }

  .lsa-header { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px 0 24px; }
  .lsa-header-title { display: flex; align-items: center; gap: 10px; }
  .lsa-header-title .lsa-warn-icon { width: 30px; height: 30px; border-radius: 50%; background: #fee2e2; color: #dc2626; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
  .lsa-header-title h3 { margin: 0; font-size: 16px; font-weight: 800; color: #0f172a; }
  .lsa-close { background: none; border: none; font-size: 18px; color: #94a3b8; cursor: pointer; padding: 4px; line-height: 1; }
  .lsa-close:hover { color: #475569; }

  .lsa-desc { padding: 12px 24px 0 24px; font-size: 13.5px; color: #475569; line-height: 1.6; }
  .lsa-desc b { color: #dc2626; }

  .lsa-table-wrap { padding: 16px 24px 0 24px; }
  .lsa-table { width: 100%; border-collapse: collapse; }
  .lsa-table th { text-align: left; font-size: 12px; font-weight: 700; color: #64748b; padding: 10px 12px; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }
  .lsa-table th:last-child, .lsa-table td:last-child { text-align: center; }
  .lsa-table td { padding: 14px 12px; font-size: 13.5px; border-bottom: 1px solid #f1f5f9; }
  .lsa-table td.mat-name { font-weight: 700; color: #0f172a; }
  .lsa-table td.mat-current { font-weight: 800; color: #dc2626; }
  .lsa-table td.mat-safety { color: #64748b; }

  .lsa-badge { display: inline-flex; font-size: 11px; font-weight: 800; padding: 4px 10px; border-radius: 999px; letter-spacing: 0.02em; }
  .lsa-badge.out { background: #dc2626; color: #ffffff; }
  .lsa-badge.low { background: #fee2e2; color: #dc2626; }

  .lsa-footer { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px 24px 24px; margin-top: 8px; }
  .lsa-later-btn { background: none; border: none; color: #64748b; font-size: 13px; font-weight: 600; cursor: pointer; }
  .lsa-later-btn:hover { color: #334155; text-decoration: underline; }
  .lsa-order-btn { display: inline-flex; align-items: center; gap: 8px; background: #02639a; color: #ffffff; border: none; padding: 11px 18px; border-radius: 8px; font-size: 13.5px; font-weight: 700; cursor: pointer; }
  .lsa-order-btn:hover { background: #0284c7; }
`;

function LowStockAlertModal() {
  const navigate = useNavigate();
  const [visible, setVisible] = useState(false);
  const [lowStockItems, setLowStockItems] = useState([]);

  useEffect(() => {
    const userRole = localStorage.getItem("userRole");
    const alreadyShown = sessionStorage.getItem("lowStockAlertShown") === "true";

    if (userRole !== "admin" || alreadyShown) return;

    const loadLowStock = async () => {
      try {
        const res = await MesApi.getLowStockMaterials();
        const items = res.data || [];
        setLowStockItems(items.length > 0 ? items : fallbackLowStockItems);
      } catch (e) {
        setLowStockItems(fallbackLowStockItems);
      } finally {
        setVisible(true);
        sessionStorage.setItem("lowStockAlertShown", "true");
      }
    };

    loadLowStock();
  }, []);

  const closeModal = () => setVisible(false);

  const handleOrderMaterials = () => {
    setVisible(false);
    navigate("/material");
  };

  if (!visible) return null;

  return (
    <div className="lsa-overlay" onClick={closeModal}>
      <style>{contentStyles}</style>
      <div className="lsa-modal" onClick={(e) => e.stopPropagation()}>
        <div className="lsa-header">
          <div className="lsa-header-title">
            <span className="lsa-warn-icon">
              <span className="material-symbols-outlined" style={{ fontSize: "18px" }}>warning</span>
            </span>
            <h3>재고 부족 알림 (Low Stock Alert)</h3>
          </div>
          <button type="button" className="lsa-close" onClick={closeModal}>✕</button>
        </div>

        <p className="lsa-desc">
          현재 안전 재고 수준 이하로 떨어진 자재가 <b>{lowStockItems.length}건</b> 있습니다. 즉각적인 확인 및 발주가 필요합니다.
        </p>

        <div className="lsa-table-wrap">
          <table className="lsa-table">
            <thead>
              <tr>
                <th>자재명</th>
                <th>현재 재고</th>
                <th>안전 재고</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {lowStockItems.map((item) => {
                const isOut = Number(item.currentStock) <= 0;
                return (
                  <tr key={item.code}>
                    <td className="mat-name">{item.name}</td>
                    <td className="mat-current">{item.currentStock} PCS</td>
                    <td className="mat-safety">{item.safetyStock}</td>
                    <td>
                      <span className={`lsa-badge ${isOut ? "out" : "low"}`}>
                        {isOut ? "OUT OF STOCK" : "LOW STOCK"}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="lsa-footer">
          <button type="button" className="lsa-later-btn" onClick={closeModal}>
            나중에 확인 (Remind me later)
          </button>
          <button type="button" className="lsa-order-btn" onClick={handleOrderMaterials}>
            <span className="material-symbols-outlined" style={{ fontSize: "18px" }}>shopping_cart</span>
            <span>자재 발주하기 (Order Materials)</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default LowStockAlertModal;