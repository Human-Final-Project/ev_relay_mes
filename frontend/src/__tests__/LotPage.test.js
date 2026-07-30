import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import JsBarcode from "jsbarcode";
import QRCode from "qrcode";
import MesApi from "../api/MesApi";
import LotPage, { buildTimeline } from "../pages/LotPage";

jest.mock("qrcode",()=>({
  __esModule:true,
  default:{toDataURL:jest.fn().mockResolvedValue("data:image/png;base64,cXItY29kZQ==")},
}));
jest.mock("jsbarcode",()=>({
  __esModule:true,
  default:jest.fn((element,value)=>element.setAttribute("data-value",value)),
}));
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
    getLotTimelineAlarms:jest.fn(),
  },
}));

const waitingLot={
  lotId:1,
  lotNo:"LOT-001",
  orderNo:"WO-001",
  itemName:"완제품",
  itemCode:"FG-001",
  lotType:"INITIAL",
  productionRound:1,
  currentProcessName:"권선",
  currentProcessCode:"OP20",
  inputQty:10,
  okQty:0,
  ngQty:0,
  status:"WAITING",
  startRequestedAt:"2026-07-23T10:00:00",
  createdAt:"2026-07-23T09:59:00",
};

beforeEach(()=>{
  QRCode.toDataURL.mockResolvedValue("data:image/png;base64,cXItY29kZQ==");
  JsBarcode.mockImplementation((element,value)=>element.setAttribute("data-value",value));
  MesApi.getLots.mockResolvedValue({data:[waitingLot]});
  MesApi.getLotByNo.mockResolvedValue({data:waitingLot});
  MesApi.getLotCommands.mockResolvedValue({data:[]});
  MesApi.getLotResponsibles.mockResolvedValue({data:[]});
  MesApi.getLotMaterialUsages.mockResolvedValue({data:[]});
  MesApi.getProductionLogs.mockResolvedValue({data:[]});
  MesApi.getInspections.mockResolvedValue({data:[]});
  MesApi.getDefects.mockResolvedValue({data:[]});
  MesApi.getLotTimelineAlarms.mockResolvedValue({data:[]});
});

const renderPage=(path="/lots")=>render(
  <MemoryRouter initialEntries={[path]}>
    <LotPage currentUser={{role:"OPERATOR"}}/>
  </MemoryRouter>
);

test("자재가 부족한 자동 LOT을 입고 대기로 표시한다",async()=>{
  renderPage();
  expect(await screen.findByText("자재 입고 대기")).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"파이프라인 투입"})).not.toBeInTheDocument();
});

test("LOT 선택 시 통합 타임라인을 기본 탭으로 표시한다",async()=>{
  renderPage();
  fireEvent.click(await screen.findByText("LOT-001"));

  expect(await screen.findByRole("button",{name:"통합 타임라인"})).toHaveClass("active");
  expect(await screen.findByText("LOT 생성")).toBeInTheDocument();
  expect(MesApi.getLotTimelineAlarms).toHaveBeenCalledWith("LOT-001");
});

test("QR 링크의 lotNo로 해당 LOT 타임라인을 바로 조회한다",async()=>{
  MesApi.getLots.mockResolvedValueOnce({data:[]});
  renderPage("/lots?lotNo=LOT-001");

  expect(await screen.findByRole("button",{name:"통합 타임라인"})).toHaveClass("active");
  expect(MesApi.getLotByNo).toHaveBeenCalledWith("LOT-001");
});

test("LOT 상세에서 QR과 바코드 라벨을 연다",async()=>{
  renderPage();
  fireEvent.click(await screen.findByText("LOT-001"));
  fireEvent.click(await screen.findByRole("button",{name:"QR · 바코드 라벨"}));

  expect(await screen.findByText("LOT QR · 바코드 라벨")).toBeInTheDocument();
  expect(await screen.findByAltText("LOT-001 추적 QR 코드")).toBeInTheDocument();
  expect(screen.getByDisplayValue("http://localhost/lots?lotNo=LOT-001")).toBeInTheDocument();
});

test("검사 측정값은 공정별로 집계하고 전체 이력을 시간순으로 정렬한다",()=>{
  const timeline=buildTimeline({
    lot:{lotNo:"LOT-001",lotType:"INITIAL",inputQty:2,createdAt:"2026-07-23T10:00:00",completedAt:"2026-07-23T11:00:00",status:"COMPLETED",okQty:1,ngQty:1},
    materials:[{itemName:"코일",materialLotNo:"RM-1",usedQty:2,usedAt:"2026-07-23T10:05:00"}],
    inspections:[
      {processCode:"OP70",processName:"최종 검사",machineId:"EQ-1",result:"OK",inspectedAt:"2026-07-23T10:40:00"},
      {processCode:"OP70",processName:"최종 검사",machineId:"EQ-1",result:"NG",inspectedAt:"2026-07-23T10:41:00"},
    ],
    commands:[],responsibles:[],logs:[],defects:[],alarms:[],
  });

  expect(timeline.map(event=>event.title)).toEqual([
    "LOT 생성","원자재 투입","최종 검사 검사 집계","완제품 LOT 완료",
  ]);
  expect(timeline[2].description).toBe("측정 2건 · OK 1 · NG 1");
});
