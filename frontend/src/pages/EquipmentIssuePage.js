import React, { useEffect, useState } from "react";
import { pushNotification } from "../utils/notificationBus";
import { getIssues, addIssue, subscribeIssues } from "../utils/equipmentIssueStore";

// -------------------------------------------------------------
// [데이터 정의]
// QualityPage.js의 공정 단계(S-01~S-05)와 동일한 체계를 사용합니다.
// -------------------------------------------------------------
const EQUIPMENT_LIST = [
  { id: "S-01", name: "권선기 (S-01)" },
  { id: "S-02", name: "레이저 마킹기 (S-02)" },
  { id: "S-03", name: "하우징 성형기 (S-03)" },
  { id: "S-04", name: "레진 주입기 (S-04)" },
  { id: "S-05", name: "최종검사기 (S-05)" },
];

const ISSUE_TYPES = ["고장/정지", "이상 소음·진동", "부품 마모/부족", "온도 이상", "기타"];
const SEVERITY = [
  { key: "low", label: "낮음", desc: "생산에 지장 없음" },
  { key: "mid", label: "보통", desc: "지연 가능성 있음" },
  { key: "urgent", label: "긴급", desc: "즉시 라인 정지 필요" },
];

const SEVERITY_STYLE = {
  low: { label: "낮음", cls: "es-sev-low" },
  mid: { label: "보통", cls: "es-sev-mid" },
  urgent: { label: "긴급", cls: "es-sev-urgent" },
};

const STATUS_STYLE = {
  received: { label: "접수됨", cls: "es-status-received" },
  checking: { label: "확인중", cls: "es-status-checking" },
  resolved: { label: "해결됨", cls: "es-status-resolved" },
};

function EquipmentIssuePage() {
  const [equipment, setEquipment] = useState(EQUIPMENT_LIST[0].id);
  const [issueType, setIssueType] = useState(ISSUE_TYPES[0]);
  const [severity, setSeverity] = useState("mid");
  const [note, setNote] = useState("");
  const [photo, setPhoto] = useState(null);

  // 더미: 실제 로그인 연동 전이라 신고자명은 고정값을 사용합니다.
  const reporterName = "김철수";

  const [history, setHistory] = useState([]);
  const [flash, setFlash] = useState(false);

  useEffect(() => {
    setHistory(getIssues());
    const unsubscribe = subscribeIssues(setHistory);
    return unsubscribe;
  }, []);

  // "내 신고 이력"은 현재 로그인한 사원이 남긴 신고만 보여줍니다.
  const myHistory = history.filter((h) => h.reporter === reporterName);

  function resetForm() {
    setEquipment(EQUIPMENT_LIST[0].id);
    setIssueType(ISSUE_TYPES[0]);
    setSeverity("mid");
    setNote("");
    setPhoto(null);
  }

  function readFileAsDataUrl(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  async function handleSubmit() {
    if (!note.trim()) {
      alert("이상 증상에 대한 상세 내용을 입력해주세요.");
      return;
    }

    // localStorage 저장 용량 보호를 위해 첨부 사진은 2MB로 제한합니다.
    if (photo && photo.size > 2 * 1024 * 1024) {
      alert("사진 용량이 너무 큽니다. 2MB 이하의 이미지를 첨부해주세요.");
      return;
    }

    const equipmentName = EQUIPMENT_LIST.find((e) => e.id === equipment)?.name || equipment;
    const photoDataUrl = photo ? await readFileAsDataUrl(photo) : null;

    addIssue({
      equipment: equipmentName,
      issueType,
      severity,
      note,
      photoName: photo ? photo.name : null,
      photoDataUrl,
      reporter: reporterName,
    });

    setFlash(true);
    setTimeout(() => setFlash(false), 1600);

    // 관리자에게는 신고 접수로, 같은 라인 사원들에게는 설비 이상 알림으로 동시에 전달
    pushNotification({
      targetRole: "both",
      type: severity === "urgent" ? "error" : severity === "mid" ? "warn" : "info",
      title: `${severity === "urgent" ? "긴급" : "설비"} ${issueType}`,
      desc: `${equipmentName} · ${note}`,
    });

    if (severity === "urgent") {
      alert("긴급 신고가 접수되었습니다. 관리자에게 즉시 알림이 전송됩니다.");
    }
    resetForm();
  }

  const styles = `
    .es { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .es-header { margin-bottom: 20px; }
    .es-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .es-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .es-grid { display: grid; grid-template-columns: 1.2fr 1fr; gap: 20px; align-items: start; }
    @media (max-width: 1000px) { .es-grid { grid-template-columns: 1fr; } }

    .es-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }
    .es-card-title { font-size: 15px; font-weight: 800; color: #0f172a; margin-bottom: 16px; }

    .es-field { margin-bottom: 16px; }
    .es-field:last-child { margin-bottom: 0; }
    .es-field label { display: block; font-size: 12px; font-weight: 700; color: #64748b; margin-bottom: 6px; }
    .es-select, .es-textarea { width: 100%; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; font-family: inherit; background: #ffffff; }
    .es-select:focus, .es-textarea:focus { border-color: #0566d9; }
    .es-textarea { min-height: 90px; resize: vertical; }
    .es-file-input { font-size: 12px; color: #64748b; }

    .es-sev-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .es-sev-btn { border: 1.5px solid #e2e8f0; border-radius: 10px; padding: 12px 8px; text-align: center; cursor: pointer; background: #fff; }
    .es-sev-btn .name { font-size: 13px; font-weight: 800; color: #334155; }
    .es-sev-btn .desc { font-size: 10.5px; color: #94a3b8; margin-top: 2px; }
    .es-sev-btn.active.low { border-color: #16803d; background: #f0fdf4; }
    .es-sev-btn.active.low .name { color: #16803d; }
    .es-sev-btn.active.mid { border-color: #a16207; background: #fefce8; }
    .es-sev-btn.active.mid .name { color: #a16207; }
    .es-sev-btn.active.urgent { border-color: #dc2626; background: #fef2f2; }
    .es-sev-btn.active.urgent .name { color: #dc2626; }

    .es-urgent-warning { margin-top: 10px; background: #fef2f2; border: 1px solid #fecaca; border-radius: 8px; padding: 10px 12px; font-size: 12px; color: #b91c1c; font-weight: 600; }

    .es-submit-bar { display: flex; gap: 10px; margin-top: 20px; }
    .es-btn { flex: 1; padding: 13px; border-radius: 10px; border: none; font-size: 14px; font-weight: 700; cursor: pointer; }
    .es-btn-ghost { flex: 0 0 auto; padding: 13px 18px; background: #f1f5f9; color: #334155; }
    .es-btn-ghost:hover { background: #e2e8f0; }
    .es-btn-primary { background: #0566d9; color: #fff; }
    .es-btn-primary:hover { opacity: 0.9; }
    .es-btn-primary.urgent { background: #dc2626; }

    .es-history-empty { text-align: center; color: #94a3b8; font-size: 12.5px; padding: 30px 10px; }
    .es-history-item { padding: 14px 2px; border-top: 1px solid #f1f5f9; }
    .es-history-item:first-child { border-top: none; }
    .es-history-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 4px; }
    .es-history-eq { font-size: 13px; font-weight: 800; color: #0f172a; }
    .es-history-type { font-size: 12px; color: #64748b; margin-bottom: 4px; }
    .es-history-note { font-size: 12.5px; color: #334155; line-height: 1.5; margin-bottom: 6px; }
    .es-history-photo { display: block; max-width: 100%; max-height: 160px; border-radius: 8px; border: 1px solid #e2e8f0; margin-bottom: 8px; object-fit: cover; }
    .es-history-meta { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #94a3b8; }

    .es-tag { font-size: 10.5px; font-weight: 700; padding: 3px 8px; border-radius: 10px; white-space: nowrap; }
    .es-sev-low { background: #dcfce7; color: #16803d; }
    .es-sev-mid { background: #fef9c3; color: #a16207; }
    .es-sev-urgent { background: #fee2e2; color: #b91c1c; }
    .es-status-received { background: #f1f5f9; color: #64748b; }
    .es-status-checking { background: #fef9c3; color: #a16207; }
    .es-status-resolved { background: #dcfce7; color: #16803d; }
  `;

  return (
    <div className="es">
      <style>{styles}</style>

      <div className="es-header">
        <h2>🛠 설비 이상 신고</h2>
        <p>설비 이상을 발견하면 즉시 신고하세요. 긴급 신고는 관리자에게 바로 알림이 전송됩니다.</p>
      </div>

      <div className="es-grid">
        {/* 좌측: 신고 폼 */}
        <div className="es-card">
          <div className="es-card-title">이상 신고 작성</div>

          <div className="es-field">
            <label>설비 / 공정 선택</label>
            <select className="es-select" value={equipment} onChange={(e) => setEquipment(e.target.value)}>
              {EQUIPMENT_LIST.map((eq) => (
                <option key={eq.id} value={eq.id}>{eq.name}</option>
              ))}
            </select>
          </div>

          <div className="es-field">
            <label>이상 유형</label>
            <select className="es-select" value={issueType} onChange={(e) => setIssueType(e.target.value)}>
              {ISSUE_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          <div className="es-field">
            <label>심각도</label>
            <div className="es-sev-grid">
              {SEVERITY.map((s) => (
                <div
                  key={s.key}
                  className={`es-sev-btn ${s.key} ${severity === s.key ? "active" : ""}`}
                  onClick={() => setSeverity(s.key)}
                >
                  <div className="name">{s.label}</div>
                  <div className="desc">{s.desc}</div>
                </div>
              ))}
            </div>
            {severity === "urgent" && (
              <div className="es-urgent-warning">
                ⚠ 긴급으로 신고하면 관리자에게 즉시 알림이 전송됩니다.
              </div>
            )}
          </div>

          <div className="es-field">
            <label>상세 증상</label>
            <textarea
              className="es-textarea"
              placeholder="언제부터, 어떤 증상이 나타났는지 구체적으로 작성해주세요."
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>

          <div className="es-field">
            <label>사진 첨부 (선택)</label>
            <input
              type="file"
              accept="image/*"
              className="es-file-input"
              onChange={(e) => setPhoto(e.target.files[0] || null)}
            />
          </div>

          <div className="es-submit-bar">
            <button type="button" className="es-btn es-btn-ghost" onClick={resetForm}>
              초기화
            </button>
            <button
              type="button"
              className={`es-btn es-btn-primary ${severity === "urgent" ? "urgent" : ""}`}
              onClick={handleSubmit}
            >
              {flash ? "신고 접수됨 ✓" : severity === "urgent" ? "🚨 긴급 신고하기" : "신고하기"}
            </button>
          </div>
        </div>

        {/* 우측: 신고 이력 */}
        <div className="es-card">
          <div className="es-card-title">내 신고 이력 ({myHistory.length}건)</div>
          {myHistory.length === 0 ? (
            <div className="es-history-empty">아직 신고한 내역이 없습니다.</div>
          ) : (
            myHistory.map((h) => (
              <div key={h.id} className="es-history-item">
                <div className="es-history-top">
                  <span className="es-history-eq">{h.equipment}</span>
                  <span className={`es-tag ${SEVERITY_STYLE[h.severity].cls}`}>
                    {SEVERITY_STYLE[h.severity].label}
                  </span>
                </div>
                <div className="es-history-type">{h.issueType}</div>
                <div className="es-history-note">{h.note}</div>
                {h.photoDataUrl && (
                  <img className="es-history-photo" src={h.photoDataUrl} alt={h.photoName || "첨부 사진"} />
                )}
                {h.photoName && !h.photoDataUrl && (
                  <div className="es-history-note">📎 {h.photoName}</div>
                )}
                <div className="es-history-meta">
                  <span>{h.time}</span>
                  <span className={`es-tag ${STATUS_STYLE[h.status].cls}`}>
                    {STATUS_STYLE[h.status].label}
                  </span>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default EquipmentIssuePage;