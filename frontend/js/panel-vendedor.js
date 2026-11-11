const sesion = Sesion.requerir(['ADMIN', 'VENDEDOR']);

// Analytics: docs/REPOSICION.md y docs/VENCIMIENTOS.md. Core: retiro en docs/API-LOTES.md.
const fuente = {
    predicciones: () => analyticsApi('/predicciones'),
    vencimientos: () => analyticsApi('/vencimientos'),
    proveedores: () => coreApi('/proveedores'),
    retirar: (loteId, observacion) => coreApi(`/lotes/${loteId}/retirar`, {
        method: 'POST',
        body: observacion ? { observacion } : {},
    }),
};

const CLASE_CRITICIDAD = { ALTA: 'text-bg-danger', MEDIA: 'text-bg-warning', BAJA: 'text-bg-secondary' };

let paraVender = [];
let paraRetirar = [];
let modalRetiro;

async function iniciar() {
    renderLayout(sesion);
    modalRetiro = new bootstrap.Modal('#modal-retiro');
    document.querySelectorAll('[data-panel]').forEach(tarjeta =>
        tarjeta.addEventListener('click', () => alternarPanel(tarjeta.dataset.panel)));
    document.getElementById('form-retiro').addEventListener('submit', confirmarRetiro);
    await Promise.all([cargarSolicitudes(), cargarVencimientos()]);
}

/** Muestra el detalle de la tarjeta elegida debajo y oculta los demás. Un segundo click lo cierra. */
function alternarPanel(nombre) {
    document.querySelectorAll('[data-panel]').forEach(tarjeta => {
        const abrir = tarjeta.dataset.panel === nombre && tarjeta.getAttribute('aria-expanded') !== 'true';
        tarjeta.setAttribute('aria-expanded', String(abrir));
        tarjeta.classList.toggle('activa', abrir);
        document.getElementById(`panel-${tarjeta.dataset.panel}`).classList.toggle('d-none', !abrir);
    });
}

function mostrarTotal(nombre, total, detalle) {
    document.getElementById(`total-${nombre}`).textContent = total;
    if (detalle) document.getElementById(`detalle-${nombre}`).textContent = detalle;
}

// --- Solicitar a proveedor ---

async function cargarSolicitudes() {
    const contenedor = document.getElementById('lista-solicitar');
    try {
        const [predicciones, proveedores] = await Promise.all([fuente.predicciones(), fuente.proveedores().catch(() => [])]);
        const telefonos = new Map(proveedores.map(p => [p.id, p.telefono]));
        const productos = productosParaSolicitar(predicciones);
        const grupos = agruparPorProveedor(productos);
        mostrarTotal('solicitar', productos.length,
            productos.length === 1 ? 'producto para reponer ya' : 'productos para reponer ya');
        if (!grupos.length) {
            contenedor.innerHTML = '<p class="tw-mb-0 text-muted">No hay productos para reponer por ahora.</p>';
            return;
        }
        contenedor.innerHTML = grupos.map((grupo, i) => renderGrupoProveedor(grupo, telefonos.get(grupo.proveedorId), i)).join('');
        contenedor.querySelectorAll('[data-copiar]').forEach(boton => boton.addEventListener('click', () =>
            copiarPedido(grupos[Number(boton.dataset.copiar)], boton)));
    } catch (e) {
        mostrarTotal('solicitar', '—', 'no se pudo calcular');
        contenedor.innerHTML = `<p class="tw-mb-0 text-danger">No se pudieron cargar las predicciones: ${escaparHtml(e.message)}</p>`;
    }
}

function renderGrupoProveedor(grupo, telefono, indice) {
    const titulo = grupo.proveedor ? escaparHtml(grupo.proveedor) : 'Sin proveedor asignado';
    const contacto = telefono ? `Tel. ${escaparHtml(telefono)}` : '';
    const filas = grupo.productos.map(p => `
        <tr>
            <td><div class="tw-font-semibold">${escaparHtml(p.nombre)}</div><div class="tw-text-xs text-muted">${escaparHtml(p.sku)}</div></td>
            <td class="text-end">${p.stock_actual}</td>
            <td class="text-end">${p.punto_reposicion ?? '-'}</td>
            <td class="text-end">${p.fecha_quiebre_estimada ? formatoFecha(p.fecha_quiebre_estimada) : '-'}</td>
            <td class="text-end tw-font-semibold">${p.lote_sugerido}</td>
        </tr>`).join('');
    return `
        <div class="tw-mb-4 tw-rounded-lg tw-border tw-p-3">
            <div class="tw-mb-2 tw-flex tw-flex-wrap tw-items-center tw-justify-between tw-gap-2">
                <div><span class="tw-font-semibold">${titulo}</span> <span class="tw-text-sm text-muted">${contacto}</span></div>
                ${grupo.proveedorId !== null ? `<button type="button" class="btn btn-sm btn-outline-secondary" data-copiar="${indice}">Copiar pedido</button>` : ''}
            </div>
            <div class="table-responsive">
                <table class="table table-sm align-middle mb-0">
                    <thead>
                        <tr>
                            <th>Producto</th>
                            <th class="text-end">Stock actual</th>
                            <th class="text-end">Punto de reposición</th>
                            <th class="text-end">Se agota</th>
                            <th class="text-end">Pedir</th>
                        </tr>
                    </thead>
                    <tbody class="tw-tabular-nums">${filas}</tbody>
                </table>
            </div>
        </div>`;
}

async function copiarPedido(grupo, boton) {
    const texto = textoPedidoProveedor(grupo);
    try {
        await navigator.clipboard.writeText(texto);
        boton.textContent = 'Copiado';
        setTimeout(() => { boton.textContent = 'Copiar pedido'; }, 2000);
    } catch {
        window.prompt('Copiá el pedido:', texto);
    }
}

// --- Intentar vender y retirar vencidos ---

async function cargarVencimientos() {
    try {
        const vencimientos = await fuente.vencimientos();
        paraVender = lotesParaVender(vencimientos);
        paraRetirar = lotesParaRetirar(vencimientos);
        mostrarTotal('vender', paraVender.length, `lotes por vencer · ${formatoMoneda(valorEnRiesgo(paraVender))} en riesgo`);
        mostrarTotal('retirar', paraRetirar.length, `lotes vencidos · ${formatoMoneda(valorEnRiesgo(paraRetirar))}`);
        renderVender();
        renderRetirar();
    } catch (e) {
        for (const nombre of ['vender', 'retirar']) {
            mostrarTotal(nombre, '—', 'no se pudo calcular');
            document.getElementById(`tabla-${nombre}`).innerHTML =
                `<tr><td colspan="5" class="text-danger">No se pudieron cargar los vencimientos: ${escaparHtml(e.message)}</td></tr>`;
        }
    }
}

function celdaProducto(lote) {
    return `<td><div class="tw-font-semibold">${escaparHtml(lote.producto)}</div>
                <div class="tw-text-xs text-muted">${escaparHtml(lote.sku)} · lote ${lote.lote_id}</div></td>`;
}

function celdaVencimiento(lote) {
    return `<td>${formatoFecha(lote.fecha_vencimiento)}<div class="tw-text-xs text-muted">${textoVencimiento(lote.dias_restantes)}</div></td>`;
}

function renderVender() {
    const cuerpo = document.getElementById('tabla-vender');
    if (!paraVender.length) {
        cuerpo.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No hay lotes por vencer.</td></tr>';
        return;
    }
    cuerpo.innerHTML = paraVender.map(l => `
        <tr>
            ${celdaProducto(l)}
            ${celdaVencimiento(l)}
            <td class="text-end">${l.cantidad_disponible}</td>
            <td class="text-end">${formatoMoneda(l.valor_en_riesgo)}</td>
            <td><span class="badge ${CLASE_CRITICIDAD[l.criticidad] ?? 'text-bg-secondary'}">${escaparHtml(l.criticidad)}</span></td>
        </tr>`).join('');
}

function renderRetirar() {
    const cuerpo = document.getElementById('tabla-retirar');
    if (!paraRetirar.length) {
        cuerpo.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No hay lotes vencidos.</td></tr>';
        return;
    }
    cuerpo.innerHTML = paraRetirar.map(l => `
        <tr>
            ${celdaProducto(l)}
            ${celdaVencimiento(l)}
            <td class="text-end">${l.cantidad_disponible}</td>
            <td class="text-end">${formatoMoneda(l.valor_en_riesgo)}</td>
            <td class="text-end"><button type="button" class="btn btn-sm btn-outline-danger" data-retirar="${l.lote_id}">Registrar retiro</button></td>
        </tr>`).join('');
    cuerpo.querySelectorAll('[data-retirar]').forEach(boton => boton.addEventListener('click', () =>
        abrirRetiro(paraRetirar.find(l => l.lote_id === Number(boton.dataset.retirar)))));
}

function abrirRetiro(lote) {
    document.getElementById('retiro-lote-id').value = lote.lote_id;
    document.getElementById('retiro-observacion').value = '';
    document.getElementById('retiro-resumen').innerHTML =
        `Vas a retirar <strong>${lote.cantidad_disponible} unidades</strong> de <strong>${escaparHtml(lote.producto)}</strong> ` +
        `(lote ${lote.lote_id}, ${textoVencimiento(lote.dias_restantes).toLowerCase()}).`;
    modalRetiro.show();
}

async function confirmarRetiro(evento) {
    evento.preventDefault();
    const boton = document.getElementById('btn-confirmar-retiro');
    const loteId = Number(document.getElementById('retiro-lote-id').value);
    const observacion = document.getElementById('retiro-observacion').value.trim();
    boton.disabled = true;
    try {
        await fuente.retirar(loteId, observacion);
        modalRetiro.hide();
        mostrarMensaje(`Retiro del lote ${loteId} registrado. El stock ya quedó descontado.`);
        await Promise.all([cargarVencimientos(), cargarSolicitudes()]);
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    } finally {
        boton.disabled = false;
    }
}

if (sesion) iniciar();
