// Panel unificado de alertas del dashboard (#33). Consume GET /api/alertas/panel de analytics
// (contrato en docs/PANEL-ALERTAS.md) y usa RIESGO y seleccionarProducto() de dashboard.js.

const TIPOS_ALERTA = {
    QUIEBRE: { icono: '📉', texto: 'Quiebre de stock' },
    REPOSICION: { icono: '🛒', texto: 'Comprar ahora' },
    STOCK_MINIMO: { icono: '📦', texto: 'Debajo del mínimo' },
    VENCIMIENTO: { icono: '⏰', texto: 'Próximo a vencer' },
    BAJA_ROTACION: { icono: '🐢', texto: 'Baja rotación' },
};

// [singular, plural] para que concuerde con "criticidad".
const ETIQUETAS_CRITICIDAD = { ALTA: ['Alta', 'altas'], MEDIA: ['Media', 'medias'], BAJA: ['Baja', 'bajas'] };

const AlertasPanelAnalytics = {
    panel: ({ tipo = null, criticidad = null } = {}) => {
        const parametros = new URLSearchParams();
        if (tipo) parametros.set('tipo', tipo);
        if (criticidad) parametros.set('criticidad', criticidad);
        const consulta = parametros.toString();
        return analyticsApi(`/alertas/panel${consulta ? `?${consulta}` : ''}`);
    },
};
const fuenteAlertas = CONFIG.DATOS_EJEMPLO?.panelAlertas ? AlertasPanelEjemplo : AlertasPanelAnalytics;

async function iniciarPanelAlertas() {
    if (fuenteAlertas === AlertasPanelEjemplo) document.getElementById('aviso-ejemplo-alertas').classList.remove('d-none');
    document.getElementById('filtro-tipo-alerta').insertAdjacentHTML('beforeend', Object.entries(TIPOS_ALERTA)
        .map(([clave, t]) => `<option value="${clave}">${t.icono} ${t.texto}</option>`).join(''));
    document.getElementById('filtro-tipo-alerta').addEventListener('change', cargarPanel);
    document.getElementById('filtro-criticidad-alerta').addEventListener('change', cargarPanel);

    // Los contadores muestran el total sin filtrar; la lista respeta los filtros.
    try {
        renderContadores(await fuenteAlertas.panel());
    } catch (e) {
        document.getElementById('contadores-alertas').innerHTML = '';
    }
    await cargarPanel();
}

async function cargarPanel() {
    const contenedor = document.getElementById('panel-alertas');
    const tipo = document.getElementById('filtro-tipo-alerta').value || null;
    const criticidad = document.getElementById('filtro-criticidad-alerta').value || null;
    try {
        renderPanel(await fuenteAlertas.panel({ tipo, criticidad }), Boolean(tipo || criticidad));
    } catch (e) {
        contenedor.innerHTML = `<div class="alert alert-danger tw-mb-0 tw-py-2">No se pudo cargar el panel de alertas: ${escaparHtml(e.message)}</div>`;
    }
}

function renderContadores(alertas) {
    const porTipo = Object.keys(TIPOS_ALERTA).map(clave => [clave, alertas.filter(a => a.tipo === clave).length]);
    const porCriticidad = ['ALTA', 'MEDIA', 'BAJA'].map(c => [c, alertas.filter(a => a.criticidad === c).length]);
    document.getElementById('contadores-alertas').innerHTML = `
        ${porCriticidad.map(([c, n]) => `
            <span class="badge rounded-pill tw-border tw-px-3 tw-py-2 ${RIESGO[c].fondo} tw-text-slate-800" style="border-color:${RIESGO[c].color}">
                ${RIESGO[c].icono} ${n} ${ETIQUETAS_CRITICIDAD[c][1]}</span>`).join('')}
        <span class="tw-mx-1 tw-border-l"></span>
        ${porTipo.map(([clave, n]) => `
            <button type="button" class="btn btn-sm btn-outline-secondary" data-tipo="${clave}" ${n ? '' : 'disabled'}>
                ${TIPOS_ALERTA[clave].icono} ${TIPOS_ALERTA[clave].texto} <span class="badge text-bg-secondary">${n}</span>
            </button>`).join('')}`;
    // Un click en el contador filtra por ese tipo (otro click vuelve a mostrar todos).
    document.querySelectorAll('#contadores-alertas [data-tipo]').forEach(boton => boton.addEventListener('click', () => {
        const filtro = document.getElementById('filtro-tipo-alerta');
        filtro.value = filtro.value === boton.dataset.tipo ? '' : boton.dataset.tipo;
        cargarPanel();
    }));
}

function renderPanel(alertas, hayFiltros) {
    const contenedor = document.getElementById('panel-alertas');
    if (!alertas.length) {
        contenedor.innerHTML = `<div class="tw-text-sm tw-text-slate-500">${hayFiltros ? 'No hay alertas con esos filtros.' : '✅ Sin alertas: todo en orden.'}</div>`;
        return;
    }
    contenedor.innerHTML = alertas.map(a => {
        const r = RIESGO[a.criticidad] ?? RIESGO.MEDIA;
        const t = TIPOS_ALERTA[a.tipo] ?? { icono: '🔔', texto: a.tipo };
        return `
            <div class="tw-flex tw-flex-wrap tw-items-center tw-justify-between tw-gap-2 tw-rounded-lg tw-border tw-border-solid tw-border-slate-200 tw-p-3 ${r.fondo}"
                 style="border-left: 4px solid ${r.color}">
                <div>
                    <div class="tw-text-sm tw-font-semibold">
                        <span class="${r.texto}">${r.icono} ${ETIQUETAS_CRITICIDAD[a.criticidad]?.[0] ?? escaparHtml(a.criticidad)}</span> · ${t.icono} ${escaparHtml(t.texto)} · ${escaparHtml(a.producto)}
                    </div>
                    <div class="tw-text-xs tw-text-slate-600">${escaparHtml(a.detalle)}</div>
                </div>
                <div class="tw-flex tw-items-center tw-gap-2">
                    <span class="tw-text-sm tw-font-semibold">→ ${escaparHtml(a.accion_sugerida)}</span>
                    <button type="button" class="btn btn-sm btn-outline-primary" data-producto="${a.producto_id}">Ver producto</button>
                </div>
            </div>`;
    }).join('');
    contenedor.querySelectorAll('[data-producto]').forEach(boton => boton.addEventListener('click', () => verProducto(Number(boton.dataset.producto))));
}

/** Lleva al gráfico de proyección del producto, si el monitor lo tiene. */
async function verProducto(productoId) {
    const existe = [...document.getElementById('selector-producto').options].some(o => Number(o.value) === productoId);
    if (!existe) {
        mostrarMensaje('Ese producto todavía no está en el monitor predictivo.', 'warning');
        return;
    }
    await seleccionarProducto(productoId);
    document.getElementById('grafico-stock').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

if (sesion) iniciarPanelAlertas();
