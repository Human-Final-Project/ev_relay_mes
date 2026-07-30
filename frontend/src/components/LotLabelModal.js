import React, { useEffect, useRef, useState } from "react";
import JsBarcode from "jsbarcode";
import QRCode from "qrcode";
import { Modal, formatDate } from "./MesComponents";

const LABEL_WIDTH = 1200;
const LABEL_HEIGHT = 800;

function loadImage(source) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = reject;
    image.src = source;
  });
}

function escapeHtml(value) {
  return String(value ?? "-")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function fileSafe(value) {
  return String(value || "LOT").replace(/[^a-zA-Z0-9._-]/g, "_");
}

export function buildLotTraceUrl(lotNo) {
  const url = new URL("/lots", window.location.origin);
  url.searchParams.set("lotNo", lotNo);
  return url.toString();
}

export default function LotLabelModal({ lot, onClose }) {
  const barcodeRef = useRef(null);
  const [qrDataUrl, setQrDataUrl] = useState("");
  const [message, setMessage] = useState("");
  const traceUrl = buildLotTraceUrl(lot.lotNo);

  useEffect(() => {
    let active = true;
    QRCode.toDataURL(traceUrl, {
      errorCorrectionLevel: "M",
      margin: 1,
      width: 360,
      color: { dark: "#0f172a", light: "#ffffff" },
    })
      .then((dataUrl) => active && setQrDataUrl(dataUrl))
      .catch(() => active && setMessage("QR 코드를 생성하지 못했습니다."));

    if (barcodeRef.current) {
      try {
        JsBarcode(barcodeRef.current, lot.lotNo, {
          format: "CODE128",
          displayValue: true,
          font: "monospace",
          fontSize: 20,
          height: 76,
          margin: 8,
          lineColor: "#0f172a",
        });
      } catch {
        setMessage("바코드를 생성하지 못했습니다.");
      }
    }
    return () => {
      active = false;
    };
  }, [lot.lotNo, traceUrl]);

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(traceUrl);
      setMessage("LOT 추적 링크를 복사했습니다.");
    } catch {
      setMessage("링크를 복사하지 못했습니다. 주소를 직접 복사해 주세요.");
    }
  };

  const downloadLabel = async () => {
    if (!qrDataUrl || !barcodeRef.current) return;
    try {
      const canvas = document.createElement("canvas");
      canvas.width = LABEL_WIDTH;
      canvas.height = LABEL_HEIGHT;
      const context = canvas.getContext("2d");
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, LABEL_WIDTH, LABEL_HEIGHT);
      context.fillStyle = "#0f172a";
      context.fillRect(0, 0, LABEL_WIDTH, 112);
      context.fillStyle = "#ffffff";
      context.font = "700 42px sans-serif";
      context.fillText("EV RELAY · LOT TRACE LABEL", 54, 70);

      const qrImage = await loadImage(qrDataUrl);
      context.drawImage(qrImage, 54, 155, 360, 360);

      context.fillStyle = "#0f172a";
      context.font = "700 40px monospace";
      context.fillText(lot.lotNo, 480, 190);
      context.font = "600 25px sans-serif";
      [
        `품목: ${lot.itemName || "-"} (${lot.itemCode || "-"})`,
        `작업지시: ${lot.orderNo || "-"}`,
        `투입 수량: ${lot.inputQty ?? "-"}`,
        `생성 일시: ${formatDate(lot.createdAt)}`,
      ].forEach((line, index) => context.fillText(line, 480, 255 + index * 52));

      const barcodeSvg = new XMLSerializer().serializeToString(barcodeRef.current);
      const barcodeImage = await loadImage(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(barcodeSvg)}`);
      context.drawImage(barcodeImage, 472, 465, 660, 190);
      context.fillStyle = "#64748b";
      context.font = "18px monospace";
      context.fillText(traceUrl, 54, 735);

      canvas.toBlob((blob) => {
        if (!blob) {
          setMessage("라벨 이미지를 만들지 못했습니다.");
          return;
        }
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = `${fileSafe(lot.lotNo)}-label.png`;
        link.click();
        URL.revokeObjectURL(link.href);
        setMessage("라벨 이미지를 저장했습니다.");
      }, "image/png");
    } catch {
      setMessage("라벨 이미지를 만들지 못했습니다.");
    }
  };

  const printLabel = () => {
    if (!qrDataUrl || !barcodeRef.current) return;
    const printWindow = window.open("", "_blank", "width=900,height=720");
    if (!printWindow) {
      setMessage("인쇄 창이 차단되었습니다. 브라우저 팝업을 허용해 주세요.");
      return;
    }
    printWindow.document.write(`<!doctype html>
      <html lang="ko"><head><title>${escapeHtml(lot.lotNo)} 라벨</title>
      <style>
        @page{size:100mm 70mm;margin:0}*{box-sizing:border-box}
        body{margin:0;font-family:Arial,sans-serif;color:#0f172a}
        .label{width:100mm;height:70mm;padding:6mm;border:1px solid #0f172a;display:grid;grid-template-columns:32mm 1fr;gap:5mm}
        .qr{width:30mm;height:30mm}.info h1{margin:0 0 3mm;font:700 15pt monospace}
        .info p{margin:1.5mm 0;font-size:8.5pt}.barcode{grid-column:1/-1;text-align:center}
        .barcode svg{max-width:86mm;height:19mm}.hint{margin-top:1mm;font-size:7pt;color:#64748b}
      </style></head><body><section class="label">
        <div><img class="qr" src="${qrDataUrl}" alt="LOT QR"><div class="hint">QR: LOT 이력 조회</div></div>
        <div class="info"><h1>${escapeHtml(lot.lotNo)}</h1>
          <p><strong>품목</strong> ${escapeHtml(lot.itemName)} (${escapeHtml(lot.itemCode)})</p>
          <p><strong>작업지시</strong> ${escapeHtml(lot.orderNo)}</p>
          <p><strong>투입 수량</strong> ${escapeHtml(lot.inputQty)}</p>
          <p><strong>생성 일시</strong> ${escapeHtml(formatDate(lot.createdAt))}</p>
        </div>
        <div class="barcode">${barcodeRef.current.outerHTML}</div>
      </section><script>window.onload=()=>window.print();</script></body></html>`);
    printWindow.document.close();
  };

  return (
    <Modal
      title="LOT QR · 바코드 라벨"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn secondary" onClick={copyLink}>링크 복사</button>
          <button type="button" className="btn secondary" onClick={downloadLabel} disabled={!qrDataUrl}>PNG 저장</button>
          <button type="button" className="btn" onClick={printLabel} disabled={!qrDataUrl}>인쇄</button>
        </>
      }
    >
      <div className="lot-label-preview">
        <div className="lot-label-qr">
          {qrDataUrl ? <img src={qrDataUrl} alt={`${lot.lotNo} 추적 QR 코드`} /> : <span>QR 생성 중...</span>}
          <small>스캔: LOT 통합 타임라인</small>
        </div>
        <div className="lot-label-info">
          <h3>{lot.lotNo}</h3>
          <dl>
            <dt>품목</dt><dd>{lot.itemName} ({lot.itemCode})</dd>
            <dt>작업지시</dt><dd>{lot.orderNo || "-"}</dd>
            <dt>투입 수량</dt><dd>{lot.inputQty ?? "-"}</dd>
            <dt>생성 일시</dt><dd>{formatDate(lot.createdAt)}</dd>
          </dl>
        </div>
        <div className="lot-label-barcode"><svg ref={barcodeRef} aria-label={`${lot.lotNo} 바코드`} /></div>
      </div>
      <label className="lot-trace-link">
        <span>QR 연결 주소</span>
        <input value={traceUrl} readOnly onFocus={(event) => event.target.select()} />
      </label>
      {message && <p className="lot-label-message" role="status">{message}</p>}
    </Modal>
  );
}
