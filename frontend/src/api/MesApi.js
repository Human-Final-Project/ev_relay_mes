import httpClient from "./httpClient";

const MesApi = {
  createOrder: (productCode, targetQty) =>
    httpClient.post("/api/mes/order", {
      productCode,
      targetQty: Number(targetQty),
    }),

  getOrders: () => httpClient.get("/api/mes/orders"),

  getMaterials: () => httpClient.get("/api/mes/material/stock"),

  getEmployees: () => httpClient.get("/api/mes/employees"),

  inboundMaterial: (formData) =>
    httpClient.post("/api/mes/material/inbound", formData),

  getRecentLogs: () => httpClient.get("/api/mes/production/recent-logs"),

  // 💡 로그인 사용자 본인 비밀번호 변경
  // 서버는 변경 성공 시 해당 사용자의 세션 버전(sessionVersion/tokenVersion)을 올려서
  // 변경 시점에 발급되어 있던 모든 기기의 토큰을 무효화해야 합니다.
  changeMyPassword: (empId, currentPassword, newPassword) =>
    httpClient.post("/api/mes/account/password", {
      empId,
      currentPassword,
      newPassword,
    }),

  // 💡 관리자가 특정 사원의 임시 비밀번호를 발급
  // 서버가 임시 비밀번호를 생성해 응답으로 1회 반환하고, 해당 사원의 세션도 함께 무효화합니다.
  issueTempPassword: (empId) =>
    httpClient.post(`/api/mes/admin/employees/${empId}/temp-password`),
};

export default MesApi;
