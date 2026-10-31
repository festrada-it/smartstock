const DIAS_CRITICOS = 7;

async function cargarPredicciones() {
    try {
        const respuesta = await fetch(`${CONFIG.ANALYTICS_API}/predicciones`);
        if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`);
        const predicciones = await respuesta.json();
        renderKpis(predicciones);
        renderTabla(predicciones);
    } catch (error) {
        const alerta = document.getElementById('alerta-error');
        alerta.textContent = `No se pudo conectar con el servicio de analítica (${error.message}).`;
        alerta.classList.remove('d-none');
        document.getElementById('tabla-predicciones').innerHTML = '';
    }
}

function renderKpis(predicciones) {
    const criticos = predicciones.filter(p => p.dias_hasta_quiebre !== null && p.dias_hasta_quiebre <= DIAS_CRITICOS);
    const bajoMinimo = predicciones.filter(p => p.stock_actual <= p.stock_minimo);
    document.getElementById('kpi-total').textContent = predicciones.length;
    document.getElementById('kpi-criticos').textContent = criticos.length;
    document.getElementById('kpi-bajo-minimo').textContent = bajoMinimo.length;
}

function renderTabla(predicciones) {
    const filas = predicciones.map(p => {
        const dias = p.dias_hasta_quiebre;
        const critico = dias !== null && dias <= DIAS_CRITICOS;
        return `
            <tr class="${critico ? 'table-danger' : ''}">
                <td class="tw-font-mono">${p.sku}</td>
                <td>${p.nombre}</td>
                <td class="text-end">${p.stock_actual}</td>
                <td class="text-end">${p.demanda_diaria_estimada ?? '-'}</td>
                <td class="text-end fw-bold">${dias ?? '> 60'}</td>
                <td>${p.fecha_quiebre_estimada ?? '-'}</td>
            </tr>`;
    });
    document.getElementById('tabla-predicciones').innerHTML = filas.join('');
}

cargarPredicciones();
