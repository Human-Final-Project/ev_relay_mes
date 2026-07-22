import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import App from "./App";
import AuthApi from "./api/AuthApi";

jest.mock("./api/AuthApi",()=>({__esModule:true,default:{getCurrentUser:jest.fn(),logout:jest.fn()}}));
jest.mock("./api/MesApi",()=>({__esModule:true,default:{getSystemConnections:jest.fn().mockResolvedValue({data:{l2:{status:"OFFLINE"},l1:{status:"OFFLINE",connected:0,total:6}}})}}));
jest.mock("./pages/DashboardPage",()=>()=> <div>생산 대시보드</div>);

const adminUser={memberId:1,loginId:"admin",memberName:"시스템 관리자",role:"ADMIN",status:"ACTIVE"};
beforeEach(()=>{jest.clearAllMocks();window.history.pushState({},"","/")});

test("shows login when Backend session is missing",async()=>{
  AuthApi.getCurrentUser.mockRejectedValue({response:{status:401}});
  render(<App/>);
  expect(await screen.findByText("시스템 로그인")).toBeInTheDocument();
});

test("restores Backend session and logs out",async()=>{
  AuthApi.getCurrentUser.mockResolvedValue({data:adminUser});AuthApi.logout.mockResolvedValue({status:204});
  render(<App/>);
  expect(await screen.findByText("생산 대시보드")).toBeInTheDocument();
  fireEvent.click(screen.getByTitle("로그아웃"));
  await waitFor(()=>expect(AuthApi.logout).toHaveBeenCalledTimes(1));
  expect(await screen.findByText("시스템 로그인")).toBeInTheDocument();
});
