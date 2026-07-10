import React from "react";
// [수정 7월 10일 21:38] react-router-dom의 Link, useLocation은 더 이상 사용하지 않아 제거, Outlet만 유지
import { Outlet } from "react-router-dom";
import GlobalStyle from "../style/GlobalStyle";

// [삭제 7월 10일 21:38] 스크린샷 디자인과 다른 남색 "MES System" 상단 네비게이션 바(Navbar, Logo, Menu, StyledLink) 전부 삭제
// [삭제 7월 10일 21:38] 콘텐츠를 1200px로 제한하고 여백을 주던 Content 래퍼도 삭제 (DashboardPage 자체 전체화면 레이아웃과 충돌하여 삭제)

const MesLayout = () => {
  return (
    <>
      <GlobalStyle />
      <Outlet />
    </>
  );
};

export default MesLayout;
