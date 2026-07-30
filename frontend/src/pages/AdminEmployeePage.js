import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, Modal, PageHeader, SortableTh, StatusBadge, formatDate, useSortableRows } from "../components/MesComponents";

const MEMBER_SORTERS = {
  identity: (row) => `${row.loginId} ${row.memberName}`,
  organization: (row) => `${row.department || ""} ${row.position || ""}`,
  role: (row) => row.role,
  status: (row) => row.status,
  creator: (row) => row.createdByName,
  updatedAt: (row) => row.updatedAt,
};

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
  department: "",
  position: "",
};

export default function AdminEmployeePage() {
  const result = useApiData(MesApi.getMembers, []);
  const members = useSortableRows(result.data || [], MEMBER_SORTERS);
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
          department: form.department,
          position: form.position,
        });
      } else {
        await MesApi.createMember({
          loginId: form.loginId,
          password: form.password,
          memberName: form.memberName,
          role: form.role,
          status: form.status,
          department: form.department,
          position: form.position,
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
    || (!form.memberId && form.role === "OPERATOR" && !/^EVR\d{8}$/.test(form.loginId))
    || !form.role
    || !form.status;

  return <div className="mes-page">
    <PageHeader
      title="사용자 관리"
      description="책임자로 배정할 사람은 OPERATOR 사용자로 등록합니다. 등록 즉시 책임자 후보 작업자와 연동됩니다."
      actions={<button className="btn" onClick={() => setForm({ ...emptyMember })}>사용자 등록</button>}
    />

    {error && <ErrorState error={error}/>} 
    {result.loading ? <LoadingState/> : result.error ? <ErrorState error={result.error} onRetry={result.reload}/> : !(result.data || []).length ? <EmptyState/> :
      <div className="mes-table-wrap"><table className="mes-table">
        <thead><tr>
          <SortableTh label="로그인 ID/이름" sortKey="identity" {...members}/>
          <SortableTh label="부서/직급" sortKey="organization" {...members}/>
          <SortableTh label="역할" sortKey="role" {...members}/>
          <SortableTh label="상태" sortKey="status" {...members}/>
          <SortableTh label="생성자" sortKey="creator" {...members}/>
          <SortableTh label="수정 시각" sortKey="updatedAt" {...members}/>
          <th>작업</th>
        </tr></thead>
        <tbody>{members.rows.map((member) => <tr key={member.memberId}>
          <td><span className="mono">{member.loginId}</span><br/><strong>{member.memberName}</strong></td>
          <td>{member.department || "-"}<br/><small>{member.position || "-"}</small></td>
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
          <Field label={form.role === "OPERATOR" ? "사번 / 로그인 ID" : "로그인 ID"}>
            <input
              value={form.loginId}
              maxLength={form.role === "OPERATOR" ? 11 : 50}
              placeholder={form.role === "OPERATOR" ? "EVR00000001" : "관리자 로그인 ID"}
              onChange={(e) => setForm({
                ...form,
                loginId: form.role === "OPERATOR"
                  ? e.target.value.toUpperCase()
                  : e.target.value,
              })}
            />
          </Field>
          <Field label="초기 비밀번호"><input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })}/></Field>
          <Field label="이름"><input value={form.memberName} onChange={(e) => setForm({ ...form, memberName: e.target.value })}/></Field>
        </>}
        <Field label="부서"><input value={form.department || ""} onChange={(e) => setForm({ ...form, department: e.target.value })}/></Field>
        <Field label="직급"><input value={form.position || ""} onChange={(e) => setForm({ ...form, position: e.target.value })}/></Field>
        <Field label="역할">
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
            {roleOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </Field>
        {!form.memberId && form.role === "OPERATOR" &&
          <p className="form-hint">사번은 EVR + 숫자 8자리로 입력합니다.</p>}
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
