import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import WorkOrderPage from "../pages/WorkOrderPage";

jest.mock("../api/MesApi",()=>({
  __esModule:true,
  default:{
    getWorkOrders:jest.fn(),
    getItems:jest.fn(),
    createWorkOrder:jest.fn(),
    releaseWorkOrder:jest.fn(),
  },
}));

beforeEach(()=>{
  MesApi.getWorkOrders.mockResolvedValue({data:[]});
  MesApi.getItems.mockResolvedValue({data:[
    {itemCode:"RM-001",itemName:"원자재",itemType:"RM",useYn:"Y"},
    {itemCode:"FG-001",itemName:"완제품",itemType:"FG",useYn:"Y"},
    {itemCode:"FG-OLD",itemName:"비활성 제품",itemType:"FG",useYn:"N"},
  ]});
  MesApi.createWorkOrder.mockResolvedValue({data:{}});
  MesApi.releaseWorkOrder.mockResolvedValue({data:{}});
});

test("작업지시 제품을 활성 완제품 선택 목록으로 표시한다",async()=>{
  render(<WorkOrderPage currentUser={{role:"MANAGER"}}/>);
  const createButton=await screen.findByRole("button",{name:"작업지시 생성"});
  await waitFor(()=>expect(createButton).toBeEnabled());
  fireEvent.click(createButton);

  const product=screen.getByLabelText("제품(코드)");
  expect(product).toHaveTextContent("완제품 (FG-001)");
  expect(product).not.toHaveTextContent("RM-001");
  expect(product).not.toHaveTextContent("FG-OLD");
});

test("submits the weekly target and planned schedule together", async () => {
  render(<WorkOrderPage currentUser={{role:"MANAGER"}}/>);
  const createButton = await screen.findByRole("button", {name:"작업지시 생성"});
  await waitFor(() => expect(createButton).toBeEnabled());
  fireEvent.click(createButton);

  fireEvent.change(screen.getByLabelText("제품(코드)"), {target:{value:"FG-001"}});
  fireEvent.change(screen.getByLabelText("목표 수량"), {target:{value:"120"}});
  fireEvent.change(screen.getByLabelText("계획 시작"), {target:{value:"2026-07-27T08:00"}});
  fireEvent.change(screen.getByLabelText("계획 종료"), {target:{value:"2026-07-31T18:00"}});
  fireEvent.click(screen.getByRole("button", {name:"저장"}));

  await waitFor(() => expect(MesApi.createWorkOrder).toHaveBeenCalledWith({
    itemCode: "FG-001",
    targetQty: 120,
    plannedStartAt: "2026-07-27T08:00",
    plannedEndAt: "2026-07-31T18:00",
  }));
});


test("확정과 최초 LOT 자동 시작을 한 번에 요청한다",async()=>{
  MesApi.getWorkOrders.mockResolvedValue({data:[{
    workOrderId:1,
    orderNo:"WO-001",
    itemCode:"FG-001",
    itemName:"완제품",
    targetQty:10,
    completedOkQty:0,
    remainingQty:10,
    status:"CREATED",
    automationStatus:"DRAFT",
  }]});
  render(<WorkOrderPage currentUser={{role:"MANAGER"}}/>);
  const release=await screen.findByRole("button",{name:"확정 및 생산 시작"});
  fireEvent.click(release);
  await waitFor(()=>expect(MesApi.releaseWorkOrder).toHaveBeenCalledWith(1));
});

test("책임자가 없는 설비가 있으면 배정 화면 이동을 안내한다", async () => {
  MesApi.getWorkOrders.mockResolvedValue({data:[{
    workOrderId:1,
    orderNo:"WO-001",
    itemCode:"FG-001",
    itemName:"완제품",
    targetQty:10,
    completedOkQty:0,
    remainingQty:10,
    status:"CREATED",
    automationStatus:"DRAFT",
  }]});
  MesApi.releaseWorkOrder.mockRejectedValue({
    response: {data: {code: "WK008", message: "책임자 미배정 설비: EQ-WIND-01"}},
  });

  render(<WorkOrderPage currentUser={{role:"MANAGER"}}/>);
  fireEvent.click(await screen.findByRole("button",{name:"확정 및 생산 시작"}));

  expect(await screen.findByText("설비 책임자 배정 필요")).toBeInTheDocument();
  expect(screen.getByRole("link", {name: "작업자 배정으로 이동"}))
    .toHaveAttribute("href", "/workers");
});
