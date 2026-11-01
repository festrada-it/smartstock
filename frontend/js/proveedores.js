const sesion = Sesion.requerir(['ADMIN', 'VENDEDOR']);
const esAdmin = Sesion.esAdmin();

const CALIDADES = {
    BUENA: { texto: 'Buena', clase: 'text-bg-success' },
    REGULAR: { texto: 'Regular', clase: 'text-bg-warning' },
    MALA: { texto: 'Mala', clase: 'text-bg-danger' },
};

// API de core-service, contrato en docs/API-PROVEEDORES.md.
const fuente = {
    listar: () => coreApi('/proveedores'),
    productos: id => coreApi(`/proveedores/${id}/productos`),
    crear: datos => coreApi('/proveedores', { method: 'POST', body: datos }),
    actualizar: (id, datos) => coreApi(`/proveedores/${id}`, { method: 'PUT', body: datos }),
    eliminar: id => coreApi(`/proveedores/${id}`, { method: 'DELETE' }),
};

let proveedores = [];
let modalProveedor;
let modalDetalle;
const detalleAbierto = crearVigente();

async function iniciar() {
    renderLayout(sesion);
    modalProveedor = new bootstrap.Modal('#modal-proveedor');
    modalDetalle = new bootstrap.Modal('#modal-detalle');

    if (esAdmin) document.getElementById('btn-nuevo').classList.remove('d-none');

    document.getElementById('btn-nuevo').addEventListener('click', () => abrirModalProveedor());
    document.getElementById('filtro-texto').addEventListener('input', renderTabla);
    document.getElementById('filtro-calidad').addEventListener('change', renderTabla);
    document.getElementById('form-proveedor').addEventListener('submit', guardarProveedor);

    await cargarProveedores();
}

async function cargarProveedores() {
    try {
        proveedores = await fuente.listar();
        renderTabla();
    } catch (e) {
        document.getElementById('tabla-proveedores').innerHTML =
            '<tr><td colspan="6" class="text-center text-muted">No se pudieron cargar los proveedores.</td></tr>';
        mostrarMensaje(`No se pudieron cargar los proveedores: ${e.message}`, 'danger');
    }
}

/** Clasificación de 1 a 5 como estrellas, con texto alternativo para lectores de pantalla. */
function estrellas(clasificacion) {
    const llenas = '★'.repeat(clasificacion);
    const vacias = '☆'.repeat(5 - clasificacion);
    return `<span class="tw-whitespace-nowrap tw-text-amber-500" role="img" aria-label="${clasificacion} de 5 estrellas"
                  title="${clasificacion} de 5">${llenas}<span class="tw-text-slate-300">${vacias}</span></span>`;
}

function badgeCalidad(calidad) {
    const c = CALIDADES[calidad] ?? { texto: escaparHtml(calidad), clase: 'text-bg-secondary' };
    return `<span class="badge ${c.clase}">${c.texto}</span>`;
}

/** Proveedor al que conviene pedirle con más anticipación (mismo criterio que usará analytics, #30). */
function esDeRiesgo(proveedor) {
    return proveedor.clasificacion <= 2 || proveedor.calidad === 'MALA';
}

function renderTabla() {
    const texto = document.getElementById('filtro-texto').value.trim().toLowerCase();
    const calidad = document.getElementById('filtro-calidad').value;

    const filtrados = proveedores
        .filter(p => (!texto || p.nombre.toLowerCase().includes(texto)) && (!calidad || p.calidad === calidad))
        .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));

    const cuerpo = document.getElementById('tabla-proveedores');
    if (!filtrados.length) {
        const mensaje = proveedores.length ? 'No hay proveedores que coincidan.' : 'Todavía no hay proveedores cargados.';
        cuerpo.innerHTML = `<tr><td colspan="6" class="text-center text-muted">${mensaje}</td></tr>`;
        return;
    }
    cuerpo.innerHTML = filtrados.map(p => `
        <tr>
            <td>
                <div class="tw-font-semibold">${escaparHtml(p.nombre)}</div>
                <div class="tw-text-xs tw-text-slate-500">${escaparHtml(p.direccion || '-')}</div>
            </td>
            <td class="tw-whitespace-nowrap">${escaparHtml(p.telefono || '-')}</td>
            <td class="text-end tw-whitespace-nowrap">
                ${p.leadTimeDias} ${p.leadTimeDias === 1 ? 'día' : 'días'}
                ${esDeRiesgo(p) ? '<span class="tw-ml-1" title="Reponer con más anticipación">⚠️</span>' : ''}
            </td>
            <td>${badgeCalidad(p.calidad)}</td>
            <td>${estrellas(p.clasificacion)}</td>
            <td class="text-end tw-whitespace-nowrap">
                <button class="btn btn-sm btn-outline-primary" data-accion="ver" data-id="${p.id}">Ver productos</button>
                ${esAdmin ? `
                    <button class="btn btn-sm btn-outline-secondary" data-accion="editar" data-id="${p.id}">Editar</button>
                    <button class="btn btn-sm btn-outline-danger" data-accion="eliminar" data-id="${p.id}">Eliminar</button>` : ''}
            </td>
        </tr>`).join('');

    cuerpo.querySelectorAll('[data-accion]').forEach(boton => boton.addEventListener('click', () => {
        const proveedor = proveedores.find(p => p.id === Number(boton.dataset.id));
        if (boton.dataset.accion === 'ver') abrirDetalle(proveedor);
        if (boton.dataset.accion === 'editar') abrirModalProveedor(proveedor);
        if (boton.dataset.accion === 'eliminar') eliminarProveedor(proveedor);
    }));
}

async function abrirDetalle(proveedor) {
    detalleAbierto.abrir(proveedor.id);
    document.getElementById('detalle-nombre').textContent = proveedor.nombre;
    document.getElementById('detalle-datos').innerHTML = `
        <dt class="col-sm-4">Dirección</dt><dd class="col-sm-8">${escaparHtml(proveedor.direccion || '-')}</dd>
        <dt class="col-sm-4">Teléfono</dt><dd class="col-sm-8">${escaparHtml(proveedor.telefono || '-')}</dd>
        <dt class="col-sm-4">Entrega promedio</dt><dd class="col-sm-8">${proveedor.leadTimeDias} días</dd>
        <dt class="col-sm-4">Calidad</dt><dd class="col-sm-8">${badgeCalidad(proveedor.calidad)}</dd>
        <dt class="col-sm-4">Clasificación</dt><dd class="col-sm-8">${estrellas(proveedor.clasificacion)}</dd>
        ${esDeRiesgo(proveedor) ? `<dd class="col-12 tw-mb-0"><div class="alert alert-warning tw-mb-0 tw-mt-2 tw-py-2">
            ⚠️ Proveedor de riesgo: conviene pedirle con más anticipación.</div></dd>` : ''}`;
    const marcas = document.getElementById('detalle-marcas');
    const cuerpo = document.getElementById('detalle-productos');
    marcas.innerHTML = '';
    cuerpo.innerHTML = '<tr><td colspan="3" class="text-center text-muted">Cargando...</td></tr>';
    modalDetalle.show();

    try {
        const productos = await fuente.productos(proveedor.id);
        if (!detalleAbierto.esActual(proveedor.id)) return; // ya se abrió otro proveedor
        const listaMarcas = [...new Set(productos.map(p => p.marca).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es'));
        marcas.innerHTML = listaMarcas.length
            ? listaMarcas.map(m => `<span class="badge rounded-pill text-bg-light tw-border">${escaparHtml(m)}</span>`).join('')
            : '<span class="text-muted tw-text-sm">Sin marcas registradas.</span>';
        cuerpo.innerHTML = productos.length
            ? productos.map(p => `
                <tr>
                    <td class="tw-font-mono">${escaparHtml(p.sku)}</td>
                    <td>${escaparHtml(p.nombre)}</td>
                    <td>${escaparHtml(p.marca || '-')}</td>
                </tr>`).join('')
            : '<tr><td colspan="3" class="text-center text-muted">Este proveedor todavía no abastece productos.</td></tr>';
    } catch (e) {
        if (!detalleAbierto.esActual(proveedor.id)) return;
        cuerpo.innerHTML = `<tr><td colspan="3" class="text-center text-danger">${escaparHtml(e.message)}</td></tr>`;
    }
}

function abrirModalProveedor(proveedor = null) {
    document.getElementById('titulo-modal-proveedor').textContent = proveedor ? 'Editar proveedor' : 'Nuevo proveedor';
    document.getElementById('proveedor-id').value = proveedor?.id ?? '';
    document.getElementById('proveedor-nombre').value = proveedor?.nombre ?? '';
    document.getElementById('proveedor-direccion').value = proveedor?.direccion ?? '';
    document.getElementById('proveedor-telefono').value = proveedor?.telefono ?? '';
    document.getElementById('proveedor-lead-time').value = proveedor?.leadTimeDias ?? 7;
    document.getElementById('proveedor-calidad').value = proveedor?.calidad ?? 'BUENA';
    document.getElementById('proveedor-clasificacion').value = proveedor?.clasificacion ?? 3;
    modalProveedor.show();
}

async function guardarProveedor(evento) {
    evento.preventDefault();
    const id = document.getElementById('proveedor-id').value;
    const datos = {
        nombre: document.getElementById('proveedor-nombre').value.trim(),
        direccion: document.getElementById('proveedor-direccion').value.trim() || null,
        telefono: document.getElementById('proveedor-telefono').value.trim() || null,
        leadTimeDias: Number(document.getElementById('proveedor-lead-time').value),
        calidad: document.getElementById('proveedor-calidad').value,
        clasificacion: Number(document.getElementById('proveedor-clasificacion').value),
    };
    try {
        if (id) await fuente.actualizar(id, datos);
        else await fuente.crear(datos);
        modalProveedor.hide();
        mostrarMensaje(id ? 'Proveedor actualizado' : 'Proveedor creado');
        await cargarProveedores();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

async function eliminarProveedor(proveedor) {
    if (!confirm(`¿Dar de baja a "${proveedor.nombre}"? Sus productos y su historial se conservan.`)) return;
    try {
        await fuente.eliminar(proveedor.id);
        mostrarMensaje('Proveedor dado de baja');
        await cargarProveedores();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

if (sesion) iniciar();
