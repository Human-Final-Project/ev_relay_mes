import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import MaterialPage from "./MaterialPage";

jest.mock("../api/MesApi",()=>({
  __esModule:true,
  default:{
    getMaterialLots:jest.fn(),
    getItems:jest.fn(),
    createMaterialLot:jest.fn(),
  },
}));

beforeEach(()=>{
  MesApi.getMaterialLots.mockResolvedValue({data:[]});
  MesApi.getItems.mockResolvedValue({data:[
    {itemCode:"RM-001",itemName:"구리선",itemType:"RM",useYn:"Y"},
    {itemCode:"RM-OLD",itemName:"비활성 자재",itemType:"RM",useYn:"N"},
    {itemCode:"SA-001",itemName:"반제품",itemType:"SA",useYn:"Y"},
    {itemCode:"FG-001",itemName:"완제품",itemType:"FG",useYn:"Y"},
  ]});
  MesApi.createMaterialLot.mockResolvedValue({data:{}});
});

test("자재 입고 품목을 활성 원자재 선택 목록으로 표시한다",async()=>{
  render(<MaterialPage currentUser={{memberId:1,role:"OPERATOR"}}/>);
  const inboundButton=await screen.findByRole("button",{name:"자재 입고"});
  await waitFor(()=>expect(inboundButton).toBeEnabled());
  fireEvent.click(inboundButton);

  const material=screen.getByLabelText("자재(코드)");
  expect(material).toHaveTextContent("구리선 (RM-001)");
  expect(material).not.toHaveTextContent("RM-OLD");
  expect(material).not.toHaveTextContent("SA-001");
  expect(material).not.toHaveTextContent("FG-001");
});
