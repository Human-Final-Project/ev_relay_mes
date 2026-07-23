import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import MasterDataPage from "./MasterDataPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getItems: jest.fn(),
    getBoms: jest.fn(),
    getProcesses: jest.fn(),
    getDefectCodes: jest.fn(),
    getAlarmCodes: jest.fn(),
    createBom: jest.fn(),
  },
}));

beforeEach(() => {
  MesApi.getItems.mockResolvedValue({data:[
    {itemCode:"RM-001",itemName:"원자재",itemType:"RM",useYn:"Y"},
    {itemCode:"SA-001",itemName:"반제품",itemType:"SA",useYn:"Y"},
    {itemCode:"FG-001",itemName:"완제품",itemType:"FG",useYn:"Y"},
  ]});
  MesApi.getBoms.mockResolvedValue({data:[]});
  MesApi.getProcesses.mockResolvedValue({data:[{processCode:"OP20",processName:"권선",processOrder:1}]});
  MesApi.getDefectCodes.mockResolvedValue({data:[]});
  MesApi.getAlarmCodes.mockResolvedValue({data:[]});
  MesApi.createBom.mockResolvedValue({data:{}});
});

test("BOM 구성품을 품목 선택 목록으로 등록한다", async () => {
  render(<MasterDataPage currentUser={{role:"ADMIN"}}/>);

  fireEvent.click(screen.getByRole("button",{name:"BOM"}));
  const addButton=await screen.findByRole("button",{name:"구성품 추가"});
  await waitFor(()=>expect(addButton).toBeEnabled());
  fireEvent.click(addButton);

  const parent=screen.getByLabelText("상위 품목");
  const child=screen.getByLabelText("하위 품목");
  expect(parent).not.toHaveTextContent("RM-001");
  expect(parent).toHaveTextContent("SA-001 · 반제품");

  fireEvent.change(parent,{target:{value:"SA-001"}});
  expect(child).not.toHaveTextContent("SA-001 · 반제품");
  fireEvent.change(child,{target:{value:"RM-001"}});
  fireEvent.change(screen.getByLabelText("적용 공정"),{target:{value:"OP20"}});
  fireEvent.click(screen.getByRole("button",{name:"저장"}));

  await waitFor(()=>expect(MesApi.createBom).toHaveBeenCalledWith({
    parentItemCode:"SA-001",
    childItemCode:"RM-001",
    quantity:1,
    processCode:"OP20",
  }));
});
