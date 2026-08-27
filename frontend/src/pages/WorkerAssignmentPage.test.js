import { fireEvent, render, screen } from "@testing-library/react";
import MesApi from "../api/MesApi";
import WorkerAssignmentPage from "./WorkerAssignmentPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getMachines: jest.fn(),
    getWorkers: jest.fn(),
    getMachineAssignments: jest.fn(),
    createWorker: jest.fn(),
    updateWorker: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
  MesApi.getMachines.mockResolvedValue({ data: [] });
  MesApi.getWorkers.mockResolvedValue({ data: [] });
  MesApi.getMachineAssignments.mockResolvedValue({ data: [] });
});

test("작업자 등록은 부서와 직급을 필수로 입력한다", async () => {
  render(<WorkerAssignmentPage currentUser={{ role: "ADMIN" }}/>);

  fireEvent.click(await screen.findByRole("button", { name: "작업자 등록" }));
  expect(screen.getByLabelText("부서 (필수)")).toBeInTheDocument();
  expect(screen.getByLabelText("직급 (필수)")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
});
