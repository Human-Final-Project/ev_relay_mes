import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import LotPage from "./LotPage";

jest.mock("../api/MesApi",()=>({
  __esModule:true,
  default:{
    getLots:jest.fn(),
    updateLotStatus:jest.fn(),
    getLotByNo:jest.fn(),
    getLotCommands:jest.fn(),
    getLotResponsibles:jest.fn(),
    getProductionLogs:jest.fn(),
    getInspections:jest.fn(),
    getDefects:jest.fn(),
  },
}));

const waitingLot={lotId:1,lotNo:"LOT-001",orderNo:"WO-001",itemName:"완제품",lotType:"INITIAL",productionRound:1,currentProcessName:"권선",currentProcessCode:"OP20",inputQty:10,okQty:0,ngQty:0,status:"WAITING"};

beforeEach(()=>{
  MesApi.getLots.mockResolvedValue({data:[waitingLot]});
  MesApi.updateLotStatus.mockResolvedValue({data:{...waitingLot,status:"RUNNING"}});
  MesApi.getLotByNo.mockResolvedValue({data:waitingLot});
  MesApi.getLotCommands.mockResolvedValue({data:[]});
  MesApi.getLotResponsibles.mockResolvedValue({data:[]});
  MesApi.getProductionLogs.mockResolvedValue({data:[]});
  MesApi.getInspections.mockResolvedValue({data:[]});
  MesApi.getDefects.mockResolvedValue({data:[]});
});

test("대기 LOT에서 생산 시작 요청을 전송한다",async()=>{
  render(<LotPage currentUser={{role:"OPERATOR"}}/>);
  const start=await screen.findByRole("button",{name:"생산 시작"});
  fireEvent.click(start);
  await waitFor(()=>expect(MesApi.updateLotStatus).toHaveBeenCalledWith(1,"RUNNING"));
});
