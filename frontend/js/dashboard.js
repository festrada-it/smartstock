const sesion = Sesion.requerir(['ADMIN']);

// Colores de los gráficos (paleta validada para daltonismo y contraste)
const COLORES = {
    serie1: '#2a78d6',      // stock / ventas reales
    serie2: '#eb6834',      // pronóstico de demanda
    critico: '#d03b3b',     // umbral mínimo y riesgo alto
    advertencia: '#fab219', // riesgo medio
    grilla: '#e1e0d9',
    texto: '#898781',
};

const RIESGO = {
    ALTA: { icono: '⛔', etiqueta: 'Alto', color: COLORES.critico, fondo: 'tw-bg-red-50', texto: 'text-danger' },
    MEDIA: { icono: '⚠️', etiqueta: 'Medio', color: COLORES.advertencia, fondo: 'tw-bg-amber-50', texto: 'tw-text-amber-700' },
    BAJA: { icono: '✅', etiqueta: 'Bajo', color: '#0ca30c', fondo: '', texto: 'text-success' },
};

let graficoStock = null;
let graficoTendencia = null;

Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';
Chart.defaults.color = COLORES.texto;

async function iniciar() {
    renderLayout(sesion);
    try {
        const [resumen, predicciones] = await Promise.all([analyticsApi('/resumen'), analyticsApi('/predicciones')]);
        renderResumen(resumen);
        renderAlertas(predicciones.filter(p => p.criticidad !== 'BAJA'));
        renderTabla(predicciones);
        renderSelector(predicciones);
        if (predicciones.length) await seleccionarProducto(predicciones[0].id);
    } catch (e) {
        mostrarMensaje(`No se pudo cargar el monitor predictivo: ${e.message}`, 'danger');
    }
}

function renderResumen(r) {
    document.getElementById('kpi-valor').textContent = formatoMoneda(r.valor_total_stock);
    document.getElementById('kpi-productos').textContent = r.productos_activos;
    document.getElementById('kpi-alta').textContent = r.alertas_alta;
    document.getElementById('kpi-media').textContent = r.alertas_media;
    document.getElementById('kpi-ingresos').textContent = formatoMoneda(r.ingresos_30_dias);
    document.getElementById('kpi-unidades').textContent = `${r.unidades_vendidas_30_dias} unidades`;
}

function renderAlertas(alertas) {
    const contenedor = document.getElementById('lista-alertas');
    if (!alertas.length) {
        contenedor.innerHTML = '<div class="tw-text-sm tw-text-slate-500">✅ Sin riesgos de quiebre en el horizonte.</div>';
        return;
    }
    contenedor.innerHTML = alertas.map(p => {
        const r = RIESGO[p.criticidad];
        const cuando = p.dias_hasta_quiebre === 0 ? 'sin stock' : `${p.dias_hasta_quiebre ?? '?'} días`;
        return `
            <button type="button" data-producto="${p.id}"
                    class="tw-w-full tw-rounded-lg tw-border tw-border-solid tw-border-slate-200 tw-p-3 tw-text-left ${r.fondo}"
                    style="border-left: 4px solid ${r.color}">
                <div class="tw-text-sm tw-font-semibold">${r.icono} Riesgo ${r.etiqueta.toLowerCase()}: ${escaparHtml(p.nombre)} · ${cuando}</div>
                <div class="tw-text-xs tw-text-slate-600">Sugerencia: reponer ${p.lote_sugerido} u.</div>
            </button>`;
    }).join('');
    contenedor.querySelectorAll('[data-producto]').forEach(boton =>
        boton.addEventListener('click', () => seleccionarProducto(Number(boton.dataset.producto))));
}

function renderTabla(predicciones) {
    document.getElementById('tabla-predicciones').innerHTML = predicciones.map(p => {
        const r = RIESGO[p.criticidad];
        return `
            <tr>
                <td class="${r.texto} tw-whitespace-nowrap">${r.icono} ${r.etiqueta}</td>
                <td class="tw-font-mono">${escaparHtml(p.sku)}</td>
                <td>${escaparHtml(p.nombre)}</td>
                <td class="text-end">${p.stock_actual}</td>
                <td class="text-end">${p.stock_minimo}</td>
                <td class="text-end">${p.demanda_diaria_estimada ?? '-'}</td>
                <td class="text-end fw-semibold">${p.dias_hasta_quiebre ?? '> 60'}</td>
                <td>${formatoFecha(p.fecha_quiebre_estimada)}</td>
                <td class="text-end fw-semibold">${p.lote_sugerido > 0 ? `${p.lote_sugerido} u.` : '-'}</td>
            </tr>`;
    }).join('');
}

function renderSelector(predicciones) {
    const selector = document.getElementById('selector-producto');
    selector.innerHTML = predicciones.map(p => `<option value="${p.id}">${escaparHtml(p.nombre)}</option>`).join('');
    selector.addEventListener('change', () => seleccionarProducto(Number(selector.value)));
}

async function seleccionarProducto(id) {
    document.getElementById('selector-producto').value = id;
    const [proyeccion, tendencia] = await Promise.all([
        analyticsApi(`/predicciones/${id}/proyeccion?dias_historico=30&dias_futuro=15`),
        analyticsApi(`/predicciones/${id}/tendencia?dias_historico=30&dias_futuro=15`),
    ]);
    const nombre = proyeccion.producto.nombre;
    document.getElementById('titulo-producto').textContent = nombre;
    document.getElementById('titulo-tendencia').textContent = nombre;
    renderGraficoStock(proyeccion);
    renderGraficoTendencia(tendencia);
}

function etiquetaDia(iso) {
    const [, mes, dia] = iso.split('-');
    return `${dia}/${mes}`;
}

function opcionesBase() {
    return {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },  // crosshair: tooltip con todas las series del día
        plugins: {
            legend: { position: 'bottom', labels: { usePointStyle: true, pointStyle: 'line', boxWidth: 24 } },
            tooltip: { backgroundColor: '#0b0b0b', padding: 10 },
        },
        scales: {
            x: { grid: { display: false }, ticks: { maxTicksLimit: 10 } },
            y: { beginAtZero: true, grid: { color: COLORES.grilla }, border: { display: false } },
        },
        elements: {
            line: { borderWidth: 2, tension: 0.25 },
            point: { radius: 0, hoverRadius: 5, hitRadius: 12 },
        },
    };
}

function renderGraficoStock({ producto, historico, proyeccion }) {
    const n = historico.length;
    const etiquetas = [...historico, ...proyeccion].map(p => etiquetaDia(p.fecha));
    // La proyección arranca en el último punto real para que la línea sea continua
    const datosHistorico = [...historico.map(p => p.valor), ...proyeccion.map(() => null)];
    const datosProyeccion = [...historico.map((_, i) => (i === n - 1 ? historico[i].valor : null)), ...proyeccion.map(p => p.valor)];

    graficoStock?.destroy();
    graficoStock = new Chart(document.getElementById('grafico-stock'), {
        type: 'line',
        data: {
            labels: etiquetas,
            datasets: [
                { label: 'Stock histórico', data: datosHistorico, borderColor: COLORES.serie1, backgroundColor: COLORES.serie1 },
                { label: 'Stock proyectado', data: datosProyeccion, borderColor: COLORES.serie1, backgroundColor: COLORES.serie1, borderDash: [6, 4] },
                { label: `Stock mínimo (${producto.stock_minimo} u.)`, data: etiquetas.map(() => producto.stock_minimo),
                  borderColor: COLORES.critico, backgroundColor: COLORES.critico, borderWidth: 1, pointHoverRadius: 0, tension: 0 },
            ],
        },
        options: opcionesBase(),
    });

    const nota = document.getElementById('nota-quiebre');
    nota.textContent = producto.dias_hasta_quiebre === null
        ? 'Con la tendencia actual no se espera quiebre en los próximos 60 días.'
        : `Quiebre estimado en ${producto.dias_hasta_quiebre} días (${formatoFecha(producto.fecha_quiebre_estimada)}). `
          + `Compra sugerida: ${producto.lote_sugerido} u.`;
}

function renderGraficoTendencia({ ventas, pronostico }) {
    const etiquetas = [...ventas, ...pronostico].map(p => etiquetaDia(p.fecha));

    graficoTendencia?.destroy();
    graficoTendencia = new Chart(document.getElementById('grafico-tendencia'), {
        type: 'line',
        data: {
            labels: etiquetas,
            datasets: [
                { label: 'Unidades vendidas', data: [...ventas.map(p => p.valor), ...pronostico.map(() => null)],
                  borderColor: COLORES.serie1, backgroundColor: COLORES.serie1 },
                { label: 'Pronóstico de demanda', data: [...ventas.map(() => null), ...pronostico.map(p => p.valor)],
                  borderColor: COLORES.serie2, backgroundColor: COLORES.serie2, borderDash: [6, 4] },
            ],
        },
        options: opcionesBase(),
    });
}

if (sesion) iniciar();
