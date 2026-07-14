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
};

export default MesApi;