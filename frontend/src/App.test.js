import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import App from "./App";
import AuthApi from "./api/AuthApi";

jest.mock("./api/AuthApi",()=>({__esModule:true,default:{getCurrentUser:jest.fn(),logout:jest.fn()}}));
jest.mock("./pages/DashboardPage",()=>()=> <div>생산 대시보드</div>);

const adminUser={memberId:1,loginId:"admin",memberName:"시스템 관리자",role:"ADMIN",status:"ACTIVE"};
const operatorUser={memberId:2,loginId:"operator",memberName:"현장 운영자",role:"OPERATOR",status:"ACTIVE"};
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


test("OPERATOR에게 작업자 배정과 사용자 관리 메뉴를 숨긴다",async()=>{
  AuthApi.getCurrentUser.mockResolvedValue({data:operatorUser});
  render(<App/>);
  expect(await screen.findByText("생산 대시보드")).toBeInTheDocument();
  expect(screen.queryByText("작업자 배정")).not.toBeInTheDocument();
  expect(screen.queryByText("사용자 관리")).not.toBeInTheDocument();
});
