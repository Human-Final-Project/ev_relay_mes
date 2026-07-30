import httpClient, { refreshCsrfToken } from "./httpClient";

const query = (params = {}) => ({
  params: Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== "" && value !== null && value !== undefined)
  ),
});

async function mutate(method, url, data, config) {
  await refreshCsrfToken();
  return httpClient.request({ method, url, data, ...config });
}

const MesApi = {
  getDashboardSummary: () => httpClient.get("/api/mes/dashboard/summary"),
  getCollectorStatus: () => httpClient.get("/api/mes/collector-status"),
  getWorkOrders: (params) => httpClient.get("/api/work-orders", query(params)),
  createWorkOrder: (data) => mutate("post", "/api/work-orders", data),
  updateWorkOrder: (id, data) => mutate("put", `/api/work-orders/${id}`, data),
  releaseWorkOrder: (id) => mutate("post", `/api/work-orders/${id}/release`),
  deleteWorkOrder: (id) => mutate("delete", `/api/work-orders/${id}`),
  createLot: (workOrderId, inputQty) =>
    mutate("post", `/api/work-orders/${workOrderId}/lots`, { inputQty: Number(inputQty) }),

  getLots: (params) => httpClient.get("/api/lots", query(params)),
  getPipelineLots: async () => {
    const response = await httpClient.get("/api/lots");
    const processOrder = ["OP20", "OP30", "OP40_OP50", "OP60", "OP70", "OP80"];
    const activeLots = (response.data || []).filter((lot) =>
      ["RUNNING", "HOLD"].includes(lot.status)
    );
    return {
      data: activeLots.sort((left, right) => {
        const orderCompare = Number(left.workOrderId || 0) - Number(right.workOrderId || 0);
        if (orderCompare !== 0) return orderCompare;
        const roundCompare = Number(left.productionRound || 0) - Number(right.productionRound || 0);
        if (roundCompare !== 0) return roundCompare;
        return processOrder.indexOf(left.currentProcessCode) - processOrder.indexOf(right.currentProcessCode);
      }),
    };
  },
  getLotByNo: (lotNo) => httpClient.get(`/api/lots/by-no/${encodeURIComponent(lotNo)}`),
  getLotCommands: (lotNo) =>
    httpClient.get(`/api/lots/by-no/${encodeURIComponent(lotNo)}/commands`),
  getLotResponsibles: (lotNo) =>
    httpClient.get(`/api/lots/by-no/${encodeURIComponent(lotNo)}/responsibles`),
  getLotMaterialUsages: (lotNo) =>
    httpClient.get(`/api/lots/by-no/${encodeURIComponent(lotNo)}/materials`),
  getLotTimelineAlarms: (lotNo) =>
    httpClient.get("/api/machines/alarms", query({ lotNo })),
  getProductionLogs: (params) => httpClient.get("/api/production-logs", query(params)),

  getMachines: () => httpClient.get("/api/machines"),
  getMachineAlarms: (params) => httpClient.get("/api/machines/alarms", query(params)),
  clearMachineAlarm: (id) => mutate("patch", `/api/machines/alarms/${id}/clear`),
  getMachineAssignments: (machineId) =>
    httpClient.get(`/api/machines/${machineId}/assignments`),
  assignResponsible: (machineId, workerId) =>
    mutate("put", `/api/machines/${machineId}/responsible`, { workerId: Number(workerId) }),
  addMachineWorker: (machineId, workerId) =>
    mutate("post", `/api/machines/${machineId}/workers`, { workerId: Number(workerId) }),
  removeMachineWorker: (machineId, workerId) =>
    mutate("delete", `/api/machines/${machineId}/assignments/${workerId}`),

  getInspections: (params) => httpClient.get("/api/quality/inspections", query(params)),
  getDefects: (params) => httpClient.get("/api/quality/defects", query(params)),
  getInspectionStandards: () => httpClient.get("/api/inspection-standards"),
  updateInspectionLimits: (id, data) =>
    mutate("patch", `/api/inspection-standards/${id}/limits`, data),

  getMaterialLots: () => httpClient.get("/api/material-lots"),
  createMaterialLot: (data) => mutate("post", "/api/material-lots", data),
  updateMaterialLotStatus: (id, status) =>
    mutate("patch", `/api/material-lots/${id}/status`, undefined, { params: { status } }),
  getItems: () => httpClient.get("/api/items"),
  createItem: (data) => mutate("post", "/api/items", data),
  updateItem: (code, data) => mutate("put", `/api/items/${encodeURIComponent(code)}`, data),
  setItemActive: (code, active) =>
    mutate("patch", `/api/items/${encodeURIComponent(code)}/active`, undefined, { params: { active } }),
  getBoms: () => httpClient.get("/api/boms"),
  createBom: (data) => mutate("post", "/api/boms", data),
  updateBom: (id, data) => mutate("put", `/api/boms/${id}`, data),
  setBomActive: (id, active) =>
    mutate("patch", `/api/boms/${id}/active`, undefined, { params: { active } }),
  getProcesses: () => httpClient.get("/api/processes"),
  getDefectCodes: () => httpClient.get("/api/defect-codes"),
  getAlarmCodes: () => httpClient.get("/api/alarm-codes"),

  getWorkers: (params) => httpClient.get("/api/workers", query(params)),
  createWorker: (data) => mutate("post", "/api/workers", data),
  updateWorker: (id, data) => mutate("put", `/api/workers/${id}`, data),
  getMembers: () => httpClient.get("/api/members"),
  createMember: (data) => mutate("post", "/api/members", data),
  updateMember: (id, data) => mutate("patch", `/api/members/${id}`, data),
  getNotices: () => httpClient.get("/api/notices"),
  createNotice: (data) => mutate("post", "/api/notices", data),
  updateNotice: (id, data) => mutate("put", `/api/notices/${id}`, data),
};

export default MesApi;
