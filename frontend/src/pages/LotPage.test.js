import { render, screen } from "@testing-library/react";
import MesApi from "../api/MesApi";
import LotPage from "./LotPage";

jest.mock("../api/MesApi",()=>({
  __esModule:true,
  default:{
    getLots:jest.fn(),
    getLotByNo:jest.fn(),
    getLotCommands:jest.fn(),
    getLotResponsibles:jest.fn(),
    getLotMaterialUsages:jest.fn(),
    getProductionLogs:jest.fn(),
    getInspections:jest.fn(),
    getDefects:jest.fn(),
  },
}));

const waitingLot={
  lotId:1,
  lotNo:"LOT-001",
  orderNo:"WO-001",
  itemName:"완제품",
  lotType:"INITIAL",
  productionRound:1,
  currentProcessName:"권선",
  currentProcessCode:"OP20",
  inputQty:10,
  okQty:0,
  ngQty:0,
  status:"WAITING",
  startRequestedAt:"2026-07-23T10:00:00",
};

beforeEach(()=>{
  MesApi.getLots.mockResolvedValue({data:[waitingLot]});
  MesApi.getLotByNo.mockResolvedValue({data:waitingLot});
  MesApi.getLotCommands.mockResolvedValue({data:[]});
  MesApi.getLotResponsibles.mockResolvedValue({data:[]});
  MesApi.getLotMaterialUsages.mockResolvedValue({data:[]});
  MesApi.getProductionLogs.mockResolvedValue({data:[]});
  MesApi.getInspections.mockResolvedValue({data:[]});
  MesApi.getDefects.mockResolvedValue({data:[]});
});

test("자재가 부족한 자동 LOT을 입고 대기로 표시한다",async()=>{
  render(<LotPage currentUser={{role:"OPERATOR"}}/>);
  expect(await screen.findByText("자재 입고 대기")).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"파이프라인 투입"})).not.toBeInTheDocument();
});
