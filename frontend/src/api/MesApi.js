import axios from "axios";

const HM_DOMAIN = "http://localhost:8111";

const MesApi = {
  createOrder: (productCode, targetQty) =>
    axios.post(`${HM_DOMAIN}/api/mes/order`, {
      productCode,
      targetQty: Number(targetQty),
    }),

  getOrders: () => axios.get(`${HM_DOMAIN}/api/mes/orders`),

  getMaterials: () => axios.get(`${HM_DOMAIN}/api/mes/material/stock`),

  inboundMaterial: (formData) =>
    axios.post(`${HM_DOMAIN}/api/mes/material/inbound`, formData),

  getRecentLogs: () => axios.get(`${HM_DOMAIN}/api/mes/production/recent-logs`),

  // 💡 로그인 사용자 본인 비밀번호 변경
  // 서버는 변경 성공 시 해당 사용자의 세션 버전(sessionVersion/tokenVersion)을 올려서
  // 변경 시점에 발급되어 있던 모든 기기의 토큰을 무효화해야 합니다.
  changeMyPassword: (empId, currentPassword, newPassword) =>
    axios.post(`${HM_DOMAIN}/api/mes/account/password`, {
      empId,
      currentPassword,
      newPassword,
    }),

  // 💡 관리자가 특정 사원의 임시 비밀번호를 발급
  // 서버가 임시 비밀번호를 생성해 응답으로 1회 반환하고, 해당 사원의 세션도 함께 무효화합니다.
  issueTempPassword: (empId) =>
    axios.post(`${HM_DOMAIN}/api/mes/admin/employees/${empId}/temp-password`),
};

export default MesApi;