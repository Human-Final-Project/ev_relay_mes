import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";

const roleOptions = [
  { value: "ADMIN", label: "ADMIN (관리자)" },
  { value: "OPERATOR", label: "OPERATOR (운영자)" },
];

const statusOptions = [
  { value: "ACTIVE", label: "사용 가능" },
  { value: "LOCKED", label: "잠김" },
  { value: "RETIRED", label: "퇴사/비활성" },
];

const emptyMember = {
  loginId: "",
  password: "",
  memberName: "",
  role: "OPERATOR",
  status: "ACTIVE",
};

export default function AdminEmployeePage() {
  const result = useApiData(MesApi.getMembers, []);
  const [form, setForm] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const openEdit = (member) => setForm({
    ...member,
    role: member.role === "ADMIN" ? "ADMIN" : "OPERATOR",
  });

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      if (form.memberId) {
        await MesApi.updateMember(form.memberId, {
          role: form.role,
          status: form.status,
        });
      } else {
        await MesApi.createMember({
          loginId: form.loginId,
          password: form.password,
          memberName: form.memberName,
          role: form.role,
          status: form.status,
        });
      }
      setForm(null);
      await result.reload();
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  };

  const invalid = !form
    || (!form.memberId && (!form.loginId.trim() || !form.password || !form.memberName.trim()))
    || !form.role
    || !form.status;

  return <div className="mes-page">
    <PageHeader
      title="사용자 관리"
      description="ADMIN 전용 계정·권한 관리 화면입니다. 사용자 역할은 관리자와 운영자로 관리합니다."
      actions={<button className="btn" onClick={() => setForm({ ...emptyMember })}>사용자 등록</button>}
    />

    {error && <ErrorState error={error}/>} 
    {result.loading ? <LoadingState/> : result.error ? <ErrorState error={result.error} onRetry={result.reload}/> : !(result.data || []).length ? <EmptyState/> :
      <div className="mes-table-wrap"><table className="mes-table">
        <thead><tr><th>로그인 ID/이름</th><th>역할</th><th>상태</th><th>생성자</th><th>수정 시각</th><th>작업</th></tr></thead>
        <tbody>{result.data.map((member) => <tr key={member.memberId}>
          <td><span className="mono">{member.loginId}</span><br/><strong>{member.memberName}</strong></td>
          <td><StatusBadge value={member.role}/></td>
          <td><StatusBadge value={member.status}/></td>
          <td>{member.createdByName || "-"}</td>
          <td>{formatDate(member.updatedAt)}</td>
          <td><button className="btn small secondary" onClick={() => openEdit(member)}>수정</button></td>
        </tr>)}</tbody>
      </table></div>
    }

    {form && <Modal
      title={form.memberId ? "사용자 수정" : "사용자 등록"}
      onClose={() => setForm(null)}
      footer={<>
        <button className="btn secondary" onClick={() => setForm(null)}>취소</button>
        <button className="btn" disabled={saving || invalid} onClick={save}>저장</button>
      </>}
    >
      <div className="mes-form-grid">
        {!form.memberId && <>
          <Field label="로그인 ID"><input value={form.loginId} onChange={(e) => setForm({ ...form, loginId: e.target.value })}/></Field>
          <Field label="초기 비밀번호"><input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })}/></Field>
          <Field label="이름"><input value={form.memberName} onChange={(e) => setForm({ ...form, memberName: e.target.value })}/></Field>
        </>}
        <Field label="역할">
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
            {roleOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </Field>
        <Field label="상태">
          <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
            {statusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </Field>
      </div>
      {error && <ErrorState error={error}/>} 
    </Modal>}
  </div>;
}
