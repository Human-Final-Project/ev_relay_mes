import MesApi from "../api/MesApi";
import httpClient, { refreshCsrfToken } from "../api/httpClient";

jest.mock("../api/httpClient",()=>({
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

test("loads and saves a separately configured weekly production target", async () => {
  httpClient.get.mockResolvedValue({data:{}});
  httpClient.request.mockResolvedValue({data:{}});

  await MesApi.getWeeklyProductionTarget("2026-07-27");
  expect(httpClient.get).toHaveBeenCalledWith(
    "/api/dashboard/weekly-target",
    {params:{weekStart:"2026-07-27"}},
  );

  const target = {targetQty:1000, targetDefectRate:3};
  await MesApi.saveWeeklyProductionTarget("2026-07-27", target);
  expect(httpClient.request).toHaveBeenCalledWith({
    method:"put",
    url:"/api/dashboard/weekly-target",
    data:target,
    params:{weekStart:"2026-07-27"},
  });
});


test("releases a work order and starts automatic LOT production",async()=>{
  httpClient.request.mockResolvedValue({status:200});
  await MesApi.releaseWorkOrder(7);
  expect(httpClient.request).toHaveBeenCalledWith({method:"post",url:"/api/work-orders/7/release",data:undefined});
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

test("requests machine alarms captured for a LOT timeline",async()=>{
  httpClient.get.mockResolvedValue({data:[]});
  await MesApi.getLotTimelineAlarms("LOT-001");
  expect(httpClient.get).toHaveBeenCalledWith(
    "/api/machines/alarms",
    {params:{lotNo:"LOT-001"}},
  );
});

test("creates a notice through the administrator endpoint",async()=>{
  httpClient.request.mockResolvedValue({status:201});
  const notice={title:"공지",content:"내용",pinned:true};
  await MesApi.createNotice(notice);
  expect(httpClient.request).toHaveBeenCalledWith({method:"post",url:"/api/notices",data:notice});
});

test("updates a notice through the administrator endpoint",async()=>{
  httpClient.request.mockResolvedValue({status:200});
  const notice={title:"수정 공지",content:"수정 내용",pinned:false};
  await MesApi.updateNotice(3,notice);
  expect(httpClient.request).toHaveBeenCalledWith({method:"put",url:"/api/notices/3",data:notice});
});

test("marks a notification as read through the authenticated endpoint", async () => {
  httpClient.request.mockResolvedValue({status:200});
  await MesApi.markNotificationRead(9);
  expect(httpClient.request).toHaveBeenCalledWith({
    method:"patch",
    url:"/api/notifications/9/read",
    data:undefined,
  });
});

test("returns only running and held lots in pipeline FIFO order",async()=>{
  httpClient.get.mockResolvedValue({data:[
    {lotId:3,workOrderId:2,productionRound:1,currentProcessCode:"OP20",status:"RUNNING"},
    {lotId:1,workOrderId:1,productionRound:1,currentProcessCode:"OP60",status:"RUNNING"},
    {lotId:4,workOrderId:1,productionRound:2,currentProcessCode:"OP20",status:"WAITING"},
    {lotId:2,workOrderId:1,productionRound:2,currentProcessCode:"OP40_OP50",status:"HOLD"},
  ]});

  const response=await MesApi.getPipelineLots();

  expect(httpClient.get).toHaveBeenCalledWith("/api/lots");
  expect(response.data.map(lot=>lot.lotId)).toEqual([1,2,3]);
});
