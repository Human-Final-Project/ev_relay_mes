import React, { useState } from "react";
import AuthApi from "../api/AuthApi";
import { ErrorState, Field, PageHeader } from "../components/MesComponents";

export default function AccountPage({currentUser,onLoggedOut}){
  const [form,setForm]=useState({currentPassword:"",newPassword:"",newPasswordConfirm:""}); const [error,setError]=useState(null); const [saving,setSaving]=useState(false);
  const submit=async(e)=>{e.preventDefault();setSaving(true);setError(null);try{await AuthApi.changePassword(form);alert("비밀번호가 변경되었습니다. 다시 로그인해 주세요.");await onLoggedOut?.()}catch(reason){setError(reason)}finally{setSaving(false)}};
  return <div className="mes-page"><PageHeader title="내 계정" description="현재 로그인 사용자와 비밀번호를 관리합니다."/><div className="mes-grid two"><section className="mes-card"><h2>사용자 정보</h2><dl className="detail-list"><dt>사용자 ID</dt><dd>{currentUser?.loginId}</dd><dt>이름</dt><dd>{currentUser?.memberName}</dd><dt>역할</dt><dd>{currentUser?.role}</dd><dt>상태</dt><dd>{currentUser?.status}</dd></dl></section><form className="mes-card" onSubmit={submit}><h2>비밀번호 변경</h2><div className="mes-grid"><Field label="현재 비밀번호"><input type="password" value={form.currentPassword} onChange={e=>setForm({...form,currentPassword:e.target.value})}/></Field><Field label="새 비밀번호"><input type="password" value={form.newPassword} onChange={e=>setForm({...form,newPassword:e.target.value})}/></Field><Field label="새 비밀번호 확인"><input type="password" value={form.newPasswordConfirm} onChange={e=>setForm({...form,newPasswordConfirm:e.target.value})}/></Field>{error&&<ErrorState error={error}/>}<button className="btn" disabled={saving||!form.currentPassword||form.newPassword!==form.newPasswordConfirm}>변경</button></div></form></div></div>
}
