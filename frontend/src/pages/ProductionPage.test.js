import { render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import ProductionPage, { MachineCard } from "./ProductionPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getMachines: jest.fn(),
    getPipelineLots: jest.fn(),
    getMachineAlarms: jest.fn(),
    getProductionLogs: jest.fn(),
    getMachineStatusHistory: jest.fn(),
    getMachineAssignments: jest.fn(),
    clearMachineAlarm: jest.fn(),
  },
}));

beforeEach(() => {
  MesApi.getMachines.mockResolvedValue({ data: [] });
  MesApi.getPipelineLots.mockResolvedValue({ data: [] });
  MesApi.getMachineAlarms.mockResolvedValue({ data: [] });
  MesApi.getProductionLogs.mockResolvedValue({ data: [] });
});

test("가동 중인 공정의 LOT와 진행률을 표시한다", () => {
  render(<MachineCard machine={{
    machineId: "EQ-WIND-01",
    machineName: "권선 설비",
    processCode: "OP20",
    processName: "코일 권선",
    status: "RUNNING",
    currentLotNo: "LOT-001",
    processedQty: 4,
    targetQty: 10,
    progressPercent: 40,
  }} />);

  expect(screen.getByText("LOT-001")).toBeInTheDocument();
  expect(screen.getByText("4 / 10 (40%)")).toBeInTheDocument();
  expect(screen.getByRole("progressbar", { name: "코일 권선 진행률" }))
    .toHaveAttribute("aria-valuenow", "40");
});

test("생산 실적은 완료 상태만 요청하고 상태 선택 항목을 표시하지 않는다", async () => {
  MesApi.getProductionLogs.mockResolvedValue({ data: [{
    productionLogId: 1,
    lotNo: "LOT-001",
    processCode: "OP80",
    processName: "마킹/포장",
    machineId: "EQ-PACK-01",
    inputQty: 10,
    okQty: 9,
    ngQty: 1,
    status: "COMPLETED",
    endedAt: "2026-07-22T10:00:00",
  }] });
  render(<ProductionPage currentUser={{ role: "VIEWER" }} />);

  await waitFor(() => expect(MesApi.getProductionLogs)
    .toHaveBeenCalledWith({ status: "COMPLETED" }));
  expect(screen.queryByLabelText("상태")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "생산 실적" })).toBeInTheDocument();
  expect(screen.queryByRole("columnheader", { name: "상태" })).not.toBeInTheDocument();
});


test("서로 다른 공정의 여러 LOT을 파이프라인 현황에 표시한다", async () => {
  MesApi.getPipelineLots.mockResolvedValue({ data: [
    { lotId: 1, lotNo: "LOT-001", orderNo: "WO-001", workOrderId: 1, currentProcessCode: "OP60", currentProcessName: "실링", lotType: "INITIAL", productionRound: 1, inputQty: 10, status: "RUNNING", startedAt: "2026-07-22T10:00:00" },
    { lotId: 2, lotNo: "LOT-002", orderNo: "WO-002", workOrderId: 2, currentProcessCode: "OP40_OP50", currentProcessName: "조립", lotType: "INITIAL", productionRound: 1, inputQty: 10, status: "RUNNING", startedAt: "2026-07-22T10:01:00" },
  ] });

  render(<ProductionPage currentUser={{ role: "VIEWER" }} />);

  expect(await screen.findByRole("heading", { name: "파이프라인 LOT 현황" })).toBeInTheDocument();
  expect(await screen.findByText("LOT-001")).toBeInTheDocument();
  expect(await screen.findByText("LOT-002")).toBeInTheDocument();
  expect(screen.getByText("OP60")).toBeInTheDocument();
  expect(screen.getByText("OP40_OP50")).toBeInTheDocument();
});
