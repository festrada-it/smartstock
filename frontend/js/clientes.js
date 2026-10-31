const sesion = Sesion.requerir(['ADMIN', 'VENDEDOR']);
const esAdmin = Sesion.esAdmin();
const TAMANIO_PAGINA = 10;

let clientes = [];
let modalCliente;
let modalDetalle;
// Paginación del historial del cliente abierto en el detalle.
let detalle = { clienteId: null, pagina: 0, totalPaginas: 0 };

async function iniciar() {
    renderLayout(sesion);
    modalCliente = new bootstrap.Modal('#modal-cliente');
    modalDetalle = new bootstrap.Modal('#modal-detalle');

    if (fuenteClientes === ClientesEjemplo) document.getElementById('aviso-ejemplo').classList.remove('d-none');
    if (esAdmin) document.getElementById('btn-nuevo').classList.remove('d-none');

    document.getElementById('btn-nuevo').addEventListener('click', () => abrirModalCliente());
    document.getElementById('filtro-texto').addEventListener('input', renderTabla);
    document.getElementById('filtro-calidad').addEventListener('change', renderTabla);
    document.getElementById('form-cliente').addEventListener('submit', guardarCliente);
    document.getElementById('btn-mas-pedidos').addEventListener('click', () => cargarPedidos(detalle.pagina + 1));

    await cargarClientes();
}

async function cargarClientes() {
    try {
        clientes = await fuenteClientes.listar();
        renderTabla();
    } catch (e) {
        document.getElementById('tabla-clientes').innerHTML =
            '<tr><td colspan="5" class="text-center text-muted">No se pudieron cargar los clientes.</td></tr>';
        mostrarMensaje(`No se pudieron cargar los clientes: ${e.message}`, 'danger');
    }
}

function renderTabla() {
    const texto = document.getElementById('filtro-texto').value.trim().toLowerCase();
    const calidad = document.getElementById('filtro-calidad').value;
    const filtrados = clientes.filter(c => (!texto || c.nombre.toLowerCase().includes(texto)) && (!calidad || c.calidad === calidad));

    const cuerpo = document.getElementById('tabla-clientes');
    if (!filtrados.length) {
        const mensaje = clientes.length ? 'No hay clientes que coincidan.' : 'Todavía no hay clientes cargados.';
        cuerpo.innerHTML = `<tr><td colspan="5" class="text-center text-muted">${mensaje}</td></tr>`;
        return;
    }
    cuerpo.innerHTML = filtrados.map(c => `
        <tr>
            <td>
                <div class="tw-font-semibold">${escaparHtml(c.nombre)}
                    ${esDeRiesgo(c) ? '<span class="tw-ml-1" title="Venderle con cuidado">⚠️</span>' : ''}</div>
                <div class="tw-text-xs tw-text-slate-500">${escaparHtml(c.direccion || '-')}</div>
            </td>
            <td class="tw-whitespace-nowrap">${escaparHtml(c.telefono || '-')}</td>
            <td>${badgeCalidad(c.calidad)}</td>
            <td>${estrellas(c.clasificacion)}</td>
            <td class="text-end tw-whitespace-nowrap">
                <button class="btn btn-sm btn-outline-primary" data-accion="ver" data-id="${c.id}">Historial</button>
                ${esAdmin ? `
                    <button class="btn btn-sm btn-outline-secondary" data-accion="editar" data-id="${c.id}">Editar</button>
                    ${esConsumidorFinal(c) ? '' : `<button class="btn btn-sm btn-outline-danger" data-accion="eliminar" data-id="${c.id}">Eliminar</button>`}` : ''}
            </td>
        </tr>`).join('');

    cuerpo.querySelectorAll('[data-accion]').forEach(boton => boton.addEventListener('click', () => {
        const cliente = clientes.find(c => c.id === Number(boton.dataset.id));
        if (boton.dataset.accion === 'ver') abrirDetalle(cliente);
        if (boton.dataset.accion === 'editar') abrirModalCliente(cliente);
        if (boton.dataset.accion === 'eliminar') eliminarCliente(cliente);
    }));
}

async function abrirDetalle(cliente) {
    detalle = { clienteId: cliente.id, pagina: 0, totalPaginas: 0 };
    document.getElementById('detalle-nombre').textContent = cliente.nombre;
    document.getElementById('detalle-datos').innerHTML = `
        <dt class="col-sm-4">Dirección</dt><dd class="col-sm-8">${escaparHtml(cliente.direccion || '-')}</dd>
        <dt class="col-sm-4">Teléfono</dt><dd class="col-sm-8">${escaparHtml(cliente.telefono || '-')}</dd>
        <dt class="col-sm-4">Calidad</dt><dd class="col-sm-8">${badgeCalidad(cliente.calidad)}</dd>
        <dt class="col-sm-4">Clasificación</dt><dd class="col-sm-8">${estrellas(cliente.clasificacion)}</dd>`;
    document.getElementById('detalle-marcas').innerHTML = '';
    document.getElementById('detalle-productos').innerHTML = '<tr><td colspan="4" class="text-center text-muted">Cargando...</td></tr>';
    document.getElementById('detalle-pedidos').innerHTML = '<tr><td colspan="4" class="text-center text-muted">Cargando...</td></tr>';
    document.getElementById('detalle-total-pedidos').textContent = '';
    modalDetalle.show();
    await Promise.all([cargarProductosCliente(cliente.id), cargarPedidos(0)]);
}

async function cargarProductosCliente(clienteId) {
    const cuerpo = document.getElementById('detalle-productos');
    try {
        const productos = await fuenteClientes.productos(clienteId);
        const marcas = [...new Set(productos.map(p => p.marca).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es'));
        document.getElementById('detalle-marcas').innerHTML = marcas.length
            ? marcas.map(m => `<span class="badge rounded-pill text-bg-light tw-border">${escaparHtml(m)}</span>`).join('')
            : '<span class="text-muted tw-text-sm">Todavía no compró productos con marca.</span>';
        cuerpo.innerHTML = productos.length
            ? productos.map(p => `
                <tr>
                    <td>${escaparHtml(p.producto)}</td>
                    <td>${escaparHtml(p.marca || '-')}</td>
                    <td class="text-end">${p.unidadesCompradas}</td>
                    <td class="text-end">${formatoFecha(p.ultimaCompra)}</td>
                </tr>`).join('')
            : '<tr><td colspan="4" class="text-center text-muted">Todavía no compró productos.</td></tr>';
    } catch (e) {
        cuerpo.innerHTML = `<tr><td colspan="4" class="text-center text-danger">${escaparHtml(e.message)}</td></tr>`;
    }
}

async function cargarPedidos(pagina) {
    const cuerpo = document.getElementById('detalle-pedidos');
    const botonMas = document.getElementById('btn-mas-pedidos');
    try {
        const respuesta = await fuenteClientes.pedidos(detalle.clienteId, pagina, TAMANIO_PAGINA);
        detalle.pagina = respuesta.pagina;
        detalle.totalPaginas = respuesta.totalPaginas;
        const filas = respuesta.contenido.map(v => `
            <tr class="${v.anulada ? 'tw-text-slate-400' : ''}">
                <td>${v.id}</td>
                <td>${formatoFecha(v.fecha)}</td>
                <td>${v.anulada ? '<span class="badge text-bg-secondary">Anulada</span>' : '<span class="badge text-bg-success">Vigente</span>'}</td>
                <td class="text-end ${v.anulada ? 'tw-line-through' : ''}">${formatoMoneda(v.total)}</td>
            </tr>`).join('');
        if (pagina === 0) {
            cuerpo.innerHTML = filas || '<tr><td colspan="4" class="text-center text-muted">Todavía no tiene pedidos.</td></tr>';
        } else {
            cuerpo.insertAdjacentHTML('beforeend', filas);
        }
        document.getElementById('detalle-total-pedidos').textContent = `(${respuesta.totalElementos})`;
        botonMas.classList.toggle('d-none', detalle.pagina + 1 >= detalle.totalPaginas);
    } catch (e) {
        cuerpo.innerHTML = `<tr><td colspan="4" class="text-center text-danger">${escaparHtml(e.message)}</td></tr>`;
        botonMas.classList.add('d-none');
    }
}

function abrirModalCliente(cliente = null) {
    document.getElementById('titulo-modal-cliente').textContent = cliente ? 'Editar cliente' : 'Nuevo cliente';
    document.getElementById('cliente-id').value = cliente?.id ?? '';
    document.getElementById('cliente-nombre').value = cliente?.nombre ?? '';
    document.getElementById('cliente-direccion').value = cliente?.direccion ?? '';
    document.getElementById('cliente-telefono').value = cliente?.telefono ?? '';
    document.getElementById('cliente-calidad').value = cliente?.calidad ?? 'BUENA';
    document.getElementById('cliente-clasificacion').value = cliente?.clasificacion ?? 3;
    modalCliente.show();
}

async function guardarCliente(evento) {
    evento.preventDefault();
    const id = document.getElementById('cliente-id').value;
    const datos = {
        nombre: document.getElementById('cliente-nombre').value.trim(),
        direccion: document.getElementById('cliente-direccion').value.trim() || null,
        telefono: document.getElementById('cliente-telefono').value.trim() || null,
        calidad: document.getElementById('cliente-calidad').value,
        clasificacion: Number(document.getElementById('cliente-clasificacion').value),
    };
    try {
        if (id) await fuenteClientes.actualizar(id, datos);
        else await fuenteClientes.crear(datos);
        modalCliente.hide();
        mostrarMensaje(id ? 'Cliente actualizado' : 'Cliente creado');
        await cargarClientes();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

async function eliminarCliente(cliente) {
    if (!confirm(`¿Dar de baja a "${cliente.nombre}"? Sus ventas se conservan.`)) return;
    try {
        await fuenteClientes.eliminar(cliente.id);
        mostrarMensaje('Cliente dado de baja');
        await cargarClientes();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

if (sesion) iniciar();
