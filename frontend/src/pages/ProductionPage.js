import React, { useState } from "react";

// -------------------------------------------------------------
// [데이터 정의] 메인 화면용 데이터
// -------------------------------------------------------------
const machineAlerts = [
  { id: 1, type: "red", title: "Line C - 설비 비상 정지", desc: "Robot Arm #3 Motor Overload Detected", time: "14:38:22" },
  { id: 2, type: "yellow", title: "자재 부족 알림 (Line B)", desc: "Housing Component Stock < 5%", time: "14:20:15" },
  { id: 3, type: "blue", title: "Line A 품질 관리 샘플링", desc: "Random Inspection Required (Every 500 units)", time: "13:55:04" },
];

const activeOrders = [
  { id: "WO-20231102-01", product: "EV-RELAY-150A", target: 2500, current: 2250, progress: 90, priority: "긴급", priorityClass: "p-critical" },
  { id: "WO-20231102-02", product: "BATT-CONN-HIGH", target: 1200, current: 840, progress: 70, priority: "보통", priorityClass: "p-normal" },
  { id: "WO-20231102-03", product: "SSR-DC-CONTROLLER", target: 3000, current: 150, progress: 5, priority: "주의", priorityClass: "p-warning" },
  { id: "WO-20231102-04", product: "AUX-SWITCH-GEN3", target: 1000, current: 980, progress: 98, priority: "보통", priorityClass: "p-normal" },
];

const lineDetails = {
  A: {
    title: "Line A - Relay Assembly",
    subtitle: "실시간 생산 공정 상세 모니터링",
    listenerActive: true,
    metrics: {
      shiftProduction: "1,248",
      shiftUnit: "EA",
      shiftTrend: "↗ 목표 대비 +4.2%",
      achievementRate: 83.2,
      achievementTarget: "목표: 1,500 EA",
      yieldRate: 99.85,
      yieldNote: "✓ 품질 기준 충족",
      cycleTime: 12.4,
      cycleStandard: 12.0,
    },
    flow: [
      { icon: "☰", name: "Coil Winding", tag: "T: 24.5℃", state: "active" },
      { icon: "⚙️", name: "Housing Assy", tag: "P: 6.2 bar", state: "active" },
      { icon: "⚡", name: "Laser Welding", tag: "Voltage High", state: "error" },
      { icon: "👁", name: "Final Inspection", tag: "Wait...", state: "wait" },
    ],
    sensor: {
      model: "📟 Laser Welder LW-402",
      rpm: "3,422 RPM",
      vibration: "0.024 mm/s²",
      vibrationBars: [20, 50, 30, 70, 40],
      metrics: [
        { label: "Voltage", value: "242.4V", sub: "Critical Limit: 240V", warn: true },
        { label: "Current", value: "4.2A", sub: "Stable Range", warn: false },
        { label: "Pressure", value: "5.8 bar", sub: "Standard 6.0", warn: false },
      ],
    },
    operators: [
      { name: "이동수", role: "공정 리더 (Leader)", img: "👨‍✈️" },
      { name: "최지은", role: "설비 오퍼레이터 (Operator)", img: "👩‍🔧" },
      { name: "박현우", role: "검사 오퍼레이터 (Operator)", img: "👨‍💻" },
    ],
    lotHistory: [
      { lot: "L20231024-001", start: "2023-10-24 08:30", end: "2023-10-24 16:30", current: 450, target: 500, status: "생산중 (Processing)", statusClass: "status-processing" },
      { lot: "L20231024-000", start: "2023-10-24 07:00", end: "2023-10-24 08:25", current: 500, target: 500, status: "완료 (Completed)", statusClass: "status-completed" },
      { lot: "L20231023-998", start: "2023-10-23 23:00", end: "2023-10-24 06:50", current: 500, target: 500, status: "완료 (Completed)", statusClass: "status-completed" },
    ],
  },
  B: {
    title: "Line B - Connector Molding",
    subtitle: "실시간 생산 공정 상세 모니터링",
    listenerActive: true,
    metrics: {
      shiftProduction: "962",
      shiftUnit: "EA",
      shiftTrend: "↗ 목표 대비 +1.1%",
      achievementRate: 78.0,
      achievementTarget: "목표: 1,200 EA",
      yieldRate: 99.42,
      yieldNote: "✓ 품질 기준 충족",
      cycleTime: 18.7,
      cycleStandard: 18.0,
    },
    flow: [
      { icon: "🧪", name: "Resin Injection", tag: "T: 210℃", state: "active" },
      { icon: "🧊", name: "Cooling", tag: "T: 32℃", state: "active" },
      { icon: "🔩", name: "Terminal Insert", tag: "OK", state: "active" },
      { icon: "👁", name: "Final Inspection", tag: "Wait...", state: "wait" },
    ],
    sensor: {
      model: "📟 Injection Molder IM-220",
      rpm: "1,180 RPM",
      vibration: "0.011 mm/s²",
      vibrationBars: [30, 25, 45, 20, 35],
      metrics: [
        { label: "Voltage", value: "228.1V", sub: "Stable Range", warn: false },
        { label: "Current", value: "3.6A", sub: "Stable Range", warn: false },
        { label: "Pressure", value: "6.4 bar", sub: "Standard 6.5", warn: false },
      ],
    },
    operators: [
      { name: "이영희", role: "공정 리더 (Leader)", img: "👩‍✈️" },
      { name: "정우성", role: "설비 오퍼레이터 (Operator)", img: "👨‍🔧" },
    ],
    lotHistory: [
      { lot: "L20231024-B14", start: "2023-10-24 09:00", end: "2023-10-24 17:00", current: 312, target: 400, status: "생산중 (Processing)", statusClass: "status-processing" },
      { lot: "L20231024-B13", start: "2023-10-24 01:00", end: "2023-10-24 08:55", current: 400, target: 400, status: "완료 (Completed)", statusClass: "status-completed" },
    ],
  },
  C: {
    title: "Line C - High Voltage Busbar",
    subtitle: "실시간 생산 공정 상세 모니터링 · 설비 비상 정지 상태",
    listenerActive: false,
    metrics: {
      shiftProduction: "540",
      shiftUnit: "EA",
      shiftTrend: "▼ 목표 대비 -18.6%",
      achievementRate: 45.0,
      achievementTarget: "목표: 1,200 EA",
      yieldRate: 97.10,
      yieldNote: "⚠ 품질 기준 미달 위험",
      cycleTime: 26.9,
      cycleStandard: 21.0,
    },
    flow: [
      { icon: "🔧", name: "Busbar Cutting", tag: "OK", state: "active" },
      { icon: "🦾", name: "Robot Arm #3", tag: "Motor Overload", state: "error" },
      { icon: "🧯", name: "Insulation Coat", tag: "Stopped", state: "wait" },
      { icon: "👁", name: "Final Inspection", tag: "Stopped", state: "wait" },
    ],
    sensor: {
      model: "📟 Robot Arm RA-3 Controller",
      rpm: "0 RPM",
      vibration: "0.081 mm/s²",
      vibrationBars: [80, 90, 60, 95, 70],
      metrics: [
        { label: "Voltage", value: "268.9V", sub: "Critical Limit: 250V", warn: true },
        { label: "Current", value: "9.8A", sub: "Overload", warn: true },
        { label: "Pressure", value: "4.1 bar", sub: "Standard 6.0", warn: true },
      ],
    },
    operators: [
      { name: "박민수", role: "공정 리더 (Leader)", img: "👨‍✈️" },
      { name: "김도윤", role: "설비 오퍼레이터 (Operator)", img: "👨‍🔧" },
      { name: "한소희", role: "검사 오퍼레이터 (Operator)", img: "👩‍💻" },
    ],
    lotHistory: [
      { lot: "L20231024-C08", start: "2023-10-24 12:00", end: "미정 (설비 정지)", current: 90, target: 400, status: "정지 (Stopped)", statusClass: "status-stopped" },
      { lot: "L20231024-C07", start: "2023-10-24 04:00", end: "2023-10-24 11:50", current: 400, target: 400, status: "완료 (Completed)", statusClass: "status-completed" },
    ],
  },
};

const lineSummaryMain = [
  { key: "A", name: "Line A", series: "EV-RELAY-X1 SERIES", statusTag: "운영중", statusClass: "tag-running", achieve: 92, operatorInitial: "김", operatorName: "김철수 반장" },
  { key: "B", name: "Line B", series: "EV-RELAY-S2 SERIES", statusTag: "운영중", statusClass: "tag-running", achieve: 78, operatorInitial: "이", operatorName: "이영희 반장" },
  { key: "C", name: "Line C", series: "HIGH VOLTAGE BUSBAR", statusTag: "설비정지", statusClass: "tag-stopped", achieve: 45, operatorInitial: "박", operatorName: "박민수 반장", danger: true },
];

const initialEmployees = [
  { id: 101, name: "이동수", role: "공정 리더 (Leader)", isEmployed: true, assignedLine: "A", img: "👨‍✈️" },
  { id: 102, name: "최지은", role: "설비 오퍼레이터 (Operator)", isEmployed: true, assignedLine: "A", img: "👩‍🔧" },
  { id: 103, name: "박현우", role: "검사 오퍼레이터 (Operator)", isEmployed: true, assignedLine: "A", img: "👨‍💻" },
  { id: 201, name: "이영희", role: "공정 리더 (Leader)", isEmployed: true, assignedLine: "B", img: "👩‍✈️" },
  { id: 202, name: "정우성", role: "설비 오퍼레이터 (Operator)", isEmployed: true, assignedLine: "B", img: "👨‍🔧" },
  { id: 301, name: "박민수", role: "공정 리더 (Leader)", isEmployed: true, assignedLine: "C", img: "👨‍✈️" },
  { id: 302, name: "김도윤", role: "설비 오퍼레이터 (Operator)", isEmployed: true, assignedLine: "C", img: "👨‍🔧" },
  { id: 303, name: "한소희", role: "검사 오퍼레이터 (Operator)", isEmployed: true, assignedLine: "C", img: "👩‍💻" },
  
  { id: 401, name: "강동원", role: "설비 오퍼레이터 (Operator)", isEmployed: true, assignedLine: null, img: "👨‍🔧" },
  { id: 402, name: "송혜교", role: "검사 오퍼레이터 (Operator)", isEmployed: true, assignedLine: null, img: "👩‍🔧" },
  { id: 403, name: "전지현", role: "설비 오퍼레이터 (Operator)", isEmployed: false, assignedLine: null, img: "👩‍🔧" },
];

// -------------------------------------------------------------
// [CSS 스타일 정의]
// -------------------------------------------------------------
const styles = `
  .monitor-container { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; background-color: #f8fafc; padding: 24px; min-height: 100vh; box-sizing: border-box; }
  .monitor-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 24px; border-bottom: 1px solid #e2e8f0; padding-bottom: 20px; }
  .title-area h2 { margin: 0 0 6px 0; font-size: 24px; font-weight: 800; color: #0f172a; }
  .title-area p { margin: 0; font-size: 14px; color: #64748b; }
  .btn-export { background: #ffffff; border: 1px solid #cbd5e1; padding: 10px 18px; border-radius: 8px; font-size: 13px; font-weight: 600; color: #475569; cursor: pointer; transition: all 0.2s; }
  .btn-export:hover { background-color: #f1f5f9; }

  .metrics-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 24px; }
  .metric-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.02); }
  .metric-card .card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  .metric-card .label { font-size: 13px; font-weight: 700; color: #64748b; }
  .metric-card .icon-badge { font-size: 16px; width: 28px; height: 28px; background: #f1f5f9; border-radius: 6px; display: flex; align-items: center; justify-content: center; }
  .metric-card .value { font-size: 26px; font-weight: 800; color: #0f172a; margin-bottom: 6px; }
  .metric-card .value span { font-size: 13px; font-weight: 500; color: #64748b; }
  .trend-text { font-size: 12px; font-weight: 600; }
  .trend-up { color: #16a34a; }
  .trend-warn { color: #dc2626; }
  .sub-label { font-size: 12px; color: #94a3b8; font-weight: 600; }

  .status-section { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .section-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
  .section-title-row h3 { margin: 0; font-size: 16px; font-weight: 700; }
  .view-more-link { font-size: 12px; font-weight: 700; color: #0566d9; cursor: pointer; }
  .lines-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
  .line-status-card { border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; background: #ffffff; transition: box-shadow 0.2s; }
  .line-status-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
  .line-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  .line-name { font-size: 15px; font-weight: 800; }
  .line-name span { font-size: 11px; color: #64748b; display: block; }
  .status-tag { font-size: 11px; font-weight: 700; padding: 3px 8px; border-radius: 12px; }
  .tag-running { background-color: #dcfce7; color: #16803d; }
  .tag-stopped { background-color: #fee2e2; color: #b91c1c; }

  .gauge-bg { background: #e2e8f0; height: 6px; border-radius: 3px; overflow: hidden; margin: 8px 0; }
  .gauge-fill { background: #0566d9; height: 100%; }

  .operator-info { margin-top: 14px; padding-top: 10px; border-top: 1px dashed #e2e8f0; display: flex; align-items: center; gap: 8px; font-size: 12px; }
  .operator-avatar { width: 24px; height: 24px; border-radius: 50%; background: #e2e8f0; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 11px; }

  .table-container { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .table-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
  .table-header-row h3 { margin: 0; font-size: 16px; }
  .search-box { border: 1px solid #cbd5e1; padding: 6px 12px; border-radius: 6px; font-size: 12px; width: 200px; }
  .monitoring-table { width: 100%; border-collapse: collapse; }
  .monitoring-table th { background-color: #f1f5f9; color: #475569; font-size: 12px; font-weight: 700; padding: 12px 16px; border-bottom: 1px solid #e2e8f0; }
  .monitoring-table td { padding: 14px 16px; border-bottom: 1px solid #e2e8f0; font-size: 13px; }

  .progress-wrapper { display: flex; align-items: center; gap: 10px; }
  .progress-bar-bg { background: #e2e8f0; height: 8px; border-radius: 4px; overflow: hidden; width: 100%; }
  .progress-bar-fill { background: #0566d9; height: 100%; }
  .progress-text { font-size: 12px; font-weight: 700; }

  .side-alarm-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .alarm-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; border-bottom: 1px solid #f1f5f9; padding-bottom: 12px; }
  .alarm-badge { background: #fee2e2; color: #dc2626; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 12px; }
  .alarm-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
  @media (max-width: 900px) { .alarm-list { grid-template-columns: 1fr; } }
  .alarm-item { padding: 14px; border-radius: 8px; border-left: 4px solid transparent; }
  .alarm-red { background: #fff5f5; border-left-color: #ef4444; }
  .alarm-yellow { background: #fffbeb; border-left-color: #f59e0b; }
  .alarm-blue { background: #eff6ff; border-left-color: #3b82f6; }
  .alarm-item-title { font-size: 13px; font-weight: 700; }
  .alarm-item-desc { font-size: 12px; color: #64748b; margin-top: 4px; }
  .alarm-item-time { font-size: 11px; color: #94a3b8; margin-top: 6px; }

  .detail-view { animation: fadeIn 0.3s ease-in-out; }
  @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

  .detail-top-nav { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; font-size: 13px; flex-wrap: wrap; }
  .back-link { font-weight: 700; color: #475569; cursor: pointer; background: #e2e8f0; padding: 6px 12px; border-radius: 6px; transition: background 0.2s; }
  .back-link:hover { background: #cbd5e1; }
  .nav-path { color: #94a3b8; font-weight: 600; }
  .active-path { color: #0566d9; font-weight: 700; }

  .line-switch-tabs { display: flex; gap: 6px; background: #f1f5f9; padding: 4px; border-radius: 8px; }
  .line-tab { padding: 6px 14px; font-size: 12px; font-weight: 700; color: #64748b; cursor: pointer; border-radius: 6px; transition: all 0.2s; }
  .line-tab:hover { color: #0f172a; }
  .line-tab-active { background: #ffffff; color: #0566d9; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }

  .listener-status { margin-left: auto; font-size: 12px; font-weight: 700; color: #0f172a; display: flex; align-items: center; gap: 6px; }
  .status-dot { width: 8px; height: 8px; background-color: #22c55e; border-radius: 50%; display: inline-block; }

  .detail-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
  .detail-title-area h2 { margin: 0; font-size: 26px; font-weight: 800; color: #0f172a; }
  .detail-title-area p { margin: 4px 0 0 0; font-size: 14px; color: #64748b; }

  .detail-action-buttons { display: flex; gap: 10px; }
  .detail-action-buttons button { padding: 10px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; cursor: pointer; border: none; transition: opacity 0.2s; }
  .detail-action-buttons button:hover { opacity: 0.9; }
  .btn-secondary { background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1 !important; }
  .btn-warning { background: #fee2e2; color: #dc2626; }

  .flow-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 24px; margin-bottom: 24px; }
  .flow-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
  .flow-header h3 { margin: 0; font-size: 16px; font-weight: 800; }
  .flow-legend { display: flex; gap: 14px; font-size: 12px; font-weight: 700; }
  .l-run { color: #0566d9; } .l-wait { color: #94a3b8; } .l-error { color: #ef4444; }
  .flow-steps { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; }
  .step-item { flex: 1; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 8px; }
  .step-item.active { border-color: #0566d9; background: #f0f9ff; }
  .step-item.error { border-color: #fca5a5; background: #fef2f2; }
  .step-item.wait { opacity: 0.6; }
  .step-icon { font-size: 20px; width: 40px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center; background: #e2e8f0; }
  .active .step-icon { background: #0566d9; color: white; }
  .error .step-icon { background: #ef4444; color: white; }
  .step-name { font-weight: 700; font-size: 13px; }
  .step-tag { font-size: 11px; padding: 3px 8px; background: #e2e8f0; border-radius: 6px; font-family: monospace; }
  .err-tag { background: #fca5a5; color: #b91c1c; font-weight: bold; }
  .flow-arrow { font-size: 20px; color: #cbd5e1; margin: 0 10px; }

  .detail-split-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 24px; }
  @media (max-width: 992px) { .detail-split-grid { grid-template-columns: 1fr; } }

  .sensor-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .sensor-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
  .sensor-header h3 { margin: 0; font-size: 15px; }
  .sensor-model { font-size: 12px; color: #64748b; font-weight: 600; }
  .charts-container { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px; }
  .mock-chart { background: #f8fafc; border: 1px solid #f1f5f9; border-radius: 8px; padding: 16px; min-height: 120px; display: flex; flex-direction: column; justify-content: space-between; }
  .mock-chart p { margin: 0; font-size: 12px; font-weight: 700; color: #64748b; }
  .chart-value { font-size: 24px; font-weight: 800; color: #0566d9; }
  .chart-value-sub { font-size: 14px; font-weight: 700; color: #475569; }
  .chart-bar-deco { display: flex; align-items: flex-end; gap: 6px; height: 40px; }
  .chart-bar-deco div { flex: 1; background: #38bdf8; border-radius: 2px; }
  .sensor-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; border-top: 1px dashed #e2e8f0; padding-top: 16px; }
  .s-metric { display: flex; flex-direction: column; gap: 4px; }
  .s-label { font-size: 11px; font-weight: 700; color: #64748b; }
  .s-val { font-size: 18px; font-weight: 800; }
  .s-sub { font-size: 11px; color: #94a3b8; }
  .text-red { color: #ef4444; }

  .operator-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; }
  .operator-card h3 { margin: 0 0 16px 0; font-size: 15px; }
  .operator-list { display: flex; flex-direction: column; gap: 12px; }
  .op-item { display: flex; align-items: center; gap: 12px; padding: 10px; border: 1px solid #f1f5f9; border-radius: 8px; background: #f8fafc; }
  .op-avatar { font-size: 20px; }
  .op-name { font-size: 13px; font-weight: 700; color: #0f172a; }
  .op-role { font-size: 11px; color: #64748b; }

  .btn-add-operator { margin-top: 10px; background: transparent; border: 1px dashed #cbd5e1; padding: 10px; border-radius: 8px; font-size: 12px; font-weight: 700; color: #64748b; cursor: pointer; transition: background 0.2s; }
  .btn-add-operator:hover { background: #f1f5f9; border-color: #94a3b8; }

  .status-badge { font-size: 11px; font-weight: 700; padding: 4px 8px; border-radius: 4px; }
  .status-processing { background: #e0f2fe; color: #0369a1; }
  .status-completed { background: #dcfce7; color: #15803d; }
  .status-stopped { background: #fee2e2; color: #b91c1c; }
  .btn-text-link { background: transparent; border: none; color: #0566d9; font-size: 12px; font-weight: 700; cursor: pointer; }
  .btn-text-link:hover { text-decoration: underline; }
  .btn-icon { background: #ffffff; border: 1px solid #cbd5e1; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; }

  .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
  .modal-content { background: #ffffff; width: 440px; border-radius: 12px; padding: 24px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); }
  .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
  .modal-header h3 { margin: 0; font-size: 18px; }
  .btn-close { background: none; border: none; font-size: 18px; cursor: pointer; color: #64748b; }
  .modal-filter { display: flex; gap: 16px; margin-bottom: 20px; background: #f8fafc; padding: 12px; border-radius: 8px; font-size: 14px; font-weight: 600; }
  .modal-employee-list { max-height: 350px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
  .employee-item { display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; }
  .emp-info { display: flex; align-items: center; gap: 12px; }
  .emp-name { font-size: 14px; font-weight: 700; color: #0f172a; }
  .emp-role { font-size: 12px; color: #64748b; margin-top: 2px; }
  
  .btn-assign { background: #0566d9; color: white; border: none; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 700; cursor: pointer; }
  .btn-assign:hover { opacity: 0.85; }
  .assign-actions { display: flex; gap: 8px; align-items: center; }
  .select-move { border: 1px solid #cbd5e1; border-radius: 6px; padding: 6px; font-size: 12px; color: #475569; outline: none; }
  .btn-remove { background: #fee2e2; color: #dc2626; border: none; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 700; cursor: pointer; }
  .btn-remove:hover { background: #fca5a5; }
`;

function ProductionPage() {
  const [view, setView] = useState("main");
  const [selectedLine, setSelectedLine] = useState("A");
  const [selectedLot, setSelectedLot] = useState(null); 
  const [lotFilter, setLotFilter] = useState("전체"); 
  const isAdmin = localStorage.getItem("userRole") === "admin"; 
  const [showAssignModal, setShowAssignModal] = useState(false); 
  const [assignFilter, setAssignFilter] = useState("unassigned"); 
  const [employees, setEmployees] = useState(initialEmployees); 
  const [linesData, setLinesData] = useState(lineDetails); 

  const goToDetail = (lineKey) => {
    setSelectedLine(lineKey);
    setView("detail");
  };

  const detail = linesData[selectedLine];

  const handleAssignOperator = (employee) => {
    const updatedLineDetails = { ...linesData };
    updatedLineDetails[selectedLine].operators.push({
      name: employee.name,
      role: employee.role,
      img: employee.img
    });
    setLinesData(updatedLineDetails);

    setEmployees(employees.map(emp => 
      emp.id === employee.id ? { ...emp, assignedLine: selectedLine } : emp
    ));
    
    alert(`${employee.name}님이 Line ${selectedLine}에 배정되었습니다.`);
  };

  const handleRemoveOperator = (employee) => {
    if (!window.confirm(`${employee.name}님을 Line ${employee.assignedLine}에서 배정 해제하시겠습니까?`)) return;

    const updatedLineDetails = { ...linesData };
    const currentLine = employee.assignedLine;
    
    if (currentLine && updatedLineDetails[currentLine]) {
      updatedLineDetails[currentLine].operators = updatedLineDetails[currentLine].operators.filter(
        op => op.name !== employee.name
      );
    }
    setLinesData(updatedLineDetails);

    setEmployees(employees.map(emp => 
      emp.id === employee.id ? { ...emp, assignedLine: null } : emp
    ));
  };

  const handleMoveOperator = (employee, targetLine) => {
    if (!targetLine || employee.assignedLine === targetLine) return;
    if (!window.confirm(`${employee.name}님을 Line ${targetLine}(으)로 이동하시겠습니까?`)) return;

    const updatedLineDetails = { ...linesData };
    const currentLine = employee.assignedLine;

    if (currentLine && updatedLineDetails[currentLine]) {
      updatedLineDetails[currentLine].operators = updatedLineDetails[currentLine].operators.filter(
        op => op.name !== employee.name
      );
    }

    updatedLineDetails[targetLine].operators.push({
      name: employee.name,
      role: employee.role,
      img: employee.img
    });

    setLinesData(updatedLineDetails);

    setEmployees(employees.map(emp => 
      emp.id === employee.id ? { ...emp, assignedLine: targetLine } : emp
    ));
  };

  const handleViewLotDetail = (lotItem) => {
    setSelectedLot(lotItem);
    setView("lotDetail");
  };

  return (
    <div className="monitor-container">
      <style>{styles}</style>

      {view === "main" ? (
        <div>
          <div className="monitor-header">
            <div className="title-area">
              <h2>생산 모니터링</h2>
              <p>실시간 공정별 가동 현황 및 작업진행 스케줄을 원격 모니터링합니다.</p>
            </div>
            <button className="btn-export">📥 보고서 출력</button>
          </div>

          <div className="metrics-grid">
            <div className="metric-card">
              <div className="card-top"><span className="label">금일 총 지시 건수</span><span className="icon-badge">📋</span></div>
              <div className="value">24 <span>건</span></div>
              <div className="trend-text trend-up">↗ 전일 대비 12% 증가</div>
            </div>
            <div className="metric-card">
              <div className="card-top"><span className="label">진행 중인 작업</span><span className="icon-badge">⚙️</span></div>
              <div className="value">18 <span>건</span></div>
              <div className="gauge-bg"><div className="gauge-fill" style={{ width: "75%" }}></div></div>
            </div>
            <div className="metric-card">
              <div className="card-top"><span className="label">금일 생산 완료</span><span className="icon-badge">✅</span></div>
              <div className="value">5,240 <span>EA</span></div>
              <div className="trend-text trend-up">목표 대비 84.5% 달성</div>
            </div>
            <div className="metric-card">
              <div className="card-top"><span className="label">긴급 조치 사항</span><span className="icon-badge" style={{ background: "#fee2e2", color: "#dc2626" }}>⚠️</span></div>
              <div className="value" style={{ color: "#dc2626" }}>03 <span>건</span></div>
              <div className="trend-text trend-warn">실시간 조치 및 관제 필요</div>
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            <div className="status-section">
              <div className="section-title-row">
                <h3>실시간 생산 라인 상태</h3>
                <span className="view-more-link" onClick={() => goToDetail(selectedLine)}>상세 현황 보기 ❯</span>
              </div>
              <div className="lines-grid">
                {lineSummaryMain.map((line) => (
                  <div key={line.key} className="line-status-card" onClick={() => goToDetail(line.key)} style={{ cursor: "pointer", border: line.danger ? "1.5px solid #fca5a5" : undefined }}>
                    <div className="line-card-header">
                      <div className="line-name" style={{ color: line.danger ? "#dc2626" : undefined }}>{line.name} <span>{line.series}</span></div>
                      <span className={`status-tag ${line.statusClass}`}>{line.statusTag}</span>
                    </div>
                    <div className="line-progress-info">
                      <span>목표 달성율</span>
                      <span style={{ color: line.danger ? "#dc2626" : undefined }}>{line.achieve}%</span>
                    </div>
                    <div className="gauge-bg" style={{ background: line.danger ? "#fee2e2" : undefined }}>
                      <div className="gauge-fill" style={{ width: `${line.achieve}%`, backgroundColor: line.danger ? "#ef4444" : undefined }}></div>
                    </div>
                    <div className="operator-info">
                      <div className="operator-avatar" style={line.danger ? { background: "#fca5a5", color: "#b91c1c" } : undefined}>{line.operatorInitial}</div>
                      <span>{line.operatorName}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="side-alarm-card">
              <div className="alarm-card-header"><h3>🚨 실시간 설비 알람</h3><span className="alarm-badge">3 New</span></div>
              <div className="alarm-list">
                {machineAlerts.map((alert) => (
                  <div key={alert.id} className={`alarm-item alarm-${alert.type}`}>
                    <div className="alarm-item-title">{alert.title}</div>
                    <div className="alarm-item-desc">{alert.desc}</div>
                    <div className="alarm-item-time">{alert.time}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      ) : view === "lotDetail" ? (
        <LotDetailView
        lotData={selectedLot}
        onBack={() => setView("detail")}
        />
      ) : (
        <div className="detail-view">
          <div className="detail-top-nav">
            <div className="back-link" onClick={() => setView("main")}>⬅ 목록으로</div>
            <div className="nav-path"><span>생산 관리</span> &gt; <span className="active-path">{detail.title.split(" - ")[0]} 상세 현황</span></div>
            <div className="line-switch-tabs">
              {Object.keys(linesData).map((key) => (
                <span key={key} className={`line-tab ${selectedLine === key ? "line-tab-active" : ""}`} onClick={() => setSelectedLine(key)}>Line {key}</span>
              ))}
            </div>
            <div className="listener-status">
              <span className="status-dot" style={{ backgroundColor: detail.listenerActive ? "#22c55e" : "#ef4444" }}></span>
              {detail.listenerActive ? "TCP Listener Active" : "TCP Listener Down"} 📡 🕒
            </div>
          </div>

          <div className="detail-header-row">
            <div className="detail-title-area">
              <h2>{detail.title}</h2>
              <p>{detail.subtitle}</p>
            </div>
            <div className="detail-action-buttons">
              <button className="btn-secondary">📋 LOT 라벨 출력</button>
              <button className="btn-warning">🚨 이슈 보고</button>
            </div>
          </div>

          <div className="metrics-grid">
            <div className="metric-card">
              <span className="label">현재 시프트 생산량</span>
              <div className="value">{detail.metrics.shiftProduction} <span>{detail.metrics.shiftUnit}</span></div>
              <span className={`trend-text ${detail.metrics.shiftTrend.startsWith("▼") ? "trend-warn" : "trend-up"}`}>{detail.metrics.shiftTrend}</span>
            </div>
            <div className="metric-card">
              <span className="label">목표 달성률</span>
              <div className="value">{detail.metrics.achievementRate} <span>%</span></div>
              <div className="gauge-bg"><div className="gauge-fill" style={{ width: `${detail.metrics.achievementRate}%` }}></div></div>
              <span className="sub-label">{detail.metrics.achievementTarget}</span>
            </div>
            <div className="metric-card">
              <span className="label">양품률 (Yield)</span>
              <div className="value">{detail.metrics.yieldRate} <span>%</span></div>
              <span className={`trend-text ${detail.metrics.yieldNote.startsWith("⚠") ? "trend-warn" : "trend-up"}`}>{detail.metrics.yieldNote}</span>
            </div>
            <div className="metric-card">
              <span className="label">현재 사이클 타임</span>
              <div className="value">{detail.metrics.cycleTime} <span>sec</span></div>
              <span className="sub-label">⏱ 표준: {detail.metrics.cycleStandard} sec</span>
            </div>
          </div>

          <div className="flow-card">
            <div className="flow-header">
              <h3>실시간 공정 흐름</h3>
              <div className="flow-legend"><span className="l-run">● 가동중</span><span className="l-wait">● 대기</span><span className="l-error">● 오류</span></div>
            </div>
            <div className="flow-steps">
              {detail.flow.map((step, idx) => (
                <React.Fragment key={idx}>
                  <div className={`step-item ${step.state}`}>
                    <div className="step-icon">{step.icon}</div>
                    <div className="step-name">{step.name}</div>
                    <div className={`step-tag ${step.state === "error" ? "err-tag" : ""}`}>{step.tag}</div>
                  </div>
                  {idx < detail.flow.length - 1 && <div className="flow-arrow">➔</div>}
                </React.Fragment>
              ))}
            </div>
          </div>

          <div className="detail-split-grid">
            <div className="sensor-card">
              <div className="sensor-header">
                <h3>주요 설비 센서 데이터</h3><span className="sensor-model">{detail.sensor.model}</span>
              </div>
              <div className="charts-container">
                <div className="mock-chart">
                  <p>Motor RPM Tracking</p><div className="chart-value">{detail.sensor.rpm}</div>
                </div>
                <div className="mock-chart">
                  <p>Vibration Level (z-axis)</p>
                  <div className="chart-bar-deco">{detail.sensor.vibrationBars.map((h, i) => (<div key={i} style={{ height: `${h}%` }}></div>))}</div>
                  <div className="chart-value-sub">{detail.sensor.vibration}</div>
                </div>
              </div>
              <div className="sensor-metrics">
                {detail.sensor.metrics.map((m, i) => (
                  <div className="s-metric" key={i}>
                    <span className="s-label">{m.label}</span>
                    <span className={`s-val ${m.warn ? "text-red" : ""}`}>{m.value}</span>
                    <span className="s-sub">{m.sub}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="operator-card">
              <h3>현장 투입 인원</h3>
              <div className="operator-list">
                {detail.operators.map((op, index) => (
                  <div className="op-item" key={index}>
                    <div className="op-avatar">{op.img}</div>
                    <div className="op-details">
                      <div className="op-name">{op.name}</div>
                      <div className="op-role">{op.role}</div>
                    </div>
                    {isAdmin && (
                      <button 
                        className="btn-text-link" 
                        style={{ color: '#ef4444', marginLeft: 'auto' }}
                        onClick={() => {
                          const emp = employees.find(e => e.name === op.name);
                          if(emp) handleRemoveOperator(emp);
                        }}
                      >
                        해제
                      </button>
                    )}
                  </div>
                ))}
                
                {isAdmin && (
                  <button className="btn-add-operator" onClick={() => setShowAssignModal(true)}>
                    + 인원 배정 및 관리
                  </button>
                )}
              </div>
            </div>
          </div>

            <div className="table-container" style={{ marginTop: "24px" }}>
              <div className="table-header-row">
                <h3>최근 LOT 생산 이력</h3>
                <div style={{ display: "flex", gap: "10px" }}>
                  {["전체", "생산중", "완료", "정지"].map(status => (
                    <button 
                      key={status} 
                      className={`btn-icon ${lotFilter === status ? 'btn-active' : ''}`}
                      onClick={() => setLotFilter(status)}
                    >
                      {status}
                    </button>
                  ))}
                  <button className="btn-icon">📥 Export</button>
                </div>
              </div>
              <table className="monitoring-table">
                <thead>
                  <tr><th>LOT 번호</th><th>시작 시간</th><th>예상 종료 시간</th><th>현재 수량 / 목표</th><th>상태</th><th>관리</th></tr>
                </thead>
                <tbody>
                  {detail.lotHistory
                    .filter(item => {
                      if (lotFilter === "전체") return true;
                      return item.status.includes(lotFilter);
                    })
                    .map((item, idx) => (
                      <tr key={idx}>
                        <td className="wo-num">{item.lot}</td>
                        <td>{item.start}</td>
                        <td>{item.end}</td>
                        <td>
                          <div className="progress-wrapper">
                            <span style={{ fontWeight: 600, minWidth: "60px" }}>{item.current} / {item.target}</span>
                            <div className="progress-bar-bg" style={{ width: "120px" }}><div className="progress-bar-fill" style={{ width: `${(item.current / item.target) * 100}%` }}></div></div>
                          </div>
                        </td>
                        <td><span className={`status-badge ${item.statusClass}`}>{item.status}</span></td>
                        <td>
                          <button className="btn-text-link" onClick={() => handleViewLotDetail(item)}>상세보기</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
      )}

      {showAssignModal && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <h3>인원 배정 관리</h3>
              <button className="btn-close" onClick={() => setShowAssignModal(false)}>✕</button>
            </div>
            
            <div className="modal-filter">
              <label>
                <input 
                  type="radio" 
                  value="unassigned" 
                  checked={assignFilter === "unassigned"} 
                  onChange={(e) => setAssignFilter(e.target.value)} 
                /> 미배정 인원
              </label>
              <label>
                <input 
                  type="radio" 
                  value="assigned" 
                  checked={assignFilter === "assigned"} 
                  onChange={(e) => setAssignFilter(e.target.value)} 
                /> 배정된 인원
              </label>
            </div>

            <div className="modal-employee-list">
              {employees
                .filter(emp => emp.isEmployed === true)
                .filter(emp => assignFilter === "unassigned" ? emp.assignedLine === null : emp.assignedLine !== null)
                .map(emp => (
                  <div key={emp.id} className="employee-item">
                    <div className="emp-info">
                      <div className="op-avatar">{emp.img}</div>
                      <div>
                        <div className="emp-name">{emp.name}</div>
                        <div className="emp-role">{emp.role} {emp.assignedLine && <strong style={{color:'#0566d9'}}>(Line {emp.assignedLine})</strong>}</div>
                      </div>
                    </div>
                    
                    {assignFilter === "unassigned" && (
                      <button className="btn-assign" onClick={() => handleAssignOperator(emp)}>Line {selectedLine}에 배정</button>
                    )}
                    
                    {assignFilter === "assigned" && (
                      <div className="assign-actions">
                        <select 
                          className="select-move"
                          onChange={(e) => {
                            handleMoveOperator(emp, e.target.value);
                            e.target.value = ""; 
                          }}
                        >
                          <option value="">라인 이동...</option>
                          {Object.keys(linesData).filter(l => l !== emp.assignedLine).map(l => (
                            <option key={l} value={l}>Line {l}로 이동</option>
                          ))}
                        </select>
                        <button className="btn-remove" onClick={() => handleRemoveOperator(emp)}>해제</button>
                      </div>
                    )}
                  </div>
              ))}
              {employees.filter(emp => emp.isEmployed && (assignFilter === "unassigned" ? emp.assignedLine === null : emp.assignedLine !== null)).length === 0 && (
                <div style={{ textAlign: 'center', padding: '20px', color: '#64748b' }}>해당하는 사원이 없습니다.</div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const LotDetailView = ({ lotData, onBack }) => {
  if (!lotData) return null;

  return (
    <div className="detail-view">
      {/* 1. 상단 내비게이션 */}
      <div className="detail-top-nav">
        <div className="back-link" onClick={onBack}>⬅ 라인 상세로 돌아가기</div>
        <div className="nav-path"><span>생산 관리</span> &gt; <span>LOT 상세</span> &gt; <span className="active-path">{lotData.lot}</span></div>
      </div>

      {/* 2. 헤더 정보 */}
      <div className="detail-header-row">
        <div className="detail-title-area">
          <h2>LOT No. {lotData.lot}</h2>
          <p>현재 상태: <span className={`status-badge ${lotData.statusClass}`}>{lotData.status}</span></p>
        </div>
        <div className="detail-action-buttons">
          <button className="btn-secondary">🖨️ 성적서 출력</button>
          <button className="btn-secondary">📊 이력 로그 저장</button>
        </div>
      </div>

      {/* 3. 본문 그리드 */}
      <div className="detail-split-grid">
        {/* 좌측: 생산 지표 */}
        <div className="flow-card">
          <h3>공정 진행 현황</h3>
          <div style={{ marginTop: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
              <span>현재 생산량: <strong>{lotData.current} / {lotData.target} EA</strong></span>
              <span>진척률: {Math.round((lotData.current / lotData.target) * 100)}%</span>
            </div>
            <div className="gauge-bg" style={{ height: '16px' }}>
              <div className="gauge-fill" style={{ width: `${(lotData.current / lotData.target) * 100}%` }}></div>
            </div>
            
            <div style={{ marginTop: '30px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
              <div className="mock-chart">
                <p>투입 시간</p>
                <div className="chart-value" style={{ fontSize: '18px' }}>{lotData.start}</div>
              </div>
              <div className="mock-chart">
                <p>예상 종료</p>
                <div className="chart-value" style={{ fontSize: '18px' }}>{lotData.end}</div>
              </div>
            </div>
          </div>
        </div>

        {/* 우측: 품질 및 특이사항 */}
        <div className="sensor-card">
          <h3>품질 및 특이사항</h3>
          <div style={{ marginTop: '16px' }}>
            <div className="op-item">
              <span>✅ 품질 검사 결과: </span>
              <span style={{ fontWeight: '700', color: '#15803d' }}>Pass</span>
            </div>
            <div className="op-item" style={{ marginTop: '10px' }}>
              <span>⚠️ 작업 중지 횟수: </span>
              <span style={{ fontWeight: '700' }}>0회</span>
            </div>
            <div style={{ marginTop: '20px', padding: '12px', background: '#f8fafc', borderRadius: '8px', fontSize: '12px', color: '#64748b' }}>
              * 본 LOT는 정상적인 생산 사이클을 유지하고 있습니다. 설비 과부하 등의 알람 발생 시 자동으로 본 영역에 로그가 기록됩니다.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductionPage;