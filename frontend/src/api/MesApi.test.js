import MesApi from "./MesApi";
import httpClient, { refreshCsrfToken } from "./httpClient";

jest.mock("./httpClient",()=>({
  __esModule:true,
  default:{ get:jest.fn(), request:jest.fn() },
  refreshCsrfToken:jest.fn(),
}));

beforeEach(()=>{jest.clearAllMocks();refreshCsrfToken.mockResolvedValue({token:"token",headerName:"X-XSRF-TOKEN"})});

test("removes empty production search parameters",async()=>{
  httpClient.get.mockResolvedValue({data:[]});
  await MesApi.getProductionLogs({lotNo:"LOT-001",processCode:"",machineId:null});
  expect(httpClient.get).toHaveBeenCalledWith("/api/production-logs",{params:{lotNo:"LOT-001"}});
});

test("creates a lot through the work-order contract",async()=>{
  httpClient.request.mockResolvedValue({status:201});
  await MesApi.createLot(7,"100");
  expect(refreshCsrfToken).toHaveBeenCalledTimes(1);
  expect(httpClient.request).toHaveBeenCalledWith({method:"post",url:"/api/work-orders/7/lots",data:{inputQty:100}});
});

test("updates material lot status as a request parameter",async()=>{
  httpClient.request.mockResolvedValue({status:200});
  await MesApi.updateMaterialLotStatus(3,"HOLD");
  expect(httpClient.request).toHaveBeenCalledWith({method:"patch",url:"/api/material-lots/3/status",data:undefined,params:{status:"HOLD"}});
});
