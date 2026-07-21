import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import WorkOrderPage from "./WorkOrderPage";

jest.mock("../api/MesApi",()=>({
  __esModule:true,
  default:{
    getWorkOrders:jest.fn(),
    getItems:jest.fn(),
    createWorkOrder:jest.fn(),
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
