import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import MesApi from "../api/MesApi";
import DashboardPage from "../pages/DashboardPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getMachines: jest.fn(),
    getWorkOrders: jest.fn(),
    getWeeklyProductionTarget: jest.fn(),
    saveWeeklyProductionTarget: jest.fn(),
    getProductionLogs: jest.fn(),
    getDefects: jest.fn(),
    getNotices: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
  const now = new Date();
  const scheduledStart = new Date(now);
  scheduledStart.setDate(now.getDate() - 1);
  const scheduledEnd = new Date(now);
  scheduledEnd.setDate(now.getDate() + 1);

  MesApi.getMachines.mockResolvedValue({data:[]});
  MesApi.getProductionLogs.mockResolvedValue({data:[{
    productionLogId: 1,
    processCode: "OP80",
    okQty: 60,
    ngQty: 2,
    endedAt: now.toISOString(),
  }]});
  MesApi.getDefects.mockResolvedValue({data:[]});
  MesApi.getNotices.mockResolvedValue({data:[]});
  MesApi.getWeeklyProductionTarget.mockResolvedValue({data:{
    weekStart: "2026-07-27",
    targetQty: 150,
    targetDefectRate: 5,
    configured: true,
  }});
  MesApi.saveWeeklyProductionTarget.mockResolvedValue({data:{}});
  MesApi.getWorkOrders.mockResolvedValue({data:[
    {
      workOrderId: 1,
      status: "RUNNING",
      targetQty: 100,
      completedOkQty: 60,
      plannedStartAt: scheduledStart.toISOString(),
      plannedEndAt: scheduledEnd.toISOString(),
    },
    {
      workOrderId: 2,
      status: "CREATED",
      targetQty: 50,
      completedOkQty: 0,
      plannedStartAt: null,
      plannedEndAt: null,
    },
  ]});
});

test("uses the separately configured weekly target and final-process output", async () => {
  render(<MemoryRouter><DashboardPage currentUser={{role:"MANAGER"}}/></MemoryRouter>);

  const targetCard = (await screen.findByText("금주 목표")).closest("article");
  expect(within(targetCard).getByText("150")).toBeInTheDocument();
  expect(targetCard).toHaveTextContent("별도 주간 생산 목표");

  const attainmentCard = screen.getByText("금주 달성률").closest("article");
  expect(within(attainmentCard).getByText("40.0%")).toBeInTheDocument();

  const defectCard = screen.getByText("금주 불량률").closest("article");
  expect(within(defectCard).getByText("3.2%")).toBeInTheDocument();

  const riskCard = screen.getByText("일정 위험").closest("article");
  expect(riskCard).toHaveTextContent("미지정 1");
});

test("manager can save a weekly target without changing a work order", async () => {
  render(<MemoryRouter><DashboardPage currentUser={{role:"MANAGER"}}/></MemoryRouter>);

  fireEvent.click(await screen.findByRole("button", {name:"주간 목표 설정"}));
  fireEvent.change(screen.getByLabelText("주간 목표 수량"), {target:{value:"1000"}});
  fireEvent.change(screen.getByLabelText("목표 불량률"), {target:{value:"3"}});
  fireEvent.click(screen.getByRole("button", {name:"저장"}));

  await waitFor(() => expect(MesApi.saveWeeklyProductionTarget).toHaveBeenCalledWith(
    expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
    {targetQty:1000, targetDefectRate:3}
  ));
});
