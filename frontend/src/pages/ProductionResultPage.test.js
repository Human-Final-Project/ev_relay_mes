import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import ProductionResultPage from "./ProductionResultPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getWorkOrders: jest.fn(),
    getMachines: jest.fn(),
    getLots: jest.fn(),
    getProductionLogs: jest.fn(),
  },
}));

beforeEach(() => {
  MesApi.getWorkOrders.mockResolvedValue({ data: [
    { workOrderId: 1, orderNo: "WO-001", itemName: "EV Relay" },
    { workOrderId: 2, orderNo: "WO-002", itemName: "EV Relay" },
  ] });
  MesApi.getMachines.mockResolvedValue({ data: [] });
  MesApi.getProductionLogs.mockResolvedValue({ data: [] });
  MesApi.getLots.mockImplementation(({ workOrderId }) => Promise.resolve({
    data: String(workOrderId) === "1"
      ? [
          { lotId: 11, lotNo: "LOT-001", lotType: "INITIAL", productionRound: 1 },
          { lotId: 12, lotNo: "LOT-001-S", lotType: "SUPPLEMENT", productionRound: 2 },
        ]
      : [],
  }));
});

test("작업지시를 선택하면 해당 작업지시의 LOT만 표시한다", async () => {
  render(<ProductionResultPage />);

  const workOrderSelect = await screen.findByLabelText("작업지시");
  const lotSelect = screen.getByLabelText("LOT");
  expect(lotSelect).toBeDisabled();

  fireEvent.change(workOrderSelect, { target: { value: "1" } });

  await waitFor(() => expect(MesApi.getLots).toHaveBeenCalledWith({ workOrderId: "1" }));
  expect(await screen.findByRole("option", { name: "LOT-001 · 최초" })).toBeInTheDocument();
  expect(screen.getByRole("option", { name: "LOT-001-S · 보충 1차" })).toBeInTheDocument();
});

test("공정별 생산량은 전체 기준으로 고정하고 조건 필터는 설비만 제공한다", async () => {
  render(<ProductionResultPage />);

  expect(await screen.findByText("검색 조건과 무관한 전체 완료 실적 기준")).toBeInTheDocument();
  expect(screen.getByLabelText("설비")).toBeInTheDocument();
  expect(screen.queryByLabelText("조건")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("공정 선택")).not.toBeInTheDocument();
});
