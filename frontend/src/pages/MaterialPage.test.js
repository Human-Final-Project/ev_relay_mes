import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import MaterialPage from "./MaterialPage";

jest.mock("../api/MesApi",()=>({
  __esModule:true,
  default:{
    getMaterialLots:jest.fn(),
    getItems:jest.fn(),
    createMaterialLot:jest.fn(),
    createItem:jest.fn(),
    updateItem:jest.fn(),
    setItemActive:jest.fn(),
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

test("OPERATOR는 품목 등록만 가능하고 원자재 입고와 품목 수정은 할 수 없다", async()=>{
  render(<MaterialPage currentUser={{memberId:1,role:"OPERATOR"}}/>);

  expect(await screen.findByRole("button",{name:"새로고침"})).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"원자재 입고"})).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole("button",{name:"품목 관리"}));
  expect(await screen.findByRole("button",{name:"품목 등록"})).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"수정"})).not.toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"비활성"})).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole("button",{name:"품목 등록"}));
  expect(screen.getByRole("heading",{name:"품목 등록"})).toBeInTheDocument();
});
