const express = require('express');
const router = express.Router();
const PDFDocument = require('pdfkit');
const fs = require('fs');
const path = require('path');
const nodemailer = require('nodemailer');
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');
let sharp = null;
try { sharp = require('sharp'); } catch (_) { sharp = null; }

// Ensure `routes/img` exists and provide helpers to resolve image paths or write data-URIs
const IMG_DIR = path.join(__dirname, 'img');
if (!fs.existsSync(IMG_DIR)) {
    try { fs.mkdirSync(IMG_DIR, { recursive: true }); } catch (e) { console.warn('Could not create img dir:', e.message); }
}

function writeDataUriToFile(dataUri, prefix) {
    try {
        const m = String(dataUri).match(/^data:([^;]+);base64,(.+)$/);
        if (!m) return null;
        const mime = m[1];
        const b64 = m[2];
        const ext = (mime.split('/')[1] || 'png').replace(/[^a-z0-9]/gi, '');
        const fname = `${prefix || 'img'}_${Date.now()}.${ext}`;
        const p = path.join(IMG_DIR, fname);
        fs.writeFileSync(p, Buffer.from(b64, 'base64'));
        console.log('[reports] writeDataUriToFile -> wrote', p, 'mime=', mime, 'b64len=', b64.length);
        return p;
    } catch (e) {
        console.error('writeDataUriToFile error:', e && e.stack ? e.stack : e.message);
        return null;
    }
}

function resolveImagePath(value) {
    if (!value) return null;
    try {
        if (typeof value !== 'string') return null;
        // data URI -> write to file
        if (value.startsWith('data:')) {
            const p = writeDataUriToFile(value, 'embedded');
            console.log('[reports] resolveImagePath -> data: URI written to', p);
            return p;
        }
        // plain base64 (no data: prefix) -> assume jpeg and write
        // Heuristic: reasonably long and consists of base64 chars
        const maybeB64 = value.replace(/\s+/g, '');
        if (maybeB64.length > 100 && /^[A-Za-z0-9+/=]+$/.test(maybeB64)) {
            try {
                const dataUri = `data:image/jpeg;base64,${maybeB64}`;
                const p = writeDataUriToFile(dataUri, 'embedded');
                console.log('[reports] resolveImagePath -> base64 (no prefix) written to', p);
                return p;
            } catch (e) {
                console.error('[reports] resolveImagePath base64 write error:', e && e.stack ? e.stack : e.message);
                // fallthrough to other resolution attempts
            }
        }
        // If caller sent just a filename, try to resolve in IMG_DIR
        const candidate = path.join(IMG_DIR, path.basename(value));
        if (fs.existsSync(candidate)) return candidate;
        // If value looks like a path relative to project, try it directly
        if (fs.existsSync(value)) return value;
        return null;
    } catch (e) { return null; }
}

function decodeImageBuffer(value) {
    try {
        if (!value) return null;
        if (Buffer.isBuffer(value)) return value;

        if (typeof value === 'object' && value.type === 'Buffer' && Array.isArray(value.data)) {
            try { return Buffer.from(value.data); } catch (e) { return null; }
        }

        if (typeof value !== 'string') return null;
        let current = value.trim();
        if (!current) return null;

        const looksLikeB64Text = (text) => {
            const cleaned = String(text || '').replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/');
            return cleaned.length > 100 && /^[A-Za-z0-9+/=]+$/.test(cleaned);
        };

        for (let depth = 0; depth < 4; depth += 1) {
            let encoded = current;
            if (encoded.startsWith('data:')) {
                const m = encoded.match(/^data:([^;]+);base64,(.+)$/);
                if (!m) return null;
                encoded = m[2] || '';
            }
            encoded = encoded.replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/');
            if (!looksLikeB64Text(encoded)) {
                return null;
            }

            const decoded = Buffer.from(encoded, 'base64');
            if (!decoded || decoded.length < 8) return decoded;

            // If the decoded bytes are still plain-text base64, keep unwrapping.
            const decodedText = decoded.toString('utf8').trim();
            if (looksLikeB64Text(decodedText)) {
                current = decodedText;
                continue;
            }

            return decoded;
        }

        return null;
    } catch (e) {
        return null;
    }
}

function isPdfKitSupportedImageBuffer(buf) {
    if (!Buffer.isBuffer(buf) || buf.length < 12) return false;
    // JPEG
    if (buf[0] === 0xFF && buf[1] === 0xD8) return true;
    // PNG
    if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4E && buf[3] === 0x47) return true;
    return false;
}

async function convertToJpegBuffer(buf, logTag) {
    if (!sharp || !Buffer.isBuffer(buf) || buf.length < 12) return null;
    try {
        return await sharp(buf).jpeg({ quality: 85 }).toBuffer();
    } catch (e) {
        console.error(`${logTag} sharp convert failed:`, e && e.stack ? e.stack : e.message);
        return null;
    }
}

async function renderImageInBoxAsync(doc, raw, x, y, w, h, logTag) {
    // 1) Try with resolved path directly
    let resolvedPath = null;
    try {
        resolvedPath = resolveImagePath(raw);
        if (resolvedPath && fs.existsSync(resolvedPath)) {
            try {
                doc.image(resolvedPath, x, y, { fit: [w, h], align: 'center', valign: 'center' });
                return true;
            } catch (e) {
                console.error(`${logTag} image by path failed:`, e && e.stack ? e.stack : e.message);
            }
        }
    } catch (e) {
        console.error(`${logTag} resolve path failed:`, e && e.stack ? e.stack : e.message);
    }

    // 2) Try with decoded buffer directly
    let rawBuffer = null;
    try {
        rawBuffer = decodeImageBuffer(raw);
        if (rawBuffer && rawBuffer.length > 32) {
            try {
                doc.image(rawBuffer, x, y, { fit: [w, h], align: 'center', valign: 'center' });
                return true;
            } catch (e) {
                console.error(`${logTag} image by buffer failed:`, e && e.stack ? e.stack : e.message);
            }
        }
    } catch (e) {
        console.error(`${logTag} decode buffer failed:`, e && e.stack ? e.stack : e.message);
    }

    // 3) Convert unsupported formats to JPEG using sharp
    try {
        let sourceBuffer = rawBuffer;
        if ((!sourceBuffer || sourceBuffer.length < 12) && resolvedPath && fs.existsSync(resolvedPath)) {
            sourceBuffer = fs.readFileSync(resolvedPath);
        }
        if (sourceBuffer && !isPdfKitSupportedImageBuffer(sourceBuffer)) {
            const jpegBuffer = await convertToJpegBuffer(sourceBuffer, logTag);
            if (jpegBuffer && jpegBuffer.length > 32) {
                doc.image(jpegBuffer, x, y, { fit: [w, h], align: 'center', valign: 'center' });
                return true;
            }
        }
    } catch (e) {
        console.error(`${logTag} jpeg conversion render failed:`, e && e.stack ? e.stack : e.message);
    }

    return false;
}

function drawDesechoTireDiagram(doc, flattened, selectedUbi) {
    const locationCounts = (flattened || []).reduce((acc, ll) => {
        const key = ll && ll.LlantasDesechoUbi ? String(ll.LlantasDesechoUbi) : 'Sin ubicación';
        acc[key] = (acc[key] || 0) + 1;
        return acc;
    }, {});

    const total = Math.max((flattened || []).length, 1);
    const locations = {
        'Banda de rodamiento': locationCounts['Banda de rodamiento'] || 0,
        'Costado': locationCounts['Costado'] || 0,
        'Hombro': locationCounts['Hombro'] || 0,
        'Caja / Pestaña': locationCounts['Caja / Pestaña'] || 0,
        'Liner': locationCounts['Liner'] || 0
    };

    const getPct = (count) => `${Math.floor((count * 100) / total)}%`;
    const selected = selectedUbi ? String(selectedUbi) : null;
    const imgPath = path.join(__dirname, 'img', 'llanta_corte_t.png');
    const pageWidth = doc.page && doc.page.width ? doc.page.width : 595;
    const centerX = Math.round(pageWidth / 2);
    const imageSize = 180;
    const blockHeight = 280;

    if (doc.y > doc.page.height - blockHeight) doc.addPage();
    const startY = Number(doc.y) || 0;
    const imageX = centerX - Math.floor(imageSize / 2);
    const imageY = startY + 28;

    try {
        doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Ubicación de desecho', 40, startY, { align: 'left' });
    } catch (e) {}

    const topY = startY + 16;
    const leftX = imageX - 75;
    const rightX = imageX + imageSize + 10;
    const bottomRightX = imageX + imageSize - 10;

    const drawLabel = (x, y, title, count, align = 'left') => {
        try {
            const isSelected = selected && title === selected;
            doc.font('Helvetica').fontSize(9).fillColor(isSelected ? '#D32F2F' : '#000000');
            doc.text(title, x, y, { width: 130, align });
            doc.font('Helvetica').fontSize(8).fillColor('#666666').text(getPct(count), x, y + 10, { width: 130, align });
        } catch (e) {}
    };

    // Top
    drawLabel(centerX - 60, topY, 'Banda de rodamiento', locations['Banda de rodamiento'], 'center');
    // Left
    drawLabel(leftX, imageY + 60, 'Costado', locations['Costado'], 'center');
    // Right
    drawLabel(rightX, imageY + 30, 'Hombro', locations['Hombro'], 'center');
    // Bottom-right
    drawLabel(bottomRightX - 55, imageY + imageSize - 5, 'Caja / Pestaña', locations['Caja / Pestaña'], 'center');
    // Bottom
    drawLabel(centerX - 20, imageY + imageSize + 8, 'Liner', locations['Liner'], 'center');

    try {
        if (fs.existsSync(imgPath)) {
            doc.image(imgPath, imageX, imageY, { width: imageSize, height: imageSize, fit: [imageSize, imageSize], align: 'center', valign: 'center' });
        } else {
            doc.rect(imageX, imageY, imageSize, imageSize).stroke('#999999');
        }
    } catch (e) {
        console.error('[reports][desecho] tire diagram image failed:', e && e.stack ? e.stack : e.message);
    }

    try {
        doc.y = startY + blockHeight;
    } catch (e) {}
}

function normalizeEmailList(value) {
    if (Array.isArray(value)) {
        return value.map((item) => String(item || '').trim().toLowerCase()).filter(Boolean);
    }
    return String(value || '')
        .split(',')
        .map((item) => item.trim().toLowerCase())
        .filter(Boolean);
}

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function normalizeCellText(value) {
    return String(value || '').replace(/\s+/g, ' ').trim();
}

function normalizeTableData(tableData) {
    const rawHeaders = Array.isArray(tableData?.headers) ? tableData.headers : [];
    const rawRows = Array.isArray(tableData?.rows) ? tableData.rows : [];

    const headers = rawHeaders.map(normalizeCellText).filter(Boolean);
    const rows = rawRows
        .filter((row) => Array.isArray(row))
        .map((row) => row.map(normalizeCellText));

    const columnsFromRows = rows.reduce((max, row) => Math.max(max, row.length), 0);
    const columnCount = Math.max(headers.length, columnsFromRows);

    if (columnCount === 0) {
        return { headers: [], rows: [] };
    }

    const paddedHeaders = headers.length
        ? [...headers, ...Array(Math.max(0, columnCount - headers.length)).fill('')]
        : Array.from({ length: columnCount }, (_, index) => `Columna ${index + 1}`);

    const paddedRows = rows.map((row) => [...row, ...Array(Math.max(0, columnCount - row.length)).fill('')]);

    return {
        headers: paddedHeaders,
        rows: paddedRows
    };
}

function sanitizeFileNamePart(value) {
    return String(value || 'reporte')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z0-9_-]/g, '_')
        .replace(/_+/g, '_')
        .replace(/^_+|_+$/g, '') || 'reporte';
}

function createPdfBufferFromTable({ title, headers, rows }) {
    return new Promise((resolve, reject) => {
        try {
            const doc = new PDFDocument({ size: 'A4', layout: 'landscape', margin: 28 });
            const chunks = [];

            doc.on('data', (chunk) => chunks.push(chunk));
            doc.on('end', () => resolve(Buffer.concat(chunks)));
            doc.on('error', reject);

            const left = doc.page.margins.left;
            const right = doc.page.margins.right;
            const top = doc.page.margins.top;
            const bottom = doc.page.margins.bottom;

            const pageWidth = doc.page.width - left - right;
            const columnCount = headers.length;
            const minWidth = 48;
            const columnWidth = Math.max(minWidth, pageWidth / Math.max(1, columnCount));
            const tableWidth = columnWidth * columnCount;
            const startX = left;

            doc.font('Helvetica-Bold').fontSize(14).text(title || 'Reporte', left, top);
            doc.moveDown(0.2);
            doc.font('Helvetica').fontSize(9).fillColor('#4b5563').text(`Generado: ${new Date().toLocaleString('es-MX')}`);

            let cursorY = doc.y + 10;

            const drawRow = (cells, isHeader) => {
                const values = cells.map(normalizeCellText);
                doc.font(isHeader ? 'Helvetica-Bold' : 'Helvetica').fontSize(8);

                const contentHeights = values.map((text) => {
                    return doc.heightOfString(text || ' ', {
                        width: columnWidth - 8,
                        align: 'left'
                    });
                });

                const rowHeight = Math.max(18, ...contentHeights.map((h) => h + 8));

                if (cursorY + rowHeight > doc.page.height - bottom) {
                    doc.addPage({ size: 'A4', layout: 'landscape', margin: 28 });
                    cursorY = doc.page.margins.top;
                    drawRow(headers, true);
                }

                let x = startX;
                for (let index = 0; index < columnCount; index += 1) {
                    if (isHeader) {
                        doc.save();
                        doc.rect(x, cursorY, columnWidth, rowHeight).fill('#e5e7eb');
                        doc.restore();
                    }

                    doc.lineWidth(0.4).strokeColor('#9ca3af').rect(x, cursorY, columnWidth, rowHeight).stroke();
                    doc.fillColor('#111827').text(values[index] || '', x + 4, cursorY + 4, {
                        width: columnWidth - 8,
                        height: rowHeight - 8,
                        align: 'left'
                    });

                    x += columnWidth;
                }

                cursorY += rowHeight;
            };

            drawRow(headers, true);
            rows.forEach((row) => drawRow(row, false));

            if (tableWidth > pageWidth) {
                doc.moveDown(1);
                doc.font('Helvetica-Oblique').fontSize(7).fillColor('#6b7280')
                    .text('Nota: El reporte tiene muchas columnas; algunas pueden verse compactadas para ajustarse a la página.', left);
            }

            doc.end();
        } catch (error) {
            reject(error);
        }
    });
}

router.post('/send-table-email', async (req, res) => {
    try {
        const { emails, subject, viewName, tableData } = req.body || {};
        const recipients = normalizeEmailList(emails);

        if (!recipients.length) {
            return res.status(400).json({ message: 'Debe indicar al menos un correo' });
        }

        const invalidEmails = recipients.filter((email) => !isValidEmail(email));
        if (invalidEmails.length > 0) {
            return res.status(400).json({ message: `Correos inválidos: ${invalidEmails.join(', ')}` });
        }

        const normalizedTable = normalizeTableData(tableData);
        if (!normalizedTable.headers.length || !normalizedTable.rows.length) {
            return res.status(400).json({ message: 'No hay datos de tabla para enviar en PDF' });
        }

        const smtpHost = process.env.SMTP_HOST;
        const smtpPort = Number(process.env.SMTP_PORT || 587);
        const smtpUser = process.env.SMTP_USER;
        const smtpPass = process.env.SMTP_PASS;
        const smtpFrom = process.env.SMTP_FROM || smtpUser;
        const smtpSecure = String(process.env.SMTP_SECURE || 'false').toLowerCase() === 'true';

        if (!smtpHost || !smtpUser || !smtpPass || !smtpFrom) {
            return res.status(400).json({
                message: 'Falta configuración SMTP (SMTP_HOST, SMTP_USER, SMTP_PASS, SMTP_FROM)'
            });
        }

        const transporter = nodemailer.createTransport({
            host: smtpHost,
            port: smtpPort,
            secure: smtpSecure,
            auth: {
                user: smtpUser,
                pass: smtpPass
            }
        });

        const safeViewName = String(viewName || 'Reporte').trim();
        const safeSubject = String(subject || `Reporte ${safeViewName}`).trim();
        const pdfBuffer = await createPdfBufferFromTable({
            title: safeSubject,
            headers: normalizedTable.headers,
            rows: normalizedTable.rows
        });
        const safeFilePart = sanitizeFileNamePart(safeViewName);
        const timestamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);
        const attachmentName = `${safeFilePart}_${timestamp}.pdf`;

        const html = `
            <div style="font-family:Segoe UI,Tahoma,Arial,sans-serif;color:#111827;">
                <h3 style="margin:0 0 12px 0;">${safeSubject}</h3>
                <p style="margin:0 0 12px 0;">Se adjunta el reporte en PDF de ${safeViewName}.</p>
            </div>
        `;

        await transporter.sendMail({
            from: smtpFrom,
            to: recipients.join(','),
            subject: safeSubject,
            html,
            text: `Se adjunta el reporte en PDF de ${safeViewName}.`,
            attachments: [
                {
                    filename: attachmentName,
                    content: pdfBuffer,
                    contentType: 'application/pdf'
                }
            ]
        });

        return res.json({ message: 'Correo enviado correctamente' });
    } catch (error) {
        return handleServerError(res, 'Error al enviar correo', error);
    }
});

// Send a link to the current page (with flota context) instead of attaching a PDF
router.post('/send-link-email', async (req, res) => {
    try {
        const { emails, viewName, link, flota, referencia } = req.body || {};
        const recipients = normalizeEmailList(emails);

        if (!recipients.length) {
            return res.status(400).json({ message: 'Debe indicar al menos un correo' });
        }

        const invalidEmails = recipients.filter((email) => !isValidEmail(email));
        if (invalidEmails.length > 0) {
            return res.status(400).json({ message: `Correos inválidos: ${invalidEmails.join(', ')}` });
        }

        const smtpHost = process.env.SMTP_HOST;
        const smtpPort = Number(process.env.SMTP_PORT || 587);
        const smtpUser = process.env.SMTP_USER;
        const smtpPass = process.env.SMTP_PASS;
        const smtpFrom = process.env.SMTP_FROM || smtpUser;
        const smtpSecure = String(process.env.SMTP_SECURE || 'false').toLowerCase() === 'true';

        if (!smtpHost || !smtpUser || !smtpPass || !smtpFrom) {
            return res.status(400).json({ message: 'Falta configuración SMTP (SMTP_HOST, SMTP_USER, SMTP_PASS, SMTP_FROM)' });
        }

        const transporter = nodemailer.createTransport({
            host: smtpHost,
            port: smtpPort,
            secure: smtpSecure,
            auth: { user: smtpUser, pass: smtpPass }
        });

        const safeViewName = String(viewName || 'Reporte').trim();
        const safeFlota = String(flota || '').trim();
        const safeReferencia = String(referencia || '').trim();

        const subject = `Notificación ${safeViewName}${safeFlota ? ' - ' + safeFlota : ''}`;

        const html = `
            <div style="font-family:Segoe UI,Tahoma,Arial,sans-serif;color:#111827;">
                <p>Buen día,</p>
                <p>Se ha registrado un nuevo <strong>${safeViewName.toLowerCase()}</strong> para tu flota, para ingresar al detalle da click <a href="${link}">aquí</a>.</p>
                <p><strong>Flota</strong> : ${safeFlota || '—'}</p>
                <p><strong>Referencia</strong> : ${safeReferencia || '—'}</p>
            </div>
        `;

        await transporter.sendMail({
            from: smtpFrom,
            to: recipients.join(','),
            subject,
            html,
            text: `Buen día, se ha registrado un nuevo ${safeViewName.toLowerCase()} para tu flota. Accede: ${link}`
        });

        return res.json({ message: 'Correo enviado correctamente' });
    } catch (error) {
        return handleServerError(res, 'Error al enviar correo (link)', error);
    }
});

// Ensure a small placeholder logo exists so PDFs render consistently
function ensureLogoPlaceholder() {
    try {
        const logoPath = path.join(IMG_DIR, 'yokohamalogo.png');
        if (!fs.existsSync(logoPath)) {
            // 1x1 transparent PNG
            const tinyPngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8Xw8AAn8B9Wb5s1wAAAAASUVORK5CYII=';
            fs.writeFileSync(logoPath, Buffer.from(tinyPngBase64, 'base64'));
        }
        return logoPath;
    } catch (e) { console.warn('ensureLogoPlaceholder error', e.message); return null; }
}

// Draw a pie slice by approximating the arc with many points and filling the polygon
function deg2rad(d) { return (d * Math.PI) / 180; }
function drawPieSlice(doc, cx, cy, r, startDeg, endDeg, options) {
    try {
        const steps = Math.max(8, Math.min(64, Math.round(Math.abs(endDeg - startDeg) / 5)));
        const points = [];
        points.push([cx, cy]);
        for (let i = 0; i <= steps; i++) {
            const t = i / steps;
            const ang = deg2rad(startDeg + (endDeg - startDeg) * t);
            const x = cx + Math.cos(ang) * r;
            const y = cy + Math.sin(ang) * r;
            points.push([x, y]);
        }
        // move to first point
        const p0 = points[0];
        doc.moveTo(p0[0], p0[1]);
        for (let i = 1; i < points.length; i++) {
            doc.lineTo(points[i][0], points[i][1]);
        }
        doc.closePath();
        if (options && options.fill) doc.fill(options.fill);
        else doc.fill();
    } catch (e) { /* ignore drawing errors */ }
}

async function generateReport(req, res) {
    const vehiculoId = req.params.vehiculoId;

    try {
        const [vehRows] = await db.query(`
            SELECT v.idVehiculos, v.VehiculosNumero, v.VehiculosOdometro, v.VehiculosImagen, t.TipoVehiculosNombre
            FROM vehiculos v
            LEFT JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
            WHERE v.idVehiculos = ?
        `, [vehiculoId]);

        if (vehRows.length === 0) {
            return res.status(404).json({ message: 'Vehículo no encontrado' });
        }
    const veh = vehRows[0];


        const [pruebaRows] = await db.query(`
            SELECT idPruebaRendimiento, PruebaRendimientoOdometro, PruebaRendimientoFecha
            FROM pruebarendimiento
            WHERE Vehiculos_idVehiculos = ?
            ORDER BY idPruebaRendimiento DESC
            LIMIT 1
        `, [vehiculoId]);
    const latestPruebaForVeh = (pruebaRows && pruebaRows.length) ? pruebaRows[0] : null;

        const [rows] = await db.query(`
            SELECT
                lv.idLlantasVehiculos,
                lv.LlantasVehiculosNoQuemado AS NoLlanta,
                lv.LlantasVehiculosPrecio AS PrecioLlanta,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                -- mediciones guardadas en LlantasVehiculos al montar la llanta (fallback)
                lv.LlantasVehiculosMM1 AS MMInst1,
                lv.LlantasVehiculosMM2 AS MMInst2,
                lv.LlantasVehiculosMM3 AS MMInst3,
                lv.LlantasVehiculosMM4 AS MMInst4,
                -- última medición (actual)
                 lr_latest.LlantasRendimientoMm1 AS MMFinal1,
                 lr_latest.LlantasRendimientoMm2 AS MMFinal2,
                 lr_latest.LlantasRendimientoMm3 AS MMFinal3,
                 lr_latest.LlantasRendimientoMm4 AS MMFinal4,
                 lr_latest.LlantasRendimientoFoto AS FotoLatest,
                 lr_latest.idLlantasRendimiento AS LlantasRendimientoIdLatest,
                 pr_latest.idPruebaRendimiento AS PruebaRendimientoIdLatest,
                 pr_latest.PruebaRendimientoOdometro AS OdometroFinal,
                 -- medición previa (anterior a la última)
                 lr_prev.LlantasRendimientoMm1 AS MMPrev1,
                 lr_prev.LlantasRendimientoMm2 AS MMPrev2,
                 lr_prev.LlantasRendimientoMm3 AS MMPrev3,
                 lr_prev.LlantasRendimientoMm4 AS MMPrev4,
                 pr_prev.PruebaRendimientoOdometro AS OdometroPrev,
                l.LlantasMm AS LlantaMm
            FROM llantasvehiculos lv
            JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
            -- última medición por llanta
            LEFT JOIN llantasrendimiento lr_latest ON lr_latest.idLlantasRendimiento = (
                SELECT idLlantasRendimiento FROM llantasrendimiento
                WHERE LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
                ORDER BY idLlantasRendimiento DESC LIMIT 1
            )
            LEFT JOIN pruebarendimiento pr_latest ON pr_latest.idPruebaRendimiento = lr_latest.PruebaRendimiento_idPruebaRendimiento
            -- medición previa (la inmediatamente anterior a la última)
                        LEFT JOIN llantasrendimiento lr_prev ON lr_prev.idLlantasRendimiento = (
                                SELECT idLlantasRendimiento FROM llantasrendimiento
                                WHERE LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
                                    AND idLlantasRendimiento < COALESCE((
                                            SELECT idLlantasRendimiento FROM llantasrendimiento
                                            WHERE LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
                                            ORDER BY idLlantasRendimiento DESC LIMIT 1
                                    ), 0)
                                ORDER BY idLlantasRendimiento DESC LIMIT 1
                        )
            LEFT JOIN pruebarendimiento pr_prev ON pr_prev.idPruebaRendimiento = lr_prev.PruebaRendimiento_idPruebaRendimiento
            WHERE lv.Vehiculos_idVehiculos = ?
            ORDER BY lv.idLlantasVehiculos ASC
        `, [vehiculoId]);

        // === 3) Cálculos ===
            const processed = (rows || []).map(r => {
            // calcular promedios mm de instalación, previa y final (usar los que existan)
            const instMMs = [r.MMInst1, r.MMInst2, r.MMInst3, r.MMInst4].map(v => Number(v || 0));
            const prevMMs = [r.MMPrev1, r.MMPrev2, r.MMPrev3, r.MMPrev4].map(v => v === null || v === undefined ? null : Number(v));
            const finalMMs = [r.MMFinal1, r.MMFinal2, r.MMFinal3, r.MMFinal4].map(v => v === null || v === undefined ? null : Number(v));

            const avg = (arr) => {
                const nums = arr.filter(v => v !== null && v !== undefined && !isNaN(v));
                if (nums.length === 0) return 0;
                return nums.reduce((a, b) => a + b, 0) / nums.length;
            };

            // preferir prevMMs si existen, si no usar MMInst como 'inicial'
            const mmInicial = (prevMMs.some(v => v !== null) ? avg(prevMMs.map(v => v === null ? 0 : v)) : avg(instMMs));
            const mmFinal = avg(finalMMs.map(v => v === null ? 0 : v));

            // Use the vehicle's odometer as the baseline (VehiculosOdometro)
            // and the prueba rendimiento odometer (from the latest llantasrendimiento) as the final odometer
            // Determine odInicial per requirement: use Vehiculos.VehiculosOdometro as the baseline
            // (this ensures $/km = Precio / (PruebaRendimientoOdometro - VehiculosOdometro)).
            // Only fallback to latestPruebaForVeh's odometer if VehiculosOdometro is missing.
            const vehicleOdoPresent = (veh.VehiculosOdometro !== null && typeof veh.VehiculosOdometro !== 'undefined');
            const odInicial = Number(vehicleOdoPresent ? veh.VehiculosOdometro : (latestPruebaForVeh && latestPruebaForVeh.PruebaRendimientoOdometro ? latestPruebaForVeh.PruebaRendimientoOdometro : 0));
            const odInicialSource = vehicleOdoPresent ? 'vehTabla' : (latestPruebaForVeh ? `prueba:${latestPruebaForVeh.idPruebaRendimiento}` : 'fallback:0');
            const odFinal = Number(r.OdometroFinal || 0);
            const precio = Number(r.PrecioLlanta || 0);
            const rendimientoId = r.LlantasRendimientoIdLatest || null;
            const pruebaId = r.PruebaRendimientoIdLatest || null;

            // kilómetros recorridos basados en la prueba más reciente asociada a la llanta
            const kmRecorridos = odFinal - odInicial;

            // Para km/mm: usar el registro del último LlantasRendimiento (MMFinal1..4)
            // y tomar el valor más bajo (mínimo) entre esas cuatro mediciones.
            const finalMMValues = finalMMs.filter(v => v !== null && v !== undefined && !isNaN(v));
            const mmLowest = (finalMMValues.length > 0) ? Math.min(...finalMMValues) : 0;

                // km/mm: usar km recorridos dividido por la diferencia entre
                // el mm original de la llanta (desde tabla Llantas) y el mmLowest
                // (valor mínimo del último LlantasRendimiento).
                const baseLlantaMm = Number(r.LlantaMm || 0);
                const mmDenominator = (baseLlantaMm - mmLowest);
                const kmPorMm = (mmDenominator > 0) ? (kmRecorridos / mmDenominator) : 0;
            const costoPorKm = (kmRecorridos > 0) ? (precio / kmRecorridos) : 0;
            // (depuración removida) -- values computed here are used in the PDF table

            return {
                idLlantasVehiculos: r.idLlantasVehiculos,
                Posicion: null, // will be computed below based on id ordering
                CostoPorKm: Number.isFinite(costoPorKm) ? costoPorKm : 0,
                KmPorMm: Number.isFinite(kmPorMm) ? kmPorMm : 0,
                MarcaModelo: `${r.LlantasMarca || ''} ${r.LlantasModelo || ''}`.trim(),
                Medida: r.LlantasMedida || '',
                NoLlanta: r.NoLlanta || r.idLlantasVehiculos || '—',
                Foto: r.FotoLatest || null,
                // include debug payload so we can render it in the PDF and inspect later
                Debug: {
                    rendimientoId,
                    pruebaId,
                    odInicial,
                    odInicialSource,
                    odFinal,
                    precio,
                    kmRecorridos,
                    mmLowest,
                        baseLlantaMm,
                        costoPorKm,
                        kmPorMm,
                }
            };
        });
        try {
            console.log('[reports] processed rows count=', processed.length, 'sample=', processed.slice(0,3).map(p=>({NoLlanta:p.NoLlanta, Foto: p.Foto}))); 
        } catch(e) { console.error('[reports] error logging processed sample', e && e.stack ? e.stack : e.message); }

        // === 4) Calcular posición basada en el orden por idLlantasVehiculos ===
        // Enumerar 1..N según id (el más bajo -> posición 1)
        processed.sort((a, b) => (a.idLlantasVehiculos || 0) - (b.idLlantasVehiculos || 0));
        processed.forEach((p, idx) => {
            p.Posicion = idx + 1;
        });

        // === 5) Agrupación ===
        const ejeDireccion = processed.filter(p => p.Posicion === 1 || p.Posicion === 2);
        const ejesLibres = processed.filter(p => p.Posicion !== 1 && p.Posicion !== 2);

        // === 5) Generar PDF ===
        const doc = new PDFDocument({ margin: 40, size: 'A4' });
        res.setHeader('Content-Type', 'application/pdf');
        res.setHeader('Content-Disposition', `inline; filename=vehiculo_${vehiculoId}_rendimiento.pdf`);
        doc.pipe(res);

        // --- ENCABEZADO ---
        // Try to render yokohamalogo.png from routes/img; fall back to text if missing
        try {
            const logoPath = path.join(__dirname, 'img', 'yokohamalogo.png');
            if (fs.existsSync(logoPath)) {
                // Center the logo horizontally; choose a slightly larger width
                const imgWidth = 260;
                const x = (doc.page.width - imgWidth) / 2;
                doc.image(logoPath, x, doc.y, { width: imgWidth });
                doc.moveDown(0.8);
            } else {
                doc.fontSize(24).font('Helvetica-Bold').fillColor('#3675a6')
                    .text('MEGATRANSPORTE', { align: 'center' });
                doc.moveDown(0.5);
            }
        } catch (e) {
            // Any failure loading the image -> fallback to text
            doc.fontSize(24).font('Helvetica-Bold').fillColor('#3675a6')
                .text('MEGATRANSPORTE', { align: 'center' });
            doc.moveDown(0.5);
        }

    // --- CONFIG BÁSICA PARA TABLA/ENCABEZADOS ---
    const COL_START = 40;
    const TABLE_WIDTH = 515;
    // aumentar la altura de fila para permitir miniaturas en la tabla
    const CELL_HEIGHT = 72;
    const BORDER_COLOR = '#AAAAAA';
    // Usar gris ligeramente más oscuro para los encabezados de columna
    const HEADER_BG = '#B3B3B3';

    // --- IMAGEN DEL VEHÍCULO (arriba a la derecha, sin afectar el flujo) ---
    const vehImgSize = 70;
    const vehImgX = COL_START + TABLE_WIDTH - vehImgSize;
    const vehImgY = 5; // pegado al borde superior, fuera del margen de contenido
    try {
        let vehImgBuffer = veh.VehiculosImagen;
        // MySQL devuelve BLOBs como Buffer aunque contengan base64 en texto.
        // Si el primer byte es ASCII imprimible (>= 0x20), el Buffer contiene texto base64.
        if (Buffer.isBuffer(vehImgBuffer) && vehImgBuffer.length > 0 && vehImgBuffer[0] >= 0x20) {
            vehImgBuffer = vehImgBuffer.toString('utf8');
        }
        if (typeof vehImgBuffer === 'string' && vehImgBuffer.length > 0) {
            const b64 = vehImgBuffer.includes(',') ? vehImgBuffer.split(',')[1] : vehImgBuffer;
            vehImgBuffer = Buffer.from(b64, 'base64');
        }
        if (Buffer.isBuffer(vehImgBuffer) && vehImgBuffer.length > 4) {
            // Log primeros bytes para diagnosticar formato
            const magic = Array.from(vehImgBuffer.slice(0, 12)).map(b => b.toString(16).padStart(2,'0')).join(' ');
            console.log('[rendimiento] magic bytes:', magic, '| len:', vehImgBuffer.length);
            // Detectar formato real por magic bytes
            let ext = null;
            if (vehImgBuffer[0] === 0xFF && vehImgBuffer[1] === 0xD8) ext = 'jpg';           // JPEG
            else if (vehImgBuffer[0] === 0x89 && vehImgBuffer[1] === 0x50) ext = 'png';      // PNG
            else if (vehImgBuffer[0] === 0x47 && vehImgBuffer[1] === 0x49) ext = 'gif';      // GIF
            else if (vehImgBuffer[0] === 0x42 && vehImgBuffer[1] === 0x4D) ext = 'bmp';      // BMP
            else if (vehImgBuffer[0] === 0x52 && vehImgBuffer[1] === 0x49) ext = 'webp';     // WEBP (RIFF)
            if (ext) {
                const tmpPath = path.join(IMG_DIR, `veh_tmp_${vehiculoId}.${ext}`);
                fs.writeFileSync(tmpPath, vehImgBuffer);
                doc.image(tmpPath, vehImgX, vehImgY, { width: vehImgSize, height: vehImgSize, fit: [vehImgSize, vehImgSize], align: 'center', valign: 'center' });
                try { fs.unlinkSync(tmpPath); } catch (_) {}
            } else {
                console.warn('[rendimiento] Formato de imagen no reconocido, magic:', magic);
            }
        }
    } catch (e) { console.error('[rendimiento] Error imagen vehiculo:', e.message); }

    // Encabezado con fondo para la fila del vehículo (mismo color que los headers de columna)
    doc.fontSize(10).font('Helvetica');
    const vehText = `Vehículo: ${veh.VehiculosNumero || vehiculoId}, tipo de vehículo: ${veh.TipoVehiculosNombre || 'Autobús'}`;
    // mostrar solo la fecha en que se generó el PDF (sin hora)
    const dateText = new Date().toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: '2-digit' });

    const yHeader = doc.y;
    // dibujar fondo de la fila de vehículo con color oscuro (como antes)
    doc.rect(COL_START, yHeader, TABLE_WIDTH, CELL_HEIGHT).fill('#333333');
    // texto en blanco: nombre del vehículo a la izquierda, fecha a la derecha (movida un poco a la izquierda)
    doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(10);
    doc.text(vehText, COL_START + 6, yHeader + 5, { width: TABLE_WIDTH - 140 });
    // desplazar la fecha algo a la izquierda para dejar margen
    doc.text(dateText, COL_START + TABLE_WIDTH - 110, yHeader + 5, { width: 100, align: 'right' });
    // restaurar color y fuente
    doc.fillColor('#000000').font('Helvetica');
    doc.moveDown(1.8);

        // --- CONFIG TABLA ---
        const columns = [
            { header: 'Foto', width: 70 },
            { header: 'Posición', width: 55 },
            { header: '$/Km', width: 65 },
            { header: 'Km/mm', width: 65 },
            { header: 'Marca/Modelo', width: 110 },
            { header: 'Medida', width: 65 },
            { header: 'No. Llanta', width: 40 }
        ];

        // Formatters for localized numbers with thousands separators
        const nfCost = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
        const nfKmMm = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        // available width for flowing text (page width minus margins)
        const availWidth = (doc.page && doc.page.width ? doc.page.width : 595) - (doc.page && doc.page.margins ? (doc.page.margins.left + doc.page.margins.right) : 80) - 40;
        
        const drawHeader = (y) => {
            // dibujar fondo gris claro y luego forzar color de texto a blanco
            try { doc.rect(COL_START, y, TABLE_WIDTH, CELL_HEIGHT).fill(HEADER_BG); } catch(e) {}
            doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(10);
            let x = COL_START;
            const headerTextY = y + 18;
            columns.forEach(col => {
                doc.text(col.header, x + 4, headerTextY, { width: col.width - 5 });
                x += col.width;
            });
            // restaurar color de texto a negro por si se usa luego
            doc.fillColor('#000000').font('Helvetica');
            return y + CELL_HEIGHT;
        };

        const drawRow = (y, data) => {
            doc.font('Helvetica').fontSize(9).fillColor('#000000');
            let x = COL_START;
            data.forEach((text, i) => {
                const colWidth = columns[i].width;
                // If this is the Foto column, try to render image
                if (i === 0) {
                    try {
                        console.log('[reports] drawRow Foto value length=', text ? String(text).length : 0);
                        if (text) {
                            const imgPath = resolveImagePath(text);
                            console.log('[reports] drawRow -> resolveImagePath returned', imgPath);
                            if (imgPath && fs.existsSync(imgPath)) {
                                try {
                                    doc.image(imgPath, x + 4, y + 4, { width: colWidth - 8, height: CELL_HEIGHT - 8, fit: [colWidth - 8, CELL_HEIGHT - 8], align: 'center', valign: 'center' });
                                } catch (e) {
                                    console.error('[reports] drawRow image render error:', e && e.stack ? e.stack : e.message);
                                    doc.fontSize(9).text('No se pudo renderizar', x + 4, y + 18, { width: colWidth - 5 });
                                }
                            } else {
                                doc.fontSize(9).text('—', x + 4, y + 18, { width: colWidth - 5 });
                            }
                        } else {
                            doc.fontSize(9).text('—', x + 4, y + 18, { width: colWidth - 5 });
                        }
                    } catch (err) {
                        console.error('[reports] drawRow Foto unexpected error:', err && err.stack ? err.stack : err.message);
                        doc.fontSize(9).text('—', x + 4, y + 18, { width: colWidth - 5 });
                    }
                } else {
                    // asegurar que el texto no sea undefined
                    const cellText = (text === undefined || text === null) ? '—' : String(text);
                    const textY = y + 18;
                    doc.text(cellText, x + 4, textY, { width: colWidth - 5 });
                }

                doc.strokeColor(BORDER_COLOR)
                    .moveTo(x, y)
                    .lineTo(x, y + CELL_HEIGHT)
                    .stroke();
                x += colWidth;
            });
            // Cerrar borde derecho de la tabla
            doc.strokeColor(BORDER_COLOR)
                .moveTo(COL_START + TABLE_WIDTH, y)
                .lineTo(COL_START + TABLE_WIDTH, y + CELL_HEIGHT)
                .stroke();
            doc.moveTo(COL_START, y + CELL_HEIGHT).lineTo(COL_START + TABLE_WIDTH, y + CELL_HEIGHT).strokeColor(BORDER_COLOR).stroke();
            return y + CELL_HEIGHT;
        };

        // === Eje de dirección ===
        doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Eje de dirección', COL_START, doc.y);
        let y = doc.y + 5;
        y = drawHeader(y);
        if ((ejeDireccion || []).length === 0) {
            y = drawRow(y, ['', '—', '—', '—', '—', '—', '—']);
        } else {
            ejeDireccion.forEach(p => {
                const costoStr = `$ ${nfCost.format(Number.isFinite(p.CostoPorKm) ? p.CostoPorKm : 0)}`;
                const kmMmStr = nfKmMm.format(Number.isFinite(p.KmPorMm) ? p.KmPorMm : 0);
                y = drawRow(y, [
                    p.Foto || '',
                    p.Posicion,
                    costoStr,
                    kmMmStr,
                    p.MarcaModelo,
                    p.Medida,
                    p.NoLlanta
                ]);
            });
        }
        doc.moveDown(1.5);

        // === Ejes libres ===
        doc.fontSize(12).font('Helvetica-Bold').text('Ejes libres', COL_START, doc.y);
        y = doc.y + 5;
        y = drawHeader(y);
        if ((ejesLibres || []).length === 0) {
            y = drawRow(y, ['', '—', '—', '—', '—', '—', '—']);
        } else {
            ejesLibres.forEach(p => {
                const costoStr = `$ ${nfCost.format(Number.isFinite(p.CostoPorKm) ? p.CostoPorKm : 0)}`;
                const kmMmStr = nfKmMm.format(Number.isFinite(p.KmPorMm) ? p.KmPorMm : 0);
                y = drawRow(y, [
                    p.Foto || '',
                    p.Posicion,
                    costoStr,
                    kmMmStr,
                    p.MarcaModelo,
                    p.Medida,
                    p.NoLlanta
                ]);
            });
        }


        // Si no hay filas en absoluto, añadir una línea informativa
        if ((processed || []).length === 0) {
            doc.moveDown(1);
            doc.fontSize(10).fillColor('#333333').text('No se encontraron registros de llantas para este vehículo.', COL_START, doc.y);
        } else {
            const photoRows = (processed || []).filter(p => p.Foto);
            if (photoRows.length > 0) {
                doc.moveDown(1.2);
                doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Fotos de llantas', COL_START, doc.y);
                doc.moveDown(0.5);
                photoRows.forEach((p) => {
                    const label = `Llanta ${p.Posicion} - ${p.NoLlanta}`;
                    const imgPath = resolveImagePath(p.Foto);
                    if (!imgPath) return;
                    if (doc.y > doc.page.height - doc.page.margins.bottom - 150) {
                        doc.addPage();
                    }
                    doc.fontSize(10).font('Helvetica-Bold').text(label, COL_START, doc.y);
                    try {
                        doc.image(imgPath, COL_START, doc.y + 4, { width: 120, height: 120, fit: [120, 120], align: 'center', valign: 'center' });
                    } catch (e) {
                        doc.fontSize(9).font('Helvetica').text('No se pudo renderizar la imagen.', COL_START, doc.y + 10);
                    }
                    doc.moveDown(8);
                });
            }
        }
        // Manejo de errores de stream para detectar problemas al enviar PDF
        doc.on('error', (err) => {
            console.error('Error en stream PDF:', err);
            try {
                if (!res.headersSent) {
                    handleServerError(res, 'Error generando PDF', err);
                } else {
                    // si headers ya enviados, terminar la respuesta
                    res.end();
                }
            } catch (e) {
                console.error('Error al terminar respuesta tras fallo en PDF:', e);
            }
        });

        res.on('error', (err) => {
            console.error('Error en response stream:', err);
        });

        doc.end();
    } catch (error) {
        console.error('Error generando reporte:', error);
        handleServerError(res, 'Error al generar el reporte de rendimiento', error);
    }
}

router.get('/vehiculo/:vehiculoId/estado_actual.pdf', generateReport);
router.get('/vehiculo/:vehiculoId/rendimiento.pdf', generateReport);

// POST /semaforo/report.pdf
// Accepts a JSON payload from the frontend containing the report data for a Prueba Semáforo
// Expected payload shape (example):
// {
//   prueba: { idPruebasSemaforo, PruebasSemaforoTitulo, ... },
//   flota: { idFlotas, ... },
//   vehiculos: [ { idVehiculoSemaforo, VehiculoSemaforoNo, TipoVehiculos_idTipoVehiculos, ... }, ... ],
//   llantasPorVehiculo: { [vehId]: [ { Llantas_idLlantas, LlantasSemaforoPresion, LlantasSemaforoVigia, LlantasSemaforoPiso, LlantasSemaforoCondPel, LlantasSemaforoObserv, LlantasSemaforoColor }, ... ] },
//   llantaCatalog: [ { idLlantas, LlantasMarca }, ... ],
//   parametros: [ { Llantas_idLlantas, ParametrosPMin, ParametrosPSug }, ... ]
// }
async function generateSemaforoReportFromPayload(req, res) {
    try {
        const payload = req.body || {};
        console.log('[reports] Received semaforo payload keys:', Object.keys(payload));
        // Helpful when testing from emulator: if no payload provided, create a small sample
        if (!payload || Object.keys(payload).length === 0) {
            console.log('[reports] No payload received, generating sample payload for preview');
            // minimal sample
            payload.prueba = { idPruebasSemaforo: 'sample', PruebasSemaforoTitulo: 'Ejemplo Semáforo' };
            payload.flota = { idFlotas: 1, FlotasNombre: 'Flota Demo' };
            payload.vehiculos = [ { idVehiculoSemaforo: 1, VehiculoSemaforoNo: 'ABC-123' } ];
            payload.llantasPorVehiculo = { '1': [
                { Llantas_idLlantas: 1, LlantasSemaforoPresion: 72, LlantasSemaforoVigia: 0, LlantasSemaforoPiso: 'Original', LlantasSemaforoCondPel: false, LlantasSemaforoObserv: '', LlantasSemaforoColor: 'Verde' },
                { Llantas_idLlantas: 2, LlantasSemaforoPresion: 60, LlantasSemaforoVigia: 0, LlantasSemaforoPiso: 'Vitalizado', LlantasSemaforoCondPel: true, LlantasSemaforoObserv: 'Corte', LlantasSemaforoColor: 'Rojo' },
                { Llantas_idLlantas: 3, LlantasSemaforoPresion: null, LlantasSemaforoVigia: 1, LlantasSemaforoPiso: 'Original', LlantasSemaforoCondPel: false, LlantasSemaforoObserv: '', LlantasSemaforoColor: 'Morado' }
            ] };
            payload.llantaCatalog = [ { idLlantas: 1, LlantasMarca: 'Yokohama' }, { idLlantas: 2, LlantasMarca: 'Bridgestone' }, { idLlantas: 3, LlantasMarca: 'Otras' } ];
            payload.parametros = [ { Llantas_idLlantas: 1, ParametrosPMin: 65, ParametrosPSug: 75 }, { Llantas_idLlantas: 2, ParametrosPMin: 58, ParametrosPSug: 68 }, { Llantas_idLlantas: 3, ParametrosPMin: 60, ParametrosPSug: 70 } ];
        }
        const prueba = payload.prueba || {};
        const flota = payload.flota || {};
        const vehiculos = payload.vehiculos || [];
        const llantasByVeh = payload.llantasPorVehiculo || {};
        const llantaCatalog = payload.llantaCatalog || [];
        const parametros = payload.parametros || [];

        // Flatten llantas
        const flattened = Object.values(llantasByVeh).flat();
        const totalLlantas = Math.max(flattened.length, 0);

        // Debug: log llantaCatalog and flattened sample to troubleshoot brand lookup
        try {
            console.log('[reports] inspeccion llantaCatalog length:', (llantaCatalog && llantaCatalog.length) || 0);
            console.log('[reports] inspeccion flattened length:', flattened.length);
            console.log('[reports] inspeccion flattened sample:', JSON.stringify(flattened.slice(0,10), null, 2));
        } catch (e) { console.log('[reports] error logging flattened sample', e && e.message); }

        // brand mapping: prefer catalog name, then check several possible llanta fields, fallback to 'Otras'
        const catalogMap = {};
        for (const it of llantaCatalog) {
            if (!it || it.idLlantas === undefined) continue;
            const cand = (it.LlantasMarca || it.Marca || it.Nombre || it.LlantasNombre || '').toString().trim();
            if (cand.length > 0) catalogMap[it.idLlantas] = cand;
        }

        const guessBrandFromLl = (ll) => {
            if (!ll) return 'Otras';
            const candidates = [ll.LlantasMarca, ll.Marca, ll.LlantasNombre, ll.Nombre, ll.Brand, ll.MarcaLlanta, ll.LlantasModelo];
            for (const c of candidates) {
                if (c !== undefined && c !== null) {
                    const s = String(c).trim();
                    if (s.length > 0) return s;
                }
            }
            return 'Otras';
        };

        const brandCounts = {};
        for (const ll of flattened) {
            let marca = 'Otras';
            try {
                if (ll && ll.Llantas_idLlantas !== undefined && catalogMap[ll.Llantas_idLlantas]) marca = catalogMap[ll.Llantas_idLlantas];
                else marca = guessBrandFromLl(ll);
            } catch (e) { marca = 'Otras'; }
            brandCounts[marca] = (brandCounts[marca] || 0) + 1;
        }

        // parametros map (by id and by medida) and catalog medida map
        const parametrosMap = {};
        const parametrosByMedida = {};
        for (const p of parametros) {
            if (p && p.Llantas_idLlantas !== undefined) parametrosMap[p.Llantas_idLlantas] = p;
            if (p && p.LlantasMedida !== undefined) parametrosByMedida[p.LlantasMedida] = p;
        }
        const catalogMeasureMap = {};
        for (const it of llantaCatalog) { if (it && it.idLlantas !== undefined) catalogMeasureMap[it.idLlantas] = it.LlantasMedida; }

        // pressure buckets (exclude vigía)
        let presionRed = 0, presionGreen = 0, presionYellow = 0, presionNoData = 0, vigiaCount = 0;
        for (const ll of flattened) {
            if (ll.LlantasSemaforoVigia == 1 || ll.LlantasSemaforoVigia === '1') { vigiaCount++; continue; }
            const p = (ll.LlantasSemaforoPresion == null) ? null : Number(ll.LlantasSemaforoPresion);
            // resolve param by llanta id then by medida (catalog)
            let param = parametrosMap[ll.Llantas_idLlantas];
            if (!param) {
                const medida = catalogMeasureMap[ll.Llantas_idLlantas];
                if (medida) param = parametrosByMedida[medida];
            }
            if (p == null) { presionNoData++; continue; }
            // If there's no parametros entry but we have a pressure value, treat as OK (same as client)
            if (!param) { presionGreen++; continue; }
            if (p <= Number(param.ParametrosPMin)) presionRed++;
            else if (p <= Number(param.ParametrosPSug)) presionGreen++;
            else presionYellow++;
        }

        // tipo de piso counts (tolerant: accept multiple field names/formats)
        const getPiso = (ll) => {
            if (!ll) return null;
            const candidates = [
                ll.LlantasSemaforoPiso,
                ll.LlantasInspeccionPiso,
                ll.LlantasPiso,
                ll.Piso,
                ll.piso,
                ll.piso_tipo,
                (ll.Llantas && ll.Llantas.LlantasInspeccionPiso),
            ];
            for (const c of candidates) {
                if (c === undefined || c === null) continue;
                const s = String(c).trim();
                if (s.length === 0) continue;
                return s;
            }
            return null;
        };
        try { console.log('[reports] flattened[0] keys sample:', Object.keys(flattened[0] || {})); } catch(e){}
        try { console.log('[reports] flattened sample (semaforo):', JSON.stringify((flattened||[]).slice(0,6), null, 2).slice(0,2000)); } catch(e){}
        // inspect candidate fields per item for piso
        try {
            (flattened||[]).slice(0,10).forEach((ll, idx) => {
                try {
                    console.log('[reports] semaforo flattened item', idx, 'keys=', Object.keys(ll || {}), 'LlantasSemaforoPiso=', ll && ll.LlantasSemaforoPiso, 'LlantasInspeccionPiso=', ll && ll.LlantasInspeccionPiso, 'LlantasPiso=', ll && ll.LlantasPiso, 'Piso=', ll && ll.Piso, 'piso=', ll && ll.piso);
                } catch(e){}
            });
        } catch(e){}
        const pisoValues = (flattened || []).map(getPiso);
        const originalCount = pisoValues.filter(v => v === 'Original').length;
        const vitalizadoCount = pisoValues.filter(v => v === 'Vitalizado').length;
        const otherPisoCount = Math.max((flattened || []).length - originalCount - vitalizadoCount, 0);
        try { console.log('[reports] semaforo Tipo de piso counts:', { originalCount, vitalizadoCount, otherPisoCount, total: flattened.length }); } catch(e){}
        try { console.log('[reports] semaforo piso sample:', (flattened||[]).slice(0,10).map(x => x && x.LlantasSemaforoPiso)); } catch(e){}

        // condicion peligrosa
        const condPelTrue = flattened.filter(it => it.LlantasSemaforoCondPel === true || it.LlantasSemaforoCondPel === 1 || it.LlantasSemaforoCondPel === '1').length;
        const condPelFalse = Math.max(flattened.length - condPelTrue, 0);

        // observaciones grouping
        const obsRaw = flattened.map(it => (it.LlantasSemaforoObserv && String(it.LlantasSemaforoObserv).trim().length > 0) ? String(it.LlantasSemaforoObserv).trim() : 'LLANTA OK');
        const obsCounts = {};
        for (const o of obsRaw) obsCounts[o] = (obsCounts[o] || 0) + 1;

        // Start PDF
        const doc = new PDFDocument({ margin: 40, size: 'A4' });
        res.setHeader('Content-Type', 'application/pdf');
        const filename = `prueba_semaforo_${prueba.idPruebasSemaforo || 'report'}.pdf`;
        res.setHeader('Content-Disposition', `inline; filename=${filename}`);
        doc.pipe(res);

        // Basic layout constants (local to this handler)
        const COL_START = 40;
        const TABLE_WIDTH = 515;
        const CELL_HEIGHT = 20;
        const BORDER_COLOR = '#AAAAAA';
        const HEADER_BG = '#B3B3B3';

        // Helpers to avoid passing NaN/invalid coordinates into PDFKit
        const safeText = (d, text, x, y, options) => {
            try {
                const fallbackX = (typeof x === 'number' && Number.isFinite(x)) ? x : (COL_START || 40);
                const fallbackY = (typeof y === 'number' && Number.isFinite(y)) ? y : (Number.isFinite(d.y) ? d.y : (d.page && d.page.margins && d.page.margins.top ? d.page.margins.top : 40));
                // Try with explicit numeric coordinates first (coerced)
                if (Number.isFinite(fallbackX) && Number.isFinite(fallbackY)) {
                    try { d.text(text, fallbackX, fallbackY, options || {}); return; } catch (e) { /* try fallback below */ }
                }
                // As a last resort, try adding a new page then writing without coordinates
                try { d.addPage(); d.text(text, options || {}); return; } catch (e) { /* final fallback */ }
                console.error('safeText failed for text (all retries):', text);
            } catch (e) {
                console.error('safeText unexpected error:', e);
            }
        };

        const safeRect = (d, x, y, w, h, options) => {
            try {
                if ([x,y,w,h].every(v => typeof v === 'number' && Number.isFinite(v))) {
                    try { d.rect(x,y,w,h).fill(options && options.fill ? options.fill : undefined).stroke(); return; } catch(e) { /* ignore */ }
                }
            } catch (e) { /* ignore */ }
        };

        // Calculate available width for flow text (page width minus margins)
        const availWidth = (doc.page && doc.page.width ? doc.page.width : 595) - (doc.page && doc.page.margins ? (doc.page.margins.left + doc.page.margins.right) : 80) - 40;

        // Ensure PDF generation stops if the client disconnects to avoid write-after-end
        res.on('close', () => {
            try { if (doc && typeof doc.destroy === 'function') doc.destroy(); } catch (e) { /* ignore */ }
        });

        // Header with logo if available
        try {
            const logoPath = resolveImagePath('yokohamalogo.png') || ensureLogoPlaceholder();
            if (logoPath && fs.existsSync(logoPath)) {
                const imgWidth = 220;
                const x = (doc.page.width - imgWidth) / 2;
                doc.image(logoPath, x, doc.y, { width: imgWidth });
                doc.moveDown(0.8);
            }
        } catch (e) { /* ignore logo errors */ }
        doc.fontSize(18).font('Helvetica-Bold').fillColor('#000000')
            .text('Reporte - Semáforo', { align: 'center' });
        if (prueba && prueba.PruebasSemaforoTitulo) doc.moveDown(0.2).fontSize(14).text(String(prueba.PruebasSemaforoTitulo), { align: 'center' });
        doc.moveDown(0.8);

        // --- Brands visualization (legend + horizontal bars to the right) ---
        doc.fontSize(12).font('Helvetica-Bold').text('Marcas', { underline: true });
        const brandEntries = Object.entries(brandCounts).sort((a,b) => b[1]-a[1]);
        const swatchSize = 10;
        // palette rotate
        const palette = ['#4e79a7','#f28e2b','#e15759','#76b7b2','#59a14f','#edc948','#b07aa1','#ff9da7','#9c755f','#bab0ac'];

        // Layout: left column for legend, right column for horizontal bars
        const leftColX = COL_START;
        const leftColWidth = Math.min(260, Math.round(availWidth * 0.55));
        const rightColX = leftColX + leftColWidth + 12;
        const chartWidth = Math.max(80, Math.min(200, Math.round(availWidth - leftColWidth - 40)));

        // compute max for scaling
        const counts = brandEntries.map(e => e[1] || 0);
        const maxCount = counts.length > 0 ? Math.max(...counts) : 1;

        // Reserve a fixed block height for legend + pie to avoid overlap
        const rowHeight = 14;
        const legendHeight = rowHeight * Math.max(brandEntries.length, 1);
        const pieMaxRadius = Math.min(Math.floor(chartWidth / 2), 90);
        const pieRadius = Math.min(pieMaxRadius, Math.floor(Math.max(40, legendHeight / 2)));
        const pieDiameter = pieRadius * 2;
        const reservedHeight = Math.max(legendHeight, pieDiameter) + 12; // padding

        // If not enough space, start a new page
        if ((Number(doc.y) || 0) + reservedHeight > doc.page.height - 100) {
            doc.addPage();
        }

        const blockTop = Number(doc.y) || 0;
        const startY = blockTop + 6;

        // draw legend rows (left column)
        let ly = startY;
        for (let i = 0; i < brandEntries.length; i++) {
            const marca = brandEntries[i][0];
            const cnt = brandEntries[i][1] || 0;
            const pct = totalLlantas > 0 ? Math.round((cnt * 100) / totalLlantas) : 0;
            const color = palette[i % palette.length];
            try { doc.save(); doc.rect(leftColX, ly - 2, swatchSize, swatchSize).fill(color).stroke(); doc.restore(); } catch (e) {}
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  ${marca}: ${cnt} (${pct}%)`, leftColX + swatchSize + 6, ly - 2, { width: leftColWidth - (swatchSize + 10) });
            ly += rowHeight;
        }

        // draw pie centered in right column within reserved block
        const pieCx = rightColX + Math.floor(chartWidth / 2);
        const pieCy = blockTop + Math.floor(reservedHeight / 2);
        const totalForPie = brandEntries.reduce((s, e) => s + (e[1] || 0), 0) || 1;
        let angle = -90;
        for (let i = 0; i < brandEntries.length; i++) {
            const cnt = brandEntries[i][1] || 0;
            const sweep = (cnt / totalForPie) * 360;
            const color = palette[i % palette.length];
            drawPieSlice(doc, pieCx, pieCy, pieRadius, angle, angle + sweep, { fill: color });
            angle += sweep;
        }
        // omit numeric label in the center of the pie (visual-only pie)

        // Move cursor to the end of reserved block to avoid overlap
        const desiredY = blockTop + reservedHeight + 12;
        try {
            // Use absolute text positioning to reliably move the internal cursor
            doc.text('', COL_START, desiredY);
        } catch (e) {
            // fallback: set doc.y if writable
            try { doc.y = desiredY; } catch (e2) { /* ignore */ }
        }

        // --- Pressure segmented bar ---
        // Draw segments in the same order and proportions as the app: OK, Bajo, Alto, Sin dato, Vigía
        doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Presión de inflado', { underline: true });
        const barX = Number(doc.x) || 0;
        let barY = (Number(doc.y) || 0) + 6;
        // make the pressure bar a bit smaller for tighter layout
        const barWidth = 360;
        const barHeight = 10;

        // segments as counts (order matches UI)
        const segments = [
            { count: presionGreen, color: '#59a14f', label: `OK ${presionGreen}` },
            { count: presionRed, color: '#e15759', label: `Bajo ${presionRed}` },
            { count: presionYellow, color: '#f28e2b', label: `Alto ${presionYellow}` },
            { count: presionNoData, color: '#cccccc', label: `Sin dato ${presionNoData}` },
            { count: vigiaCount, color: '#8e44ad', label: `Vigía ${vigiaCount}` }
        ];

        // compute sum of segments (weights) to match client Box weights
        const sumSegments = Math.max(segments.reduce((s, it) => s + (it.count || 0), 0), 1);

        // draw main segmented bar using counts/sumSegments for widths
        let accX = barX;
        for (const seg of segments) {
            if (!seg.count || seg.count <= 0) continue;
            const w = Math.max(1, Math.round(barWidth * (seg.count / sumSegments)));
            try { doc.rect(accX, barY, w, barHeight).fill(seg.color).stroke(); } catch (e) {}
            // percentage label under each segment using integer division (like Kotlin Int/Int)
            try {
                const pct = Math.floor((seg.count * 100) / sumSegments);
                const cx = accX + Math.floor(w / 2);
                doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pct}%`, cx - 12, barY + barHeight + 4, { width: 24, align: 'center' });
            } catch (e) { /* ignore label errors */ }
            accX += w;
        }
        // Draw stacked legend to the right of the segmented bar (swatches + counts/percent)
        try {
            const sw = 10;
            const legendX = barX + barWidth + 12;
            let ly = barY - 2;
            for (const seg of segments) {
                if (!seg.count || seg.count <= 0) continue;
                const pct = Math.floor((seg.count * 100) / sumSegments);
                doc.save(); doc.rect(legendX, ly, sw, sw).fill(seg.color).stroke(); doc.restore();
                doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`${seg.label} (${pct}%)`, legendX + sw + 6, ly, { width: 160 });
                ly += sw + 6;
            }
        } catch (e) {
            // fallback: simple inline vigía text if legend fails
            try { const pctVigia = Math.floor((vigiaCount * 100) / sumSegments); doc.font('Helvetica').fontSize(9).fillColor('#000000').text(` Vigía: ${vigiaCount} (${pctVigia}%)`, { continued: false, width: 120 }); } catch(e){}
        }
        // add a bit more vertical space to account for the percent labels
        doc.moveDown(2.2);

        // --- Tipo de piso ---
        // Ensure the header aligns with left column
        try { safeText(doc, 'Tipo de piso', COL_START, doc.y, { underline: true }); } catch (e) { doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Tipo de piso', { underline: true }); }
        const pisoTotal = Math.max(originalCount + vitalizadoCount, 1);
        const pctOriginal = Math.round((originalCount * 100) / pisoTotal);
        const pctVital = Math.round((vitalizadoCount * 100) / pisoTotal);
        // draw small bars
        const pisoX = Number(doc.x) || 0;
        const pisoY = (Number(doc.y) || 0) + 6;
        const pisoW = 220;
        const wOrig = Math.max(1, Math.round(pisoW * (pctOriginal / 100)));
        const wVit = Math.max(1, Math.round(pisoW * (pctVital / 100)));
        doc.rect(pisoX, pisoY, wOrig, 12).fill('#4e79a7').stroke();
        doc.rect(pisoX + wOrig, pisoY, wVit, 12).fill('#76b7b2').stroke();
        // percentage labels below each small piso bar
        try {
            const cxOrig = pisoX + Math.floor(wOrig/2);
            doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pctOriginal}%`, cxOrig - 12, pisoY + 12 + 4, { width: 24, align: 'center' });
            const cxVit = pisoX + wOrig + Math.floor(wVit/2);
            doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pctVital}%`, cxVit - 12, pisoY + 12 + 4, { width: 24, align: 'center' });
        } catch (e) { /* ignore */ }
        const pisoTx = pisoX + pisoW + 8;
        // Draw legend with swatches for Piso (avoid duplicating percentage text)
        try {
            const sw = 10;
            const legendX = COL_START + TABLE_WIDTH - 180;
            let ly = pisoY - 2;
            // Original (top)
            doc.save(); doc.rect(legendX, ly, sw, sw).fill('#4e79a7').stroke(); doc.restore();
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`Original: ${originalCount} (${pctOriginal}%)`, legendX + sw + 6, ly, { width: 160 });
            // Vitalizado (below)
            ly += sw + 6;
            doc.save(); doc.rect(legendX, ly, sw, sw).fill('#76b7b2').stroke(); doc.restore();
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`Vitalizado: ${vitalizadoCount} (${pctVital}%)`, legendX + sw + 6, ly, { width: 160 });
        } catch (e) {
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  Original: ${originalCount} (${pctOriginal}%)  Vitalizado: ${vitalizadoCount} (${pctVital}%)`, pisoTx, pisoY - 1, { width: Math.max(120, availWidth - pisoTx) });
        }
        // leave extra space for the percent labels
        doc.moveDown(1.8);

        // --- Condición peligrosa ---
        // Force title to start at left column to keep alignment with other sections
        doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000');
        safeText(doc, 'Condición peligrosa', COL_START, doc.y, { underline: true });
        const pctCPTrue = Math.round((condPelTrue * 100) / Math.max(condPelTrue + condPelFalse, 1));
        const cpW = 220;
        const cpTrueW = Math.max(1, Math.round(cpW * (pctCPTrue / 100)));
        // draw CP bars at current x/y using safeRect
        const cpBarY = doc.y + 6;
        safeRect(doc, COL_START, cpBarY, cpTrueW, 12, { fill: '#e15759' });
        safeRect(doc, COL_START + cpTrueW, cpBarY, cpW - cpTrueW, 12, { fill: '#59a14f' });
        // percentage labels centered under each CP segment
        try {
            const c1 = COL_START + Math.floor(cpTrueW / 2);
            const c2 = COL_START + cpTrueW + Math.floor((cpW - cpTrueW) / 2);
            doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pctCPTrue}%`, c1 - 12, cpBarY + 12 + 4, { width: 24, align: 'center' });
            const pctCPFalse = 100 - pctCPTrue;
            doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pctCPFalse}%`, c2 - 12, cpBarY + 12 + 4, { width: 24, align: 'center' });
        } catch (e) { }
        // Draw legend with swatches for Condición peligrosa (avoid duplicate percent text)
        try {
            const sw = 10;
            const legendX = COL_START + TABLE_WIDTH - 180;
            let ly = cpBarY - 2;
            // Peligrosa (top)
            doc.save(); doc.rect(legendX, ly, sw, sw).fill('#e15759').stroke(); doc.restore();
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`Peligrosa: ${condPelTrue} (${pctCPTrue}%)`, legendX + sw + 6, ly, { width: 160 });
            // Normal (below)
            ly += sw + 6;
            doc.save(); doc.rect(legendX, ly, sw, sw).fill('#59a14f').stroke(); doc.restore();
            const pctCPFalse = 100 - pctCPTrue;
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`Normal: ${condPelFalse} (${pctCPFalse}%)`, legendX + sw + 6, ly, { width: 160 });
        } catch (e) {
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  Peligrosa: ${condPelTrue} (${pctCPTrue}%)  Normal: ${condPelFalse}`, COL_START + cpW + 12, cpBarY + 2, { width: Math.max(120, availWidth - (COL_START + cpW + 12)) });
        }
        // leave extra vertical space for percent labels
        doc.moveDown(2);

        // Observaciones list with colored swatches + pie chart on the right
        doc.fontSize(12).font('Helvetica-Bold').text('Observaciones', COL_START, doc.y, { width: TABLE_WIDTH, underline: true, align: 'center' });
        // Prepare top observations and colors
        const obsEntries = Object.entries(obsCounts).sort((a,b) => b[1]-a[1]);
        const topN = 6;
        const topObs = obsEntries.slice(0, topN);
        const othersCount = obsEntries.slice(topN).reduce((s,e) => s + e[1], 0);
        const displayObs = topObs.map(([k,v]) => ({ label: k, count: v }));
        if (othersCount > 0) displayObs.push({ label: 'Otras', count: othersCount });

        if (displayObs.length > 0) {
            const paletteObs = ['#1976D2','#9C27B0','#7B1FA2','#59A14F','#F28E2B','#E15759','#BDBDBD'];
            const leftColX = COL_START;
            const leftColWidth = Math.min(300, Math.round(availWidth * 0.6));
            const rightColX = leftColX + leftColWidth + 12;
            const chartWidth = Math.max(80, Math.round(availWidth - leftColWidth - 40));

            const rowHeight = 14;
            const legendHeight = rowHeight * Math.max(displayObs.length, 1);
            // Make pie noticeably smaller, reserve minimal vertical space so it sits higher
            const baseRadius = Math.min(Math.floor(chartWidth/2), Math.max(16, Math.floor(legendHeight/2)));
            const pieRadius = Math.max(8, Math.floor(baseRadius * 0.48));
            const pieDiameter = pieRadius * 2;
            const reservedHeight = Math.max(legendHeight, pieDiameter) + 4;

            if ((Number(doc.y) || 0) + reservedHeight > doc.page.height - 100) doc.addPage();
            const blockTop = Number(doc.y) || 0;
            let ly = blockTop + 6;

            // draw legend (left) with colored swatches (one of the two dots colored)
            for (let i = 0; i < displayObs.length; i++) {
                const it = displayObs[i];
                const col = paletteObs[i % paletteObs.length];
                try { doc.save(); doc.rect(leftColX, ly - 2, 8, 8).fill(col).stroke(); doc.restore(); } catch(e) {}
                const pct = totalLlantas > 0 ? Math.round((it.count * 100) / totalLlantas) : 0;
                doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  ${it.label}: ${it.count} (${pct}%)`, leftColX + 12, ly - 2, { width: leftColWidth - 12 });
                ly += rowHeight;
            }

            // draw pie at right (shifted further right and higher)
            // place the pie near the right edge of the report block and lift it up
            const maxRight = COL_START + TABLE_WIDTH - 12; // 12px padding from right edge
            const pieCx = Math.min(rightColX + chartWidth, maxRight) - Math.ceil(pieRadius * 0.8);
            const pieCy = blockTop + Math.floor(reservedHeight/2) - 36;
            const totalForPie = displayObs.reduce((s, it) => s + it.count, 0) || 1;
            let angle = -90;
            for (let i = 0; i < displayObs.length; i++) {
                const cnt = displayObs[i].count || 0;
                const sweep = (cnt / totalForPie) * 360;
                const color = paletteObs[i % paletteObs.length];
                drawPieSlice(doc, pieCx, pieCy, pieRadius, angle, angle + sweep, { fill: color });
                angle += sweep;
            }

            // move cursor after reserved block
            const desiredY = blockTop + reservedHeight + 12;
            try { doc.text('', COL_START, desiredY); } catch(e) { try { doc.y = desiredY; } catch(e2) {} }
        } else {
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text('Sin observaciones', COL_START, doc.y);
            doc.moveDown(0.4);
        }

        // --- NUEVA HOJA: listado al detalle ---
        doc.addPage();
        // Vehicle details with color swatch and improved layout
        doc.fillColor('#000000').fontSize(14).font('Helvetica-Bold').text('Detalle de los vehículos', COL_START, doc.y, { width: TABLE_WIDTH, underline: true, align: 'center' });
        doc.moveDown(0.4);
        for (const veh of vehiculos) {
            doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text(`${veh.VehiculoSemaforoNo || 'Sin placa'} (${veh.idVehiculoSemaforo || ''})`, COL_START, doc.y);
            const llantas = llantasByVeh[veh.idVehiculoSemaforo] || [];
            if (llantas.length === 0) {
                doc.font('Helvetica').fontSize(10).text('  - Sin llantas registradas');
            } else {
                // Render llantas as a table: Marca | Presión | Piso | Cond | Obs
                let vehCols = [
                    { header: 'Foto', width: 70 },
                    { header: 'Marca', width: 70 },
                    { header: 'Presión', width: 50 },
                    { header: 'Piso', width: 60 },
                    { header: 'Cond', width: 55 },
                    { header: 'Color', width: 50 },
                    { header: 'Observaciones', width: 95 }
                ];

                // helper: map common color names (es/en) to hex
                const colorNameToHex = (name) => {
                    if (!name) return null;
                    const s = String(name).trim().toLowerCase();
                    const map = {
                        'rojo': '#e15759', 'red': '#e15759',
                        'amarillo': '#f4d03f', 'yellow': '#f4d03f',
                        'verde': '#59a14f', 'green': '#59a14f',
                        'morado': '#8e44ad', 'purple': '#8e44ad',
                        'azul': '#4e79a7', 'blue': '#4e79a7',
                        'gris': '#cccccc', 'gray': '#cccccc', 'grey': '#cccccc',
                        'negro': '#000000', 'black': '#000000',
                        'blanco': '#ffffff', 'white': '#ffffff'
                    };
                    if (map[s]) return map[s];
                    // if already hex-like
                    if (/^#?[0-9a-f]{3,6}$/i.test(s)) return s.startsWith('#') ? s : `#${s}`;
                    return null;
                };

                const hexToRgb = (hex) => {
                    try {
                        const h = hex.replace('#','');
                        const bigint = parseInt(h.length===3 ? h.split('').map(c=>c+c).join('') : h, 16);
                        return { r: (bigint >> 16) & 255, g: (bigint >> 8) & 255, b: bigint & 255 };
                    } catch(e) { return null; }
                };

                const isLightHex = (hex) => {
                    const rgb = hexToRgb(hex);
                    if (!rgb) return false;
                    // Perceived luminance
                    const lum = (0.299*rgb.r + 0.587*rgb.g + 0.114*rgb.b) / 255;
                    return lum > 0.7;
                };

                const drawVehHeader = (y) => {
                    try { doc.rect(COL_START, y, TABLE_WIDTH, CELL_HEIGHT).fill(HEADER_BG); } catch(e) {}
                    doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(9);
                    let x = COL_START;
                    vehCols.forEach(col => { doc.text(col.header, x + 4, y + 6, { width: col.width - 5 }); x += col.width; });
                    doc.fillColor('#000000').font('Helvetica');
                    try { doc.strokeColor(BORDER_COLOR).moveTo(COL_START + TABLE_WIDTH, y).lineTo(COL_START + TABLE_WIDTH, y + CELL_HEIGHT).stroke(); } catch(e) {}
                    return y + CELL_HEIGHT;
                };

                const drawVehRow = async (y, data) => {
                    // ensure font is set before measuring so heightOfString is accurate
                    try { doc.font('Helvetica').fontSize(9); } catch (e) {}
                    // calculate required row height based on cell contents (allow multiline for Observaciones)
                    const heights = [];
                    for (let i = 0; i < data.length; i++) {
                        const cell = data[i];
                        const colW = vehCols[i].width - 8;
                        let txt = '';
                        if (cell && typeof cell === 'object' && cell.__image) txt = '';
                        else if (cell && typeof cell === 'object' && cell.color) txt = String(cell.text || '');
                        else txt = (cell === undefined || cell === null) ? '—' : String(cell);
                        try {
                            const h = doc.heightOfString(txt, { width: colW });
                            const minH = (cell && typeof cell === 'object' && cell.__image) ? 72 : CELL_HEIGHT;
                            heights.push(Math.max(minH, Math.ceil(h) + 6));
                        } catch (e) { heights.push(CELL_HEIGHT); }
                    }
                    const rowH = Math.max(...heights, CELL_HEIGHT);
                    // draw each cell with the computed height
                    doc.font('Helvetica').fontSize(9).fillColor('#000000');
                    let x = COL_START;
                    for (let i = 0; i < data.length; i++) {
                        const cell = data[i];
                        const colW = vehCols[i].width;
                        if (cell && typeof cell === 'object' && cell.__image) {
                            const raw = cell.value || null;
                            const hasRaw = !!(raw && String(raw).trim().length > 0);
                            if (!hasRaw) {
                                try { doc.text('Sin foto', x + 4, y + 4, { width: colW - 8 }); } catch(_) {}
                            } else {
                            let painted = false;
                            try {
                                painted = await renderImageInBoxAsync(doc, raw, x + 4, y + 4, colW - 8, rowH - 8, '[reports][semaforo][table]');
                                console.log('[reports][semaforo][table] foto len=', raw ? String(raw).length : 0, 'painted=', painted);
                            } catch (_) { painted = false; }
                            if (!painted) {
                                try { doc.text('IMG ERR', x + 4, y + 4, { width: colW - 8 }); } catch(_) {}
                            }
                            }
                        } else if (cell && typeof cell === 'object' && cell.color) {
                            const hex = colorNameToHex(cell.color) || (typeof cell.color === 'string' ? cell.color : null) || '#FFFFFF';
                            try { doc.rect(x, y, colW, rowH).fill(hex).stroke(); } catch(e) {}
                            const textColor = isLightHex(hex) ? '#000000' : '#FFFFFF';
                            doc.fillColor(textColor).font('Helvetica-Bold').fontSize(9);
                            const label = cell.text || '';
                            try { doc.text(label, x + 4, y + 4, { width: colW - 8 }); } catch(e) {}
                            doc.fillColor('#000000').font('Helvetica').fontSize(9);
                        } else {
                            const cellText = (cell === undefined || cell === null) ? '—' : String(cell);
                            try { doc.text(cellText, x + 4, y + 4, { width: colW - 8 }); } catch(e) {}
                        }
                        try { doc.strokeColor(BORDER_COLOR).moveTo(x, y).lineTo(x, y + rowH).stroke(); } catch(e) {}
                        x += colW;
                    }
                    try { doc.moveTo(COL_START, y + rowH).lineTo(COL_START + TABLE_WIDTH, y + rowH).strokeColor(BORDER_COLOR).stroke(); } catch(e) {}
                    try { doc.strokeColor(BORDER_COLOR).moveTo(COL_START + TABLE_WIDTH, y).lineTo(COL_START + TABLE_WIDTH, y + rowH).stroke(); } catch(e) {}
                    return y + rowH;
                };

                let vy = doc.y + 6;
                vy = drawVehHeader(vy);
                for (const ll of llantas) {
                    const marca = catalogMap[ll.Llantas_idLlantas] || 'Otras';
                    const pres = (ll.LlantasSemaforoVigia == 1 || ll.LlantasSemaforoVigia === '1') ? 'Vigía' : (ll.LlantasSemaforoPresion != null ? String(ll.LlantasSemaforoPresion) : 'Sin dato');
                    const piso = ll.LlantasSemaforoPiso || 'Original';
                    const cond = (ll.LlantasSemaforoCondPel === true || ll.LlantasSemaforoCondPel === 1 || ll.LlantasSemaforoCondPel === '1') ? 'Peligrosa' : 'Normal';
                    const obs = ll.LlantasSemaforoObserv || '';
                    // pass color info for LlantasSemaforoColor so the renderer can fill the new Color column
                    const colorCell = { text: ll.LlantasSemaforoColor || '', color: ll.LlantasSemaforoColor || null };
                    const fotoCell = { __image: true, value: ll.LlantasSemaforoFoto1 || ll.LlantasSemaforoFoto2 || ll.LlantasSemaforoFoto || null };
                    vy = await drawVehRow(vy, [fotoCell, marca, pres, piso, cond, colorCell, obs]);
                    if (vy > doc.page.height - 80) { doc.addPage(); vy = drawVehHeader(doc.y + 6); }
                }

                // advance main cursor to after table
                try { doc.y = vy + 2; } catch(e) { doc.moveDown(0.3); }
            }
            doc.moveDown(0.4);
            if (doc.y > doc.page.height - 80) doc.addPage();
        }

        doc.end();
    } catch (err) {
        console.error('Error generando PDF semaforo desde payload:', err);
        try { if (typeof doc !== 'undefined' && doc && typeof doc.destroy === 'function') doc.destroy(); } catch (e) { /* ignore */ }
        handleServerError(res, 'Error generando reporte semáforo', err);
    }
}

router.post('/semaforo/report.pdf', generateSemaforoReportFromPayload);
// POST /inspeccion/report.pdf
// Accepts a JSON payload from the frontend containing the report data for a Prueba Inspeccion
async function generateInspeccionReportFromPayload(req, res) {
    try {
        const payload = req.body || {};
        console.log('[reports] Received inspeccion payload keys:', Object.keys(payload));
        if (!payload || Object.keys(payload).length === 0) {
            console.log('[reports] No payload received, generating sample payload for inspeccion preview');
            payload.prueba = { idPruebaInspeccion: 'sample', PruebaInspeccionTitulo: 'Ejemplo Inspección' };
            payload.flota = { idFlotas: 1, FlotasNombre: 'Flota Demo' };
            payload.vehiculos = [ { idVehiculoInspeccion: 1, VehiculoInspeccionNo: 'ABC-123' } ];
            payload.llantasPorVehiculo = { '1': [
                { Llantas_idLlantas: 1, LlantasInspeccionPresion: 72, LlantasInspeccionVigia: 0, LlantasInspeccionPiso: 'Original', LlantasInspeccionCondPel: 0, LlantasInspeccionObservacion: '', LlantasInspeccionFoto: null },
                { Llantas_idLlantas: 2, LlantasInspeccionPresion: 60, LlantasInspeccionVigia: 0, LlantasInspeccionPiso: 'Vitalizado', LlantasInspeccionCondPel: 1, LlantasInspeccionObservacion: 'Corte', LlantasInspeccionFoto: null },
                { Llantas_idLlantas: 3, LlantasInspeccionPresion: null, LlantasInspeccionVigia: 1, LlantasInspeccionPiso: 'Original', LlantasInspeccionCondPel: 0, LlantasInspeccionObservacion: '', LlantasInspeccionFoto: null }
            ] };
            payload.llantaCatalog = [ { idLlantas: 1, LlantasMarca: 'Yokohama' }, { idLlantas: 2, LlantasMarca: 'Bridgestone' }, { idLlantas: 3, LlantasMarca: 'Otras' } ];
            payload.parametros = [ { Llantas_idLlantas: 1, ParametrosPMin: 65, ParametrosPSug: 75 }, { Llantas_idLlantas: 2, ParametrosPMin: 58, ParametrosPSug: 68 }, { Llantas_idLlantas: 3, ParametrosPMin: 60, ParametrosPSug: 70 } ];
        }

        const prueba = payload.prueba || {};
        const flota = payload.flota || {};
        const vehiculos = payload.vehiculos || [];
        const llantasByVeh = payload.llantasPorVehiculo || {};
        const llantaCatalog = payload.llantaCatalog || [];
        const parametros = payload.parametros || [];

        const flattened = Object.values(llantasByVeh).flat();
        const totalLlantas = Math.max(flattened.length, 0);

        // brand mapping: match UI - map catalog by id -> LlantasMarca, fallback to 'Otras'
        // Normalize keys to strings because payload may send ids as strings
        const catalogMap = {};
        for (const it of llantaCatalog) {
            if (!it || it.idLlantas === undefined) continue;
            catalogMap[String(it.idLlantas)] = (it.LlantasMarca || 'Otras');
        }
        try { console.log('[reports] catalogMap keys:', Object.keys(catalogMap).slice(0,40)); } catch(e) { /* ignore */ }

        // If payload didn't include a catalog, try loading from DB (using flota if available)
        if (Object.keys(catalogMap).length === 0) {
            try {
                console.log('[reports] llantaCatalog empty — attempting DB fallback');
                let rows;
                if (flota && flota.idFlotas) {
                    // load all llantas and mark those associated would be filtered client-side; we only need id/Marca/Medida
                    const q = `SELECT idLlantas, LlantasMarca, LlantasMedida FROM llantas ORDER BY LlantasMarca, LlantasModelo`;
                    const resRows = await db.execute(q);
                    rows = Array.isArray(resRows) && resRows[0] ? resRows[0] : resRows;
                } else {
                    const resRows = await db.execute(`SELECT idLlantas, LlantasMarca, LlantasMedida FROM llantas ORDER BY LlantasMarca, LlantasModelo`);
                    rows = Array.isArray(resRows) && resRows[0] ? resRows[0] : resRows;
                }
                if (rows && rows.length) {
                    console.log('[reports] DB fetched llantas rows:', rows.length);
                    for (const r of rows) {
                        if (!r || r.idLlantas === undefined) continue;
                        catalogMap[String(r.idLlantas)] = (r.LlantasMarca || 'Otras');
                    }
                    try { console.log('[reports] catalogMap keys after DB:', Object.keys(catalogMap).slice(0,40)); } catch(e) {}
                } else {
                    console.log('[reports] DB fallback returned no llantas');
                }
            } catch (dbErr) {
                console.error('[reports] Error fetching llanta catalog from DB fallback:', dbErr && dbErr.message);
            }
        }
        const brandCounts = {};
        for (const ll of flattened) {
            const key = ll && ll.Llantas_idLlantas !== undefined ? String(ll.Llantas_idLlantas) : null;
            const has = key ? (Object.prototype.hasOwnProperty.call(catalogMap, key) ? true : false) : false;
            try { console.log('[reports] brand lookup: raw=', ll && ll.Llantas_idLlantas, 'key=', key, 'hasCatalog=', has, 'catalogVal=', catalogMap[key]); } catch(e) {}
            const marca = (key && catalogMap[key]) ? catalogMap[key] : 'Otras';
            brandCounts[marca] = (brandCounts[marca] || 0) + 1;
        }

        // parametros map (by id and by medida) and catalog medida map
        const parametrosMap = {};
        const parametrosByMedida = {};
        for (const p of parametros) {
            if (p && p.Llantas_idLlantas !== undefined) parametrosMap[String(p.Llantas_idLlantas)] = p;
            if (p && p.LlantasMedida !== undefined) parametrosByMedida[p.LlantasMedida] = p;
        }
        const catalogMeasureMap = {};
        for (const it of llantaCatalog) { if (it && it.idLlantas !== undefined) catalogMeasureMap[String(it.idLlantas)] = it.LlantasMedida; }

        // pressure buckets (exclude vigía)
        let presionRed = 0, presionGreen = 0, presionYellow = 0, presionNoData = 0, vigiaCount = 0;
        for (const ll of flattened) {
            if (ll.LlantasInspeccionVigia == 1 || ll.LlantasInspeccionVigia === '1') { vigiaCount++; continue; }
            const p = (ll.LlantasInspeccionPresion == null) ? null : Number(ll.LlantasInspeccionPresion);
            // resolve param by llanta id then by medida (catalog)
            const key = ll.Llantas_idLlantas !== undefined ? String(ll.Llantas_idLlantas) : null;
            let param = key ? parametrosMap[key] : null;
            if (!param) {
                const medida = key ? catalogMeasureMap[key] : null;
                if (medida) param = parametrosByMedida[medida];
            }
            if (p == null) { presionNoData++; continue; }
            // If there's no parametros entry but we have a pressure value, treat as OK (same as client)
            if (!param) { presionGreen++; continue; }
            if (p <= Number(param.ParametrosPMin)) presionRed++;
            else if (p <= Number(param.ParametrosPSug)) presionGreen++;
            else presionYellow++;
        }

        // tipo de piso counts (tolerant getter)
        const getPiso2 = (ll) => {
            if (!ll) return null;
            const candidates = [
                ll.LlantasInspeccionPiso,
                ll.LlantasSemaforoPiso,
                ll.LlantasPiso,
                ll.Piso,
                ll.piso
            ];
            for (const c of candidates) {
                if (c === undefined || c === null) continue;
                const s = String(c).trim();
                if (s.length === 0) continue;
                return s;
            }
            return null;
        };
        try { console.log('[reports] flattened[0] keys sample (inspeccion):', Object.keys(flattened[0] || {})); } catch(e){}
        try { console.log('[reports] flattened sample (inspeccion):', JSON.stringify((flattened||[]).slice(0,6), null, 2).slice(0,2000)); } catch(e){}
        try {
            (flattened||[]).slice(0,10).forEach((ll, idx) => {
                try {
                    console.log('[reports] inspeccion flattened item', idx, 'keys=', Object.keys(ll || {}), 'LlantasInspeccionPiso=', ll && ll.LlantasInspeccionPiso, 'LlantasSemaforoPiso=', ll && ll.LlantasSemaforoPiso, 'Piso=', ll && ll.Piso, 'LlantasPiso=', ll && ll.LlantasPiso);
                } catch(e){}
            });
        } catch(e){}
        const pisoVals = (flattened || []).map(getPiso2);
        const originalCount = pisoVals.filter(v => v === 'Original').length;
        const vitalizadoCount = pisoVals.filter(v => v === 'Vitalizado').length;
        const otherPisoCount = Math.max((flattened || []).length - originalCount - vitalizadoCount, 0);
        try { console.log('[reports] inspeccion Tipo de piso counts:', { originalCount, vitalizadoCount, otherPisoCount, total: flattened.length }); } catch(e){}
        try { console.log('[reports] inspeccion piso sample:', (flattened||[]).slice(0,10).map(x => x && x.LlantasInspeccionPiso)); } catch(e){}

        // condicion peligrosa
        const condPelTrue = flattened.filter(it => it.LlantasInspeccionCondPel === 1 || it.LlantasInspeccionCondPel === '1').length;
        const condPelFalse = Math.max(flattened.length - condPelTrue, 0);

        // observaciones grouping
        const obsRaw = flattened.map(it => (it.LlantasInspeccionObservacion && String(it.LlantasInspeccionObservacion).trim().length > 0) ? String(it.LlantasInspeccionObservacion).trim() : 'LLANTA OK');
        const obsCounts = {};
        for (const o of obsRaw) obsCounts[o] = (obsCounts[o] || 0) + 1;

        // Start PDF (reuse layout used for semaforo)
        const doc = new PDFDocument({ margin: 40, size: 'A4' });
        res.setHeader('Content-Type', 'application/pdf');
        const filename = `prueba_inspeccion_${prueba.idPruebaInspeccion || 'report'}.pdf`;
        res.setHeader('Content-Disposition', `inline; filename=${filename}`);
        doc.pipe(res);

        // Basic layout constants
        const COL_START = 40;
        const TABLE_WIDTH = 515;
        const CELL_HEIGHT = 20;
        const BORDER_COLOR = '#AAAAAA';
        const HEADER_BG = '#B3B3B3';

        const availWidth = (doc.page && doc.page.width ? doc.page.width : 595) - (doc.page && doc.page.margins ? (doc.page.margins.left + doc.page.margins.right) : 80) - 40;

        // Header with logo
        try {
            const logoPath = resolveImagePath('yokohamalogo.png') || ensureLogoPlaceholder();
            if (logoPath && fs.existsSync(logoPath)) {
                const imgWidth = 220;
                const x = (doc.page.width - imgWidth) / 2;
                doc.image(logoPath, x, doc.y, { width: imgWidth });
                doc.moveDown(0.8);
            }
        } catch (e) { /* ignore */ }
        doc.fontSize(18).font('Helvetica-Bold').fillColor('#000000').text('Reporte - Inspección', { align: 'center' });
        if (prueba && prueba.PruebaInspeccionTitulo) doc.moveDown(0.2).fontSize(14).text(String(prueba.PruebaInspeccionTitulo), { align: 'center' });
        doc.moveDown(0.8);

        // Brands: show top 3 and aggregate the rest as 'Otras'
        doc.fontSize(12).font('Helvetica-Bold').text('Marcas', { underline: true });
        const brandEntries = Object.entries(brandCounts).sort((a,b) => b[1]-a[1]);
        const palette = ['#4e79a7','#f28e2b','#e15759','#76b7b2','#59a14f','#edc948','#b07aa1','#ff9da7','#9c755f','#bab0ac'];
        const topN = 3;
        const top = brandEntries.slice(0, topN);
        const othersCount = brandEntries.slice(topN).reduce((s, e) => s + (e[1] || 0), 0);
        const displayList = top.slice();
        if (othersCount > 0) displayList.push(['Otras', othersCount]);
        let bx = Number(doc.x) || 0;
        let by = (Number(doc.y) || 0) + 6;
        let bi = 0;
        for (const [marca, cnt] of displayList) {
            const pct = totalLlantas > 0 ? Math.round((cnt * 100) / totalLlantas) : 0;
            const color = palette[bi % palette.length];
            doc.save(); doc.rect(bx, by - 2, 10, 10).fill(color).stroke(); doc.restore();
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  ${marca}: ${cnt} (${pct}%)`, bx + 16, by - 2, { width: Math.max(120, availWidth - (bx + 16) ) });
            by += 14; bi++; if (by > doc.page.height - 120) { doc.addPage(); by = doc.y + 6; }
        }
        // draw a small pie of brands to the right using the displayList
        try {
            const blockTop = Number(doc.y) - ((displayList.length * 14) || 0);
            const reservedHeight = Math.max(120, displayList.length * 14);
            const chartWidth = 160;
            const rightColX = COL_START + TABLE_WIDTH - chartWidth;
            const pieRadius = Math.min(60, Math.floor(Math.min(chartWidth, reservedHeight) / 2) - 4);
            const pieCx = rightColX + Math.floor(chartWidth / 2);
            const pieCy = blockTop + Math.floor(reservedHeight / 2);
            const totalForPie = displayList.reduce((s, e) => s + (e[1] || 0), 0) || 1;
            let angle = -90;
            for (let i = 0; i < displayList.length; i++) {
                const cnt = displayList[i][1] || 0;
                const sweep = (cnt / totalForPie) * 360;
                const color = palette[i % palette.length];
                drawPieSlice(doc, pieCx, pieCy, pieRadius, angle, angle + sweep, { fill: color });
                angle += sweep;
            }
        } catch (e) { /* ignore pie */ }
        doc.moveDown(0.6);

        // Milimetraje: compute below/between/above using parametros (ProfMin/ProfMax)
        try {
            let mmBelow = 0, mmBetween = 0, mmAbove = 0, mmNoData = 0;
            for (const ll of flattened) {
                const medida = catalogMeasureMap[ll.Llantas_idLlantas];
                // resolve param by llanta id then by medida
                let param = parametrosMap[ll.Llantas_idLlantas];
                if (!param && medida) param = parametrosByMedida[medida];
                if (!param) { mmNoData++; continue; }
                const profMin = Number(param.ParametrosProfMin || 0);
                const profMax = Number(param.ParametrosProfMax || 0);
                const mmVals = [];
                if (ll.LlantasInspeccionMm1) mmVals.push(Number(ll.LlantasInspeccionMm1));
                if (ll.LlantasInspeccionMm2) mmVals.push(Number(ll.LlantasInspeccionMm2));
                if (ll.LlantasInspeccionMm3) mmVals.push(Number(ll.LlantasInspeccionMm3));
                if (ll.LlantasInspeccionMm4) mmVals.push(Number(ll.LlantasInspeccionMm4));
                if (mmVals.length === 0) { mmNoData++; continue; }
                const avg = mmVals.reduce((s,v)=>s+v,0) / mmVals.length;
                if (avg < profMin) mmBelow++;
                else if (avg <= profMax) mmBetween++;
                else mmAbove++;
            }

            const mmTotal = Math.max(mmBelow + mmBetween + mmAbove, 1);
            // draw milimetraje card title
            doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Milimetraje (porcentaje)', { underline: true });
            const mmBarX = Number(doc.x) || 0;
            const mmBarY = (Number(doc.y) || 0) + 6;
            const mmBarWidth = 360; const mmBarHeight = 10;
            const mmSegments = [
                { count: mmBelow, color: '#e15759', label: `< ProfMin ${mmBelow}` },
                { count: mmBetween, color: '#59a14f', label: `Entre ${mmBetween}` },
                { count: mmAbove, color: '#f28e2b', label: `> ProfMax ${mmAbove}` }
            ];
            let accX2 = mmBarX;
            for (const seg of mmSegments) {
                if (!seg.count || seg.count <= 0) continue;
                const w = Math.max(1, Math.round(mmBarWidth * (seg.count / mmTotal)));
                try { doc.rect(accX2, mmBarY, w, mmBarHeight).fill(seg.color).stroke(); } catch(e) {}
                accX2 += w;
            }
            // legend to the right
            try {
                const sw = 10;
                const legendX = mmBarX + mmBarWidth + 12;
                let ly = mmBarY - 2;
                for (const seg of mmSegments) {
                    if (!seg.count || seg.count <= 0) continue;
                    const pct = Math.floor((seg.count * 100) / mmTotal);
                    doc.save(); doc.rect(legendX, ly, sw, sw).fill(seg.color).stroke(); doc.restore();
                    doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`${seg.label.replace(/\s+\d+$/,'')}: ${seg.count} (${pct}%)`, legendX + sw + 6, ly, { width: 160 });
                    ly += sw + 6;
                }
            } catch(e) {}
            if (mmNoData > 0) { doc.font('Helvetica').fontSize(10).fillColor('#666666').text(`Sin dato: ${mmNoData}`, mmBarX, mmBarY + mmBarHeight + 14); }
            doc.moveDown(1.2);
        } catch (e) { /* ignore milimetraje errors */ }

        // Presión segmented bar (reuse semaforo visuals)
        // Match app: order OK, Bajo, Alto, Sin dato, Vigía and include vigía inside main bar
        doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Presión de inflado', { underline: true });
        const barX = Number(doc.x) || 0;
        let barY = (Number(doc.y) || 0) + 6;
        // slightly smaller bar for inspection layout
        const barWidth = 360; const barHeight = 10;

        const segments = [
            { count: presionGreen, color: '#59a14f', label: `OK ${presionGreen}` },
            { count: presionRed, color: '#e15759', label: `Bajo ${presionRed}` },
            { count: presionYellow, color: '#f28e2b', label: `Alto ${presionYellow}` },
            { count: presionNoData, color: '#cccccc', label: `Sin dato ${presionNoData}` },
            { count: vigiaCount, color: '#8e44ad', label: `Vigía ${vigiaCount}` }
        ];

        const sumSegments = Math.max(segments.reduce((s, it) => s + (it.count || 0), 0), 1);

        let accX = barX;
        for (const seg of segments) {
            if (!seg.count || seg.count <= 0) continue;
            const w = Math.max(1, Math.round(barWidth * (seg.count / sumSegments)));
            try { doc.rect(accX, barY, w, barHeight).fill(seg.color).stroke(); } catch(e) {}
            try {
                const pct = Math.floor((seg.count * 100) / sumSegments);
                const cx = accX + Math.floor(w/2);
                doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pct}%`, cx - 12, barY + barHeight + 4, { width: 24, align: 'center' });
            } catch(e) {}
            accX += w;
        }
        // Draw stacked legend to the right of the segmented bar
        try {
            const sw = 10;
            const legendX = barX + barWidth + 12;
            let ly = barY - 2;
            for (const seg of segments) {
                if (!seg.count || seg.count <= 0) continue;
                const pct = Math.floor((seg.count * 100) / sumSegments);
                doc.save(); doc.rect(legendX, ly, sw, sw).fill(seg.color).stroke(); doc.restore();
                doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`${seg.label} (${pct}%)`, legendX + sw + 6, ly, { width: 160 });
                ly += sw + 6;
            }
        } catch (e) {
            try { const pctVigia = Math.floor((vigiaCount * 100) / sumSegments); doc.font('Helvetica').fontSize(9).fillColor('#000000').text(` Vigía: ${vigiaCount} (${pctVigia}%)`, { continued: false, width: 120 }); } catch(e){}
        }
        doc.moveDown(2.2);

        // Tipo de piso (include Others to match UI)
        const pisoTotal = Math.max(originalCount + vitalizadoCount + otherPisoCount, 1);
        const pctOriginal = Math.round((originalCount * 100) / pisoTotal);
        const pctVital = Math.round((vitalizadoCount * 100) / pisoTotal);
        const pctOther = Math.round((otherPisoCount * 100) / pisoTotal);
        // Anchor the piso bar explicitly at the left column so it aligns with other bars
        const pisoBarX = COL_START;
        const pisoY = (Number(doc.y) || 0) + 6;
        const pisoBarWidth = 360; // match other segmented bars width
        const pOrigW = Math.max(1, Math.round(pisoBarWidth * (originalCount / pisoTotal)));
        const pVitW = Math.max(1, Math.round(pisoBarWidth * (vitalizadoCount / pisoTotal)));
        const pOtherW = Math.max(1, Math.round(pisoBarWidth * (otherPisoCount / pisoTotal)));
        // UI colors: Original blue, Vitalizado brown, Otros gray
        try { doc.rect(pisoBarX, pisoY, pOrigW, 12).fill('#1976D2').stroke(); } catch(e) {}
        try { doc.rect(pisoBarX + pOrigW, pisoY, pVitW, 12).fill('#6D4C41').stroke(); } catch(e) {}
        try { doc.rect(pisoBarX + pOrigW + pVitW, pisoY, pOtherW, 12).fill('#BDBDBD').stroke(); } catch(e) {}

        // Draw per-segment percentage labels beneath the bar (centered for each segment)
        try {
            const pisoSegments = [
                { key: 'Original', count: originalCount, color: '#1976D2' },
                { key: 'Vitalizado', count: vitalizadoCount, color: '#6D4C41' },
                { key: 'Otros', count: otherPisoCount, color: '#BDBDBD' }
            ];
            const pisoSum = Math.max(pisoSegments.reduce((s, it) => s + (it.count || 0), 0), 1);
            let acc = pisoBarX;
            for (const seg of pisoSegments) {
                const w = Math.max(1, Math.round(pisoBarWidth * ((seg.count || 0) / pisoSum)));
                if (seg.count && seg.count > 0) {
                    try {
                        const pct = Math.floor((seg.count * 100) / pisoSum);
                        const cx = acc + Math.floor(w / 2);
                        doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pct}%`, cx - 12, pisoY + 12 + 4, { width: 24, align: 'center' });
                    } catch (e) {}
                }
                acc += w;
            }

            // Draw stacked legend to the right with counts and percentages (matching other bars)
            try {
                const sw = 10;
                const legendX = pisoBarX + pisoBarWidth + 12;
                let ly = pisoY - 2;
                for (const seg of pisoSegments) {
                    if (!seg.count || seg.count <= 0) continue;
                    const pct = Math.floor((seg.count * 100) / pisoSum);
                    doc.save(); doc.rect(legendX, ly, sw, sw).fill(seg.color).stroke(); doc.restore();
                    doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`${seg.key}: ${seg.count} (${pct}%)`, legendX + sw + 6, ly, { width: 160 });
                    ly += sw + 6;
                }
            } catch (e) {}
        } catch (e) { }

        // Title on the left to match the app layout; keep this block minimal (client shows only the segmented bar)
        try { doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Tipo de piso', COL_START, pisoY - 18, { underline: true }); } catch(e) { try { doc.text('Tipo de piso', { underline: true }); } catch(e2) {} }
        doc.moveDown(1.8);

        // Condición peligrosa
        const safeText = (d, text, x, y, options) => { try { d.text(text, x, y, options || {}); } catch(e) { d.addPage(); d.text(text, options || {}); } };
        const safeRect = (d, x, y, w, h, options) => { try { d.rect(x,y,w,h).fill(options && options.fill ? options.fill : undefined).stroke(); } catch(e){} };
        // ensure the title starts at the left column for consistent alignment
        safeText(doc, 'Condición peligrosa', COL_START, doc.y, { underline: true });
        const pctCPTrue = Math.round((condPelTrue * 100) / Math.max(condPelTrue + condPelFalse, 1)); const cpW = 220; const cpTrueW = Math.max(1, Math.round(cpW * (pctCPTrue / 100)));
        const cpBarY = doc.y + 6; safeRect(doc, COL_START, cpBarY, cpTrueW, 12, { fill: '#e15759' }); safeRect(doc, COL_START + cpTrueW, cpBarY, cpW - cpTrueW, 12, { fill: '#59a14f' });
        try { const c1 = COL_START + Math.floor(cpTrueW/2); const c2 = COL_START + cpTrueW + Math.floor((cpW - cpTrueW)/2); doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pctCPTrue}%`, c1 - 12, cpBarY + 12 + 4, { width: 24, align: 'center' }); const pctCPFalse = 100 - pctCPTrue; doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pctCPFalse}%`, c2 - 12, cpBarY + 12 + 4, { width: 24, align: 'center' }); } catch(e) {}
        // Draw legend with swatches for Condición peligrosa (stacked right)
        try {
            const sw = 10;
            const legendX = COL_START + TABLE_WIDTH - 180;
            let ly = cpBarY - 2;
            doc.save(); doc.rect(legendX, ly, sw, sw).fill('#e15759').stroke(); doc.restore();
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`Peligrosa: ${condPelTrue} (${pctCPTrue}%)`, legendX + sw + 6, ly, { width: 160 });
            ly += sw + 6;
            doc.save(); doc.rect(legendX, ly, sw, sw).fill('#59a14f').stroke(); doc.restore();
            const pctCPFalse = 100 - pctCPTrue;
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`Normal: ${condPelFalse} (${pctCPFalse}%)`, legendX + sw + 6, ly, { width: 160 });
        } catch (e) {
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  Peligrosa: ${condPelTrue} (${pctCPTrue}%)  Normal: ${condPelFalse}`, COL_START + cpW + 12, cpBarY + 2, { width: Math.max(120, availWidth - (COL_START + cpW + 12)) });
        }
        doc.moveDown(2);

        // Observaciones: show as pie chart (top N + Otras)
        doc.fontSize(12).font('Helvetica-Bold').text('Observaciones', COL_START, doc.y, { width: TABLE_WIDTH, underline: true });
        const obsEntries = Object.entries(obsCounts).sort((a,b) => b[1]-a[1]);
        const obsTopN = 3;
        const obsTop = obsEntries.slice(0, obsTopN);
        const obsOthersCount = obsEntries.slice(obsTopN).reduce((s, e) => s + (e[1] || 0), 0);
        const obsDisplay = obsTop.slice();
        if (obsOthersCount > 0) obsDisplay.push(['Otras', obsOthersCount]);
        try {
            const legendX = COL_START;
            let ly = doc.y + 6;
            const colors = ['#1976D2', '#9C27B0', '#7B1FA2', '#BDBDBD'];
            let ci = 0;
            const totalObs = Math.max(obsDisplay.reduce((s, e) => s + (e[1] || 0), 0), 1);
            for (const [label, cnt] of obsDisplay) {
                const pct = Math.round((cnt * 100) / totalObs);
                const sw = 10;
                doc.save(); doc.rect(legendX, ly, sw, sw).fill(colors[ci % colors.length]).stroke(); doc.restore();
                doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`${label}: ${cnt} (${pct}%)`, legendX + sw + 6, ly, { width: 220 });
                ly += sw + 6; ci++;
            }
            // make pie smaller and place it to the right and slightly above the legend
            const chartWidth = 120;
            // compute pie radius conservatively
            const pieRadius = Math.max(10, Math.min(36, Math.floor(Math.min(chartWidth, 80) / 2)));
            // position pie near the right edge of the table area and raise it a bit
            const pieCx = COL_START + TABLE_WIDTH - 12 - Math.ceil(pieRadius * 0.6);
            const pieCy = doc.y - 8;
            let angle = -90;
            for (let i = 0; i < obsDisplay.length; i++) {
                const cnt = obsDisplay[i][1] || 0;
                const sweep = (cnt / totalObs) * 360;
                const color = colors[i % colors.length];
                drawPieSlice(doc, pieCx, pieCy, pieRadius, angle, angle + sweep, { fill: color });
                angle += sweep;
            }
            // move doc.y below legend/pie area
            try { doc.y = Math.max(doc.y, ly) + 8; } catch(e) { doc.moveDown(1); }
        } catch (e) {
            const obsItems = obsEntries.map(([obs, cnt]) => `• ${obs}: ${cnt}`);
            try { doc.list(obsItems, COL_START + 6, doc.y, { bulletRadius: 2, textIndent: 10, width: Math.max(200, availWidth - 20) }); } catch (e2) { obsItems.forEach(it => doc.font('Helvetica').fontSize(10).fillColor('#000000').text(it)); }
        }
        doc.moveDown(0.8);

        // Vehicle details
        // --- NUEVA HOJA: listado al detalle ---
        doc.addPage();
        doc.fillColor('#000000').fontSize(14).font('Helvetica-Bold').text('Detalle de los vehículos', COL_START, doc.y, { width: TABLE_WIDTH, underline: true, align: 'center' }); doc.moveDown(0.4);
        for (const veh of vehiculos) {
            doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text(`${veh.VehiculoInspeccionNo || 'Sin placa'} (${veh.idVehiculoInspeccion || ''})`, COL_START, doc.y);
            const llantas = llantasByVeh[veh.idVehiculoInspeccion] || [];
            if (llantas.length === 0) { doc.font('Helvetica').fontSize(10).text('  - Sin llantas registradas'); } else {
                // Render llantas as a table: Marca | Presión | Piso | Cond | Obs (no Color column for inspección)
                let vehCols = [
                    { header: 'Foto', width: 70 },
                    { header: 'Marca', width: 85 },
                    { header: 'Presión', width: 45 },
                    { header: 'Piso', width: 65 },
                    { header: 'Cond', width: 60 },
                    { header: 'Obs', width: 95 }
                ];

                const drawVehHeader = (y) => {
                    try { doc.rect(COL_START, y, TABLE_WIDTH, CELL_HEIGHT).fill(HEADER_BG); } catch(e) {}
                    doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(9);
                    let x = COL_START;
                    vehCols.forEach(col => { doc.text(col.header, x + 4, y + 6, { width: col.width - 5 }); x += col.width; });
                    doc.fillColor('#000000').font('Helvetica');
                    try { doc.strokeColor(BORDER_COLOR).moveTo(COL_START + TABLE_WIDTH, y).lineTo(COL_START + TABLE_WIDTH, y + CELL_HEIGHT).stroke(); } catch(e) {}
                    return y + CELL_HEIGHT;
                };

                const drawVehRow = async (y, data) => {
                    // ensure font is set before measuring so heightOfString is accurate
                    try { doc.font('Helvetica').fontSize(9); } catch (e) {}
                    // calculate required row height based on cell contents (allow multiline for Observaciones)
                    const heights = [];
                    for (let i = 0; i < data.length; i++) {
                        const cell = data[i];
                        const colW = vehCols[i].width - 8;
                        let txt = '';
                        if (cell && typeof cell === 'object' && cell.__image) txt = '';
                        else if (cell && typeof cell === 'object' && cell.color) txt = String(cell.text || '');
                        else txt = (cell === undefined || cell === null) ? '—' : String(cell);
                        try {
                            const h = doc.heightOfString(txt, { width: colW });
                            const minH = (cell && typeof cell === 'object' && cell.__image) ? 72 : CELL_HEIGHT;
                            heights.push(Math.max(minH, Math.ceil(h) + 6));
                        } catch (e) { heights.push(CELL_HEIGHT); }
                    }
                    const rowH = Math.max(...heights, CELL_HEIGHT);
                    // draw each cell with the computed height
                    doc.font('Helvetica').fontSize(9).fillColor('#000000');
                    let x = COL_START;
                    for (let i = 0; i < data.length; i++) {
                        const cell = data[i];
                        const colW = vehCols[i].width;
                        if (cell && typeof cell === 'object' && cell.__image) {
                            const raw = cell.value || null;
                            const hasRaw = !!(raw && String(raw).trim().length > 0);
                            if (!hasRaw) {
                                try { doc.text('Sin foto', x + 4, y + 4, { width: colW - 8 }); } catch(_) {}
                            } else {
                            let painted = false;
                            try {
                                painted = await renderImageInBoxAsync(doc, raw, x + 4, y + 4, colW - 8, rowH - 8, '[reports][inspeccion][table]');
                                console.log('[reports][inspeccion][table] foto len=', raw ? String(raw).length : 0, 'painted=', painted);
                            } catch (_) { painted = false; }
                            if (!painted) {
                                try { doc.text('IMG ERR', x + 4, y + 4, { width: colW - 8 }); } catch(_) {}
                            }
                            }
                        } else if (cell && typeof cell === 'object' && cell.color) {
                            const hex = colorNameToHex(cell.color) || (typeof cell.color === 'string' ? cell.color : null) || '#FFFFFF';
                            try { doc.rect(x, y, colW, rowH).fill(hex).stroke(); } catch(e) {}
                            const textColor = isLightHex(hex) ? '#000000' : '#FFFFFF';
                            doc.fillColor(textColor).font('Helvetica-Bold').fontSize(9);
                            const label = cell.text || '';
                            try { doc.text(label, x + 4, y + 4, { width: colW - 8 }); } catch(e) {}
                            doc.fillColor('#000000').font('Helvetica').fontSize(9);
                        } else {
                            const cellText = (cell === undefined || cell === null) ? '—' : String(cell);
                            try { doc.text(cellText, x + 4, y + 4, { width: colW - 8 }); } catch(e) {}
                        }
                        try { doc.strokeColor(BORDER_COLOR).moveTo(x, y).lineTo(x, y + rowH).stroke(); } catch(e) {}
                        x += colW;
                    }
                    try { doc.moveTo(COL_START, y + rowH).lineTo(COL_START + TABLE_WIDTH, y + rowH).strokeColor(BORDER_COLOR).stroke(); } catch(e) {}
                    try { doc.strokeColor(BORDER_COLOR).moveTo(COL_START + TABLE_WIDTH, y).lineTo(COL_START + TABLE_WIDTH, y + rowH).stroke(); } catch(e) {}
                    return y + rowH;
                };

                let vy = doc.y + 6;
                vy = drawVehHeader(vy);
                for (const ll of llantas) {
                    const marca = catalogMap[String(ll.Llantas_idLlantas)] || 'Otras';
                    const pres = (ll.LlantasInspeccionVigia == 1 || ll.LlantasInspeccionVigia === '1') ? 'Vigía' : (ll.LlantasInspeccionPresion != null ? String(ll.LlantasInspeccionPresion) : 'Sin dato');
                    const piso = ll.LlantasInspeccionPiso || 'Original';
                    const cond = (ll.LlantasInspeccionCondPel == 1 || ll.LlantasInspeccionCondPel === '1') ? 'Peligrosa' : 'Normal';
                    const obs = ll.LlantasInspeccionObservacion || '';
                    const fotoCell = { __image: true, value: ll.LlantasInspeccionFoto || ll.LlantasInspeccionFoto2 || ll.LlantasInspeccionFoto1 || null };
                    vy = await drawVehRow(vy, [fotoCell, marca, pres, piso, cond, obs]);
                    if (vy > doc.page.height - 80) { doc.addPage(); vy = drawVehHeader(doc.y + 6); }
                }

                try { doc.y = vy + 2; } catch(e) { doc.moveDown(0.3); }
            }
            doc.moveDown(0.4); if (doc.y > doc.page.height - 80) doc.addPage();
        }

        doc.end();
    } catch (err) {
        console.error('Error generando PDF inspeccion desde payload:', err);
        try { if (typeof doc !== 'undefined' && doc && typeof doc.destroy === 'function') doc.destroy(); } catch (e) { /* ignore */ }
        handleServerError(res, 'Error generando reporte inspección', err);
    }
}

router.post('/inspeccion/report.pdf', generateInspeccionReportFromPayload);

// POST /desecho/report.pdf
// Accepts a JSON payload from the frontend containing the report data for a Prueba Desecho
async function generateDesechoReportFromPayload(req, res) {
    try {
        console.log('[reports] generateDesechoReportFromPayload invoked');
        const payload = req.body || {};
        const prueba = payload.prueba || {};
        const flota = payload.flota || {};
        const llantas = payload.llantas || payload.llantasPorVehiculo || [];
        const llantaCatalog = payload.llantaCatalog || [];

        // Flatten shapes: if llantas is an object of arrays (llantasPorVehiculo), flatten
        const flattened = Array.isArray(llantas) ? llantas : Object.values(llantas).flat();

        // Compute aggregations
        const brandMap = {};
        for (const c of llantaCatalog) { if (c && c.idLlantas) brandMap[c.idLlantas] = c.LlantasMarca || 'Otras'; }
        const marcaCounts = {};
        const causaCounts = {};
        const pisoCounts = {};
        const dateCounts = {};
        const remanentes = [];

        for (const ll of flattened) {
            const marca = brandMap[ll.Llantas_idLlantas] || 'Otras';
            marcaCounts[marca] = (marcaCounts[marca] || 0) + 1;
            const causa = (ll.LlantasDesechoCausaDes && ll.LlantasDesechoCausaDes.trim()) ? ll.LlantasDesechoCausaDes.trim() : 'Sin causa';
            causaCounts[causa] = (causaCounts[causa] || 0) + 1;
            const piso = (ll.LlantasDesechoPiso && ll.LlantasDesechoPiso.trim()) ? ll.LlantasDesechoPiso.trim() : 'Desconocido';
            pisoCounts[piso] = (pisoCounts[piso] || 0) + 1;
            const fecha = ((ll.LlantasDesechoFecha || '').toString().split('T')[0]) || 'Sin fecha';
            dateCounts[fecha] = (dateCounts[fecha] || 0) + 1;
            if (ll.LlantasDesechoRemanente !== undefined && ll.LlantasDesechoRemanente !== null) remanentes.push(Number(ll.LlantasDesechoRemanente));
        }

        // Start PDF
        const doc = new PDFDocument({ margin: 40, size: 'A4' });
        res.setHeader('Content-Type', 'application/pdf');
        const filename = `prueba_desecho_${prueba.idPruebasDesecho || 'report'}.pdf`;
        res.setHeader('Content-Disposition', `inline; filename=${filename}`);
        doc.pipe(res);

        // Header with logo
        try {
            const logoPath = resolveImagePath('yokohamalogo.png') || ensureLogoPlaceholder();
            if (logoPath && fs.existsSync(logoPath)) {
                const imgWidth = 220;
                const x = (doc.page.width - imgWidth) / 2;
                doc.image(logoPath, x, doc.y, { width: imgWidth });
                doc.moveDown(0.8);
            }
        } catch (e) { /* ignore */ }
        doc.fontSize(18).font('Helvetica-Bold').text('Reporte - Desecho', { align: 'center' });
        if (prueba && prueba.PruebasDesechoNombre) doc.fontSize(12).text(prueba.PruebasDesechoNombre, { align: 'center' });
        doc.moveDown(0.8);

        // Shared layout constants (match semáforo)
        const COL_START = 40;
        const TABLE_WIDTH = 515;
        const CELL_HEIGHT = 20;
        const BORDER_COLOR = '#AAAAAA';
        const HEADER_BG = '#B3B3B3';
        const availWidth = (doc.page && doc.page.width ? doc.page.width : 595) - (doc.page && doc.page.margins ? (doc.page.margins.left + doc.page.margins.right) : 80) - 40;

        // Marcas (with swatches) + pie chart to the right
        doc.fontSize(12).font('Helvetica-Bold').text('Marcas', { underline: true });
        const brandEntries = Object.entries(marcaCounts).sort((a,b) => b[1]-a[1]);
        const palette = ['#4e79a7','#f28e2b','#e15759','#76b7b2','#59a14f','#edc948','#b07aa1','#ff9da7','#9c755f','#bab0ac'];
        // Layout for legend (left) + pie (right)
        const totalLlantas = flattened.length || 0;
        const chartWidth = Math.min(availWidth, 420);
        const leftColX = COL_START;
        const leftColWidth = Math.min(260, Math.round(availWidth * 0.55));
        const rightColX = leftColX + leftColWidth + 12;
        const rightChartWidth = Math.max(80, Math.min(200, Math.round(availWidth - leftColWidth - 40)));
        const swatchSize = 10;

        // Reserve a fixed block height for legend + pie to avoid overlap
        const rowHeight = 14;
        const legendHeight = rowHeight * Math.max(brandEntries.length, 1);
        const pieMaxRadius = Math.min(Math.floor(chartWidth / 2), 90);
        const pieRadius = Math.min(pieMaxRadius, Math.floor(Math.max(40, legendHeight / 2)));
        const pieDiameter = pieRadius * 2;
        const reservedHeight = Math.max(legendHeight, pieDiameter) + 12; // padding

        // If not enough space, start a new page
        if ((Number(doc.y) || 0) + reservedHeight > doc.page.height - 100) {
            doc.addPage();
        }

        const blockTop = Number(doc.y) || 0;
        const startY = blockTop + 6;

        // draw legend rows (left column)
        let ly = startY;
        for (let i = 0; i < brandEntries.length; i++) {
            const marca = brandEntries[i][0];
            const cnt = brandEntries[i][1] || 0;
            const pct = totalLlantas > 0 ? Math.round((cnt * 100) / totalLlantas) : 0;
            const color = palette[i % palette.length];
            try { doc.save(); doc.rect(leftColX, ly - 2, swatchSize, swatchSize).fill(color).stroke(); doc.restore(); } catch (e) {}
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  ${marca}: ${cnt} (${pct}%)`, leftColX + swatchSize + 6, ly - 2, { width: leftColWidth - (swatchSize + 10) });
            ly += rowHeight;
        }

        // draw pie centered in right column within reserved block
        const pieCx = rightColX + Math.floor(rightChartWidth / 2);
        const pieCy = blockTop + Math.floor(reservedHeight / 2);
        const totalForPie = brandEntries.reduce((s, e) => s + (e[1] || 0), 0) || 1;
        let angle = -90;
        for (let i = 0; i < brandEntries.length; i++) {
            const cnt = brandEntries[i][1] || 0;
            const sweep = (cnt / totalForPie) * 360;
            const color = palette[i % palette.length];
            drawPieSlice(doc, pieCx, pieCy, pieRadius, angle, angle + sweep, { fill: color });
            angle += sweep;
        }

        // Move cursor to the end of reserved block to avoid overlap
        const desiredY = blockTop + reservedHeight + 12;
        try { doc.text('', COL_START, desiredY); } catch (e) { try { doc.y = desiredY; } catch (e2) { /* ignore */ } }

        // Causas (with pie chart to the right)
        doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Causas de desecho', { underline: true });
        const causaEntries = Object.entries(causaCounts).sort((a,b) => b[1]-a[1]);
        // small chart layout
        const causaTotal = flattened.length || 0;
        const causaChartWidth = Math.min(availWidth, 420);
        const causaLeftX = COL_START;
        const causaLeftW = Math.min(260, Math.round(availWidth * 0.55));
        const causaRightX = causaLeftX + causaLeftW + 12;
        const causaRightChartW = Math.max(80, Math.min(200, Math.round(availWidth - causaLeftW - 40)));
        const swatch = 10;

        const rowH = 14;
        const legendH = rowH * Math.max(causaEntries.length, 1);
        const pieMaxR = Math.min(Math.floor(causaChartWidth / 2), 70);
        const pieR = Math.min(pieMaxR, Math.floor(Math.max(30, legendH / 2)));
        const reservedH = Math.max(legendH, pieR * 2) + 12;
        if ((Number(doc.y) || 0) + reservedH > doc.page.height - 100) doc.addPage();
        const top = Number(doc.y) || 0;
        let ly2 = top + 6;
        for (let i = 0; i < causaEntries.length; i++) {
            const c = causaEntries[i][0];
            const cnt = causaEntries[i][1] || 0;
            const pct = causaTotal > 0 ? Math.round((cnt * 100) / causaTotal) : 0;
            const color = palette[i % palette.length];
            try { doc.save(); doc.rect(causaLeftX, ly2 - 2, swatch, swatch).fill(color).stroke(); doc.restore(); } catch (e) {}
            doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`  ${c}: ${cnt} (${pct}%)`, causaLeftX + swatch + 6, ly2 - 2, { width: causaLeftW - (swatch + 10) });
            ly2 += rowH;
        }

        // pie on the right of causas block
        const pieCx2 = causaRightX + Math.floor(causaRightChartW / 2);
        const pieCy2 = top + Math.floor(reservedH / 2);
        const totalForCausaPie = causaEntries.reduce((s,e)=>s+(e[1]||0),0) || 1;
        let ang2 = -90;
        for (let i = 0; i < causaEntries.length; i++) {
            const cnt = causaEntries[i][1] || 0;
            const sweep = (cnt / totalForCausaPie) * 360;
            const color = palette[i % palette.length];
            drawPieSlice(doc, pieCx2, pieCy2, pieR, ang2, ang2 + sweep, { fill: color });
            ang2 += sweep;
        }

        // advance cursor
        const endY = top + reservedH + 12;
        try { doc.text('', COL_START, endY); } catch (e) { try { doc.y = endY; } catch (e2) { /* ignore */ } }
        doc.moveDown(0.6);

        // Remanente summary as horizontal bar chart
        doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Remanente (mm)', { underline: true });
        if (remanentes.length === 0) {
            doc.font('Helvetica').fontSize(10).text('Sin datos');
        } else {
            const byRounded = {};
            for (const r of remanentes) { const k = Math.round(r); byRounded[k] = (byRounded[k] || 0) + 1; }
            const sorted = Object.entries(byRounded).map(e => [Number(e[0]), e[1]]).sort((a,b)=>a[0]-b[0]);

            // chart layout
            const labelW = 50;
            const maxChartW = Math.min(360, Math.max(120, availWidth - labelW - 80));
            const counts = sorted.map(s => s[1] || 0);
            const maxCount = counts.length > 0 ? Math.max(...counts) : 1;
            const barH = 12;
            const gap = 8;
            const blockH = sorted.length * (barH + gap) + 8;
            if ((Number(doc.y) || 0) + blockH > doc.page.height - 100) doc.addPage();

            let cy = (Number(doc.y) || 0) + 6;
            for (let i = 0; i < sorted.length; i++) {
                const mm = sorted[i][0];
                const cnt = sorted[i][1] || 0;
                const barX = COL_START + labelW + 8;
                const w = Math.max(2, Math.round((cnt / Math.max(1, maxCount)) * maxChartW));
                const color = palette[i % palette.length];

                // mm label
                doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`${mm} mm`, COL_START, cy + 2, { width: labelW - 4 });
                // bar
                try { doc.save(); doc.rect(barX, cy, w, barH).fill(color).stroke(); doc.restore(); } catch (e) {}
                // count at end
                doc.font('Helvetica').fontSize(9).fillColor('#000000').text(String(cnt), barX + w + 6, cy + 1);

                cy += barH + gap;
            }
            // move cursor after chart
            try { doc.text('', COL_START, cy + 6); } catch (e) { try { doc.y = cy + 6; } catch (e2) { /* ignore */ } }
        }
        doc.moveDown(0.6);

        // Tipo de piso (segmented bar like other reports)
        // tolerant counts: try to detect 'Original' and 'Vitalizado' keywords, rest -> Otros
        const pisoEntries = Object.entries(pisoCounts).sort((a,b)=>b[1]-a[1]);
        let originalCount = 0, vitalizadoCount = 0;
        for (const ll of flattened) {
            const p = String((ll.LlantasDesechoPiso || '')).toLowerCase();
            if (!p || p.trim() === '') continue;
            if (p.includes('original')) originalCount++;
            else if (p.includes('vital')) vitalizadoCount++;
        }
        const otherPisoCount = Math.max(0, flattened.length - originalCount - vitalizadoCount);
        const pisoTotal = Math.max(originalCount + vitalizadoCount + otherPisoCount, 1);

        // Anchor the piso bar at the left column to align with other bars
        try { doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000').text('Tipo de piso', COL_START, doc.y, { underline: true }); } catch(e) { try { doc.text('Tipo de piso', { underline: true }); } catch(e2) {} }
        const pisoBarX = COL_START;
        const pisoY = (Number(doc.y) || 0) + 6;
        const pisoBarWidth = 360;
        const pOrigW = Math.max(1, Math.round(pisoBarWidth * (originalCount / pisoTotal)));
        const pVitW = Math.max(1, Math.round(pisoBarWidth * (vitalizadoCount / pisoTotal)));
        const pOtherW = Math.max(1, Math.round(pisoBarWidth * (otherPisoCount / pisoTotal)));
        try { doc.rect(pisoBarX, pisoY, pOrigW, 12).fill('#1976D2').stroke(); } catch(e) {}
        try { doc.rect(pisoBarX + pOrigW, pisoY, pVitW, 12).fill('#6D4C41').stroke(); } catch(e) {}
        try { doc.rect(pisoBarX + pOrigW + pVitW, pisoY, pOtherW, 12).fill('#BDBDBD').stroke(); } catch(e) {}

        // percentage labels centered under each segment
        try {
            const pisoSegments = [
                { key: 'Original', count: originalCount, color: '#1976D2' },
                { key: 'Vitalizado', count: vitalizadoCount, color: '#6D4C41' },
                { key: 'Otros', count: otherPisoCount, color: '#BDBDBD' }
            ];
            const pisoSum = Math.max(pisoSegments.reduce((s, it) => s + (it.count || 0), 0), 1);
            let acc = pisoBarX;
            for (const seg of pisoSegments) {
                const w = Math.max(1, Math.round(pisoBarWidth * ((seg.count || 0) / pisoSum)));
                if (seg.count && seg.count > 0) {
                    try {
                        const pct = Math.floor((seg.count * 100) / pisoSum);
                        const cx = acc + Math.floor(w / 2);
                        doc.font('Helvetica').fontSize(8).fillColor('#000000').text(`${pct}%`, cx - 12, pisoY + 12 + 4, { width: 24, align: 'center' });
                    } catch (e) {}
                }
                acc += w;
            }

            // stacked legend to the right with counts
            try {
                const sw = 10;
                const legendX = pisoBarX + pisoBarWidth + 12;
                let ly = pisoY - 2;
                for (const seg of pisoSegments) {
                    if (!seg.count || seg.count <= 0) continue;
                    const pct = Math.floor((seg.count * 100) / pisoSum);
                    doc.save(); doc.rect(legendX, ly, sw, sw).fill(seg.color).stroke(); doc.restore();
                    doc.font('Helvetica').fontSize(10).fillColor('#000000').text(`${seg.key}: ${seg.count} (${pct}%)`, legendX + sw + 6, ly, { width: 160 });
                    ly += sw + 6;
                }
            } catch (e) {}
        } catch (e) { }

        doc.moveDown(1.0);

        // Diagrama de ubicación de desecho con porcentajes, igual al reporte visual de la pantalla
        try {
            const selectedUbi = (flattened || []).map((ll) => ll && ll.LlantasDesechoUbi).find((v) => v && String(v).trim()) || null;
            drawDesechoTireDiagram(doc, flattened, selectedUbi);
        } catch (e) {
            console.error('[reports][desecho] tire diagram error:', e && e.stack ? e.stack : e.message);
        }

        // Llantas por fecha -> render as monthly line chart (counts per month)
        const dateChartBlockHeight = 180;
        if (doc.y > doc.page.height - dateChartBlockHeight) {
            doc.addPage();
        }
        const titleY = (Number(doc.y) || 0);
        try {
            doc.fontSize(12).font('Helvetica-Bold').fillColor('#000000');
            // absolute placement at left column
            doc.text('Cantidad de llantas por fecha', COL_START, titleY, { underline: true });
        } catch (e) {
            try { doc.text('Cantidad de llantas por fecha', { underline: true }); } catch (e2) { /* ignore */ }
        }
        // reserve space and position chart below the title explicitly to avoid misalignment
        const chartTopFromTitle = titleY + 22;
        try { doc.x = COL_START; doc.y = chartTopFromTitle; } catch (e) { /* ignore */ }
        // Build month buckets (0-11) and a special 'Sin' bucket for missing dates
        const monthLabels = ['Ene','Feb','Mar','Abr','May','Jun','Jul','Ago','Sep','Oct','Nov','Dic'];
        const monthCounts = new Array(12).fill(0);
        let sinCount = 0;
        try {
            for (const k of Object.keys(dateCounts || {})) {
                const cnt = dateCounts[k] || 0;
                if (!k || String(k).toLowerCase().includes('sin')) { sinCount += cnt; continue; }
                // Expecting YYYY-MM-DD or similar
                const parts = String(k).split('-');
                let monthIdx = null;
                if (parts.length >= 2 && !isNaN(Number(parts[1]))) {
                    monthIdx = Math.max(0, Math.min(11, Number(parts[1]) - 1));
                } else {
                    // Try Date parse fallback
                    const dt = new Date(k);
                    if (!isNaN(dt.getTime())) monthIdx = dt.getMonth();
                }
                if (monthIdx === null) { sinCount += cnt; } else { monthCounts[monthIdx] += cnt; }
            }
        } catch (e) { /* ignore aggregation errors */ }

        // Compose points: include only months that have data plus optional 'Sin'
        const points = [];
        for (let m = 0; m < 12; m++) if (monthCounts[m] > 0) points.push({ label: monthLabels[m], count: monthCounts[m] });
        if (sinCount > 0) points.push({ label: 'Sin', count: sinCount });

        if (points.length === 0) {
            doc.font('Helvetica').fontSize(10).text('Sin datos');
            doc.moveDown(0.8);
        } else {
            const chartLeft = COL_START;
            const chartWidth = Math.min(420, Math.max(200, availWidth - 40));
            const chartTop = chartTopFromTitle; // use explicit position based on title
            const chartHeight = 120;
            const innerH = chartHeight - 30; // leave space for month labels
            const n = points.length;
            const maxCount = Math.max(...points.map(p => p.count), 1);

            // Draw baseline (optional subtle axis)
            try { doc.save(); doc.moveTo(chartLeft, chartTop + innerH).lineTo(chartLeft + chartWidth, chartTop + innerH).strokeColor('#CCCCCC').stroke(); doc.restore(); } catch(e) {}

            // compute x positions
            const step = n > 1 ? (chartWidth / (n - 1)) : 0;
            const coords = [];
            for (let i = 0; i < n; i++) {
                const cx = chartLeft + (n === 1 ? Math.floor(chartWidth / 2) : Math.round(i * step));
                const v = points[i].count;
                const cy = chartTop + Math.round((1 - (v / maxCount)) * innerH);
                coords.push({ x: cx, y: cy, v });
            }

            // Draw line connecting points
            try {
                doc.save();
                doc.moveTo(coords[0].x, coords[0].y);
                for (let i = 1; i < coords.length; i++) doc.lineTo(coords[i].x, coords[i].y);
                doc.lineWidth(1.5).strokeColor('#1976D2').stroke();
                doc.restore();
            } catch (e) { /* ignore */ }

            // Draw points and numeric labels
            for (const p of coords) {
                try { doc.circle(p.x, p.y, 3).fill('#1976D2'); } catch (e) {}
                try { doc.font('Helvetica').fontSize(10).fillColor('#000000').text(String(p.v), p.x - 8, p.y - 16, { width: 30, align: 'center', lineBreak: false }); } catch(e){}
            }

            // Draw month labels
            for (let i = 0; i < n; i++) {
                const lab = points[i].label || '';
                const cx = coords[i].x;
                try { doc.font('Helvetica').fontSize(12).fillColor('#000000').text(lab, cx - 16, chartTop + innerH + 10, { width: 32, align: 'center', lineBreak: false }); } catch(e){}
            }

            // Advance cursor after chart
            try { doc.text('', COL_START, chartTop + chartHeight + 6); } catch (e) { try { doc.y = chartTop + chartHeight + 6; } catch (e2) { /* ignore */ } }
            doc.moveDown(0.8);
        }

        // --- NUEVA HOJA: listado al detalle ---
        doc.addPage();
        // Detalle de llantas (table matching semáforo format)
        doc.fontSize(12).font('Helvetica-Bold').text('Detalle de llantas', { underline: true });
        // columns: Marca | Causa | Piso | Rem
        let vehCols = [
            { header: 'Foto', width: 70 },
            { header: 'Marca', width: 60 },
            { header: 'Causa', width: 95 },
            { header: 'Piso', width: 60 },
            { header: 'Rem', width: 35 }
        ];
        try { const fixed = vehCols.slice(0, vehCols.length - 1).reduce((s, c) => s + (c.width || 0), 0); vehCols[vehCols.length - 1].width = Math.max(60, TABLE_WIDTH - fixed); } catch(e) {}

        const drawVehHeader = (y) => {
            try { doc.rect(COL_START, y, TABLE_WIDTH, CELL_HEIGHT).fill(HEADER_BG); } catch(e) {}
            doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(9);
            let x = COL_START;
            vehCols.forEach(col => { doc.text(col.header, x + 4, y + 6, { width: col.width - 5 }); x += col.width; });
            doc.fillColor('#000000').font('Helvetica');
            try { doc.strokeColor(BORDER_COLOR).moveTo(COL_START + TABLE_WIDTH, y).lineTo(COL_START + TABLE_WIDTH, y + CELL_HEIGHT).stroke(); } catch(e) {}
            return y + CELL_HEIGHT;
        };

        const drawVehRow = async (y, data) => {
            try { doc.font('Helvetica').fontSize(9); } catch(e) {}
            const heights = [];
            for (let i = 0; i < data.length; i++) {
                const cell = data[i];
                const colW = vehCols[i].width - 8;
                let txt = '';
                if (cell && typeof cell === 'object' && cell.__image) txt = '';
                else if (cell && typeof cell === 'object' && cell.color) txt = String(cell.text || ''); else txt = (cell === undefined || cell === null) ? '—' : String(cell);
                try { const h = doc.heightOfString(txt, { width: colW }); const minH = (cell && typeof cell === 'object' && cell.__image) ? 72 : CELL_HEIGHT; heights.push(Math.max(minH, Math.ceil(h) + 6)); } catch(e) { heights.push(CELL_HEIGHT); }
            }
            const rowH = Math.max(...heights, CELL_HEIGHT);
            doc.font('Helvetica').fontSize(9).fillColor('#000000'); let x = COL_START;
            for (let i = 0; i < data.length; i++) {
                const cell = data[i]; const colW = vehCols[i].width;
                if (cell && typeof cell === 'object' && cell.__image) {
                    const raw = cell.value || null;
                    const hasRaw = !!(raw && String(raw).trim().length > 0);
                    if (!hasRaw) {
                        try { doc.text('Sin foto', x + 4, y + 4, { width: colW - 8 }); } catch(_) {}
                    } else {
                    let painted = false;
                    try {
                        painted = await renderImageInBoxAsync(doc, raw, x + 4, y + 4, colW - 8, rowH - 8, '[reports][desecho][table]');
                        console.log('[reports][desecho][table] foto len=', raw ? String(raw).length : 0, 'painted=', painted);
                    } catch (_) { painted = false; }
                    if (!painted) {
                        try { doc.text('IMG ERR', x + 4, y + 4, { width: colW - 8 }); } catch(_) {}
                    }
                    }
                } else if (cell && typeof cell === 'object' && cell.color) {
                    const hex = colorNameToHex(cell.color) || (typeof cell.color === 'string' ? cell.color : null) || '#FFFFFF';
                    try { doc.rect(x, y, colW, rowH).fill(hex).stroke(); } catch(e) {}
                    const textColor = isLightHex(hex) ? '#000000' : '#FFFFFF'; doc.fillColor(textColor).font('Helvetica-Bold').fontSize(9);
                    const label = cell.text || '';
                    try { doc.text(label, x + 4, y + 4, { width: colW - 8 }); } catch(e) {}
                    doc.fillColor('#000000').font('Helvetica').fontSize(9);
                } else { const cellText = (cell === undefined || cell === null) ? '—' : String(cell); try { doc.text(cellText, x + 4, y + 4, { width: colW - 8 }); } catch(e) {} }
                try { doc.strokeColor(BORDER_COLOR).moveTo(x, y).lineTo(x, y + rowH).stroke(); } catch(e) {}
                x += colW;
            }
            try { doc.moveTo(COL_START, y + rowH).lineTo(COL_START + TABLE_WIDTH, y + rowH).strokeColor(BORDER_COLOR).stroke(); } catch(e) {}
            try { doc.strokeColor(BORDER_COLOR).moveTo(COL_START + TABLE_WIDTH, y).lineTo(COL_START + TABLE_WIDTH, y + rowH).stroke(); } catch(e) {}
            return y + rowH;
        };

        let vy = doc.y + 6; vy = drawVehHeader(vy);
        for (const ll of flattened) {
            const marca = brandMap[ll.Llantas_idLlantas] || 'Otras';
            const causa = ll.LlantasDesechoCausaDes || '-';
            const piso = ll.LlantasDesechoPiso || '-';
            const rem = (ll.LlantasDesechoRemanente !== undefined && ll.LlantasDesechoRemanente !== null) ? String(ll.LlantasDesechoRemanente) : '-';
            const obs = ll.LlantasDesechoObservacion || '';
            const fotoCell = { __image: true, value: ll.LlantasDesechoFoto1 || ll.LlantasDesechoFoto2 || ll.LlantasDesechoFoto || null };
            vy = await drawVehRow(vy, [fotoCell, marca, causa, piso, rem]);
            if (vy > doc.page.height - 120) { doc.addPage(); vy = drawVehHeader(doc.y + 6); }
        }

        doc.end();
    } catch (err) {
        console.error('Error generating desecho PDF:', err);
        handleServerError(res, 'Error generando reporte desecho', err);
    }
}

router.post('/desecho/report.pdf', generateDesechoReportFromPayload);

// Endpoint para enviar gráficos por correo
router.post('/send-chart-email', async (req, res) => {
    try {
        const { emails, subject, chartName, pdfBase64 } = req.body || {};
        const recipients = normalizeEmailList(emails);

        if (!recipients.length) {
            return res.status(400).json({ message: 'Debe indicar al menos un correo' });
        }

        const invalidEmails = recipients.filter((email) => !isValidEmail(email));
        if (invalidEmails.length > 0) {
            return res.status(400).json({ message: `Correos inválidos: ${invalidEmails.join(', ')}` });
        }

        if (!pdfBase64 || typeof pdfBase64 !== 'string') {
            return res.status(400).json({ message: 'Falta el PDF en base64' });
        }

        // Convertir base64 a buffer
        let pdfBuffer;
        try {
            pdfBuffer = Buffer.from(pdfBase64, 'base64');
            if (pdfBuffer.length === 0) {
                return res.status(400).json({ message: 'El PDF está vacío' });
            }
        } catch (error) {
            return res.status(400).json({ message: 'PDF en base64 inválido' });
        }

        const smtpHost = process.env.SMTP_HOST;
        const smtpPort = Number(process.env.SMTP_PORT || 587);
        const smtpUser = process.env.SMTP_USER;
        const smtpPass = process.env.SMTP_PASS;
        const smtpFrom = process.env.SMTP_FROM || smtpUser;
        const smtpSecure = String(process.env.SMTP_SECURE || 'false').toLowerCase() === 'true';

        if (!smtpHost || !smtpUser || !smtpPass || !smtpFrom) {
            return res.status(400).json({
                message: 'Falta configuración SMTP (SMTP_HOST, SMTP_USER, SMTP_PASS, SMTP_FROM)'
            });
        }

        const transporter = nodemailer.createTransport({
            host: smtpHost,
            port: smtpPort,
            secure: smtpSecure,
            auth: {
                user: smtpUser,
                pass: smtpPass
            }
        });

        const safeChartName = String(chartName || 'Gráfico').trim();
        const safeSubject = String(subject || `Gráfico: ${safeChartName}`).trim();
        const safeFilePart = sanitizeFileNamePart(safeChartName);
        const timestamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);
        const attachmentName = `${safeFilePart}_${timestamp}.pdf`;

        const html = `
            <div style="font-family:Segoe UI,Tahoma,Arial,sans-serif;color:#111827;">
                <h3 style="margin:0 0 12px 0;">${safeSubject}</h3>
                <p style="margin:0 0 12px 0;">Se adjunta la gráfica en PDF de ${safeChartName}.</p>
                <p style="margin:0;color:#6b7280;font-size:12px;">Generado: ${new Date().toLocaleString('es-MX')}</p>
            </div>
        `;

        await transporter.sendMail({
            from: smtpFrom,
            to: recipients.join(','),
            subject: safeSubject,
            html,
            text: `Se adjunta la gráfica en PDF de ${safeChartName}.`,
            attachments: [
                {
                    filename: attachmentName,
                    content: pdfBuffer,
                    contentType: 'application/pdf'
                }
            ]
        });

        return res.json({ message: 'Correo enviado correctamente' });
    } catch (error) {
        return handleServerError(res, 'Error al enviar correo con gráfico', error);
    }
});

module.exports = router;