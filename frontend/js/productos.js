const sesion = Sesion.requerir(['ADMIN', 'VENDEDOR']);
const esAdmin = Sesion.esAdmin();

const ESTADOS = {
    SIN_STOCK: { icono: '⛔', texto: 'Sin stock', clase: 'text-danger' },
    BAJO_MINIMO: { icono: '⚠️', texto: 'Bajo mínimo', clase: 'tw-text-amber-700' },
    OK: { icono: '✅', texto: 'OK', clase: 'text-success' },
};

const STOCK_MINIMO_POR_DEFECTO = 30;
const ejemploCatalogo = Boolean(CONFIG.DATOS_EJEMPLO?.catalogo);

let productos = [];
let proveedores = [];
let modalProducto;
let modalIngreso;

async function iniciar() {
    renderLayout(sesion);
    modalProducto = new bootstrap.Modal('#modal-producto');
    modalIngreso = new bootstrap.Modal('#modal-ingreso');

    if (esAdmin) {
        document.getElementById('btn-nuevo').classList.remove('d-none');
        document.querySelectorAll('.solo-admin').forEach(el => el.classList.remove('d-none'));
    }

    if (ejemploCatalogo) document.getElementById('aviso-ejemplo').classList.remove('d-none');

    document.getElementById('btn-nuevo').addEventListener('click', () => abrirModalProducto());
    document.getElementById('filtro-texto').addEventListener('input', renderTabla);
    document.getElementById('filtro-categoria').addEventListener('change', renderTabla);
    document.getElementById('filtro-proveedor').addEventListener('change', renderTabla);
    document.getElementById('filtro-estado').addEventListener('change', renderTabla);
    document.getElementById('form-producto').addEventListener('submit', guardarProducto);
    document.getElementById('form-ingreso').addEventListener('submit', registrarIngreso);

    // Los proveedores van antes que los productos: los datos de ejemplo necesitan sus ids reales.
    await Promise.all([cargarCategorias(), cargarProveedores()]);
    await cargarProductos();
}

async function cargarProveedores() {
    try {
        proveedores = await coreApi('/proveedores');
        const opciones = proveedores.map(p => `<option value="${p.id}">${escaparHtml(p.nombre)}</option>`).join('');
        document.getElementById('filtro-proveedor').insertAdjacentHTML('beforeend', opciones);
        document.getElementById('producto-proveedor').insertAdjacentHTML('beforeend', opciones);
    } catch (e) {
        mostrarMensaje(`No se pudieron cargar los proveedores: ${e.message}`, 'warning');
    }
}

async function cargarCategorias() {
    const categorias = await coreApi('/categorias');
    const opciones = categorias.map(c => `<option value="${c.id}">${escaparHtml(c.nombre)}</option>`).join('');
    document.getElementById('filtro-categoria').insertAdjacentHTML('beforeend', opciones);
    document.getElementById('producto-categoria').insertAdjacentHTML('beforeend', opciones);
}

async function cargarProductos() {
    try {
        const respuesta = await coreApi('/productos');
        productos = ejemploCatalogo ? respuesta.map(p => CatalogoEjemplo.completar(p, proveedores)) : respuesta;
        renderTabla();
    } catch (e) {
        mostrarMensaje(`No se pudieron cargar los productos: ${e.message}`, 'danger');
    }
}

function renderTabla() {
    const texto = document.getElementById('filtro-texto').value.trim().toLowerCase();
    const categoria = document.getElementById('filtro-categoria').value;
    const proveedor = document.getElementById('filtro-proveedor').value;
    const estado = document.getElementById('filtro-estado').value;

    const filtrados = productos.filter(p =>
        (!texto || [p.nombre, p.sku, p.marca].some(campo => campo?.toLowerCase().includes(texto)))
        && (!categoria || String(p.categoriaId) === categoria)
        && (!proveedor || String(p.proveedorId) === proveedor)
        && (!estado || p.estadoStock === estado));

    const cuerpo = document.getElementById('tabla-productos');
    if (!filtrados.length) {
        cuerpo.innerHTML = '<tr><td colspan="11" class="text-center text-muted">No hay productos que coincidan.</td></tr>';
        return;
    }
    cuerpo.innerHTML = filtrados.map(p => {
        const e = ESTADOS[p.estadoStock];
        return `
            <tr>
                <td class="tw-font-mono">${escaparHtml(p.sku)}</td>
                <td>${escaparHtml(p.nombre)}</td>
                <td>${escaparHtml(p.marca || '-')}</td>
                <td>${escaparHtml(p.grupo ?? p.categoriaNombre ?? '-')}</td>
                <td>${escaparHtml(p.proveedor || '-')}</td>
                <td class="text-end">${formatoMoneda(p.precio)}</td>
                ${esAdmin ? `<td class="text-end">${formatoMoneda(p.costo)}</td>` : ''}
                <td class="text-end fw-semibold">${p.stockActual}</td>
                <td class="text-end">${p.stockMinimo}</td>
                <td class="${e.clase} tw-whitespace-nowrap">${e.icono} ${e.texto}</td>
                <td class="text-end tw-whitespace-nowrap">
                    <button class="btn btn-sm btn-outline-success" data-accion="ingreso" data-id="${p.id}">+ Ingreso</button>
                    ${esAdmin ? `
                        <button class="btn btn-sm btn-outline-secondary" data-accion="editar" data-id="${p.id}">Editar</button>
                        <button class="btn btn-sm btn-outline-danger" data-accion="eliminar" data-id="${p.id}">Eliminar</button>` : ''}
                </td>
            </tr>`;
    }).join('');

    cuerpo.querySelectorAll('[data-accion]').forEach(boton => boton.addEventListener('click', () => {
        const producto = productos.find(p => p.id === Number(boton.dataset.id));
        if (boton.dataset.accion === 'editar') abrirModalProducto(producto);
        if (boton.dataset.accion === 'eliminar') eliminarProducto(producto);
        if (boton.dataset.accion === 'ingreso') abrirModalIngreso(producto);
    }));
}

function abrirModalProducto(producto = null) {
    document.getElementById('titulo-modal-producto').textContent = producto ? 'Editar producto' : 'Nuevo producto';
    document.getElementById('producto-id').value = producto?.id ?? '';
    document.getElementById('producto-sku').value = producto?.sku ?? '';
    document.getElementById('producto-nombre').value = producto?.nombre ?? '';
    document.getElementById('producto-categoria').value = producto?.categoriaId ?? '';
    document.getElementById('producto-marca').value = producto?.marca ?? '';
    document.getElementById('producto-proveedor').value = producto?.proveedorId ?? '';
    document.getElementById('producto-precio').value = producto?.precio ?? '';
    document.getElementById('producto-costo').value = producto?.costo ?? '';
    document.getElementById('producto-stock').value = producto?.stockActual ?? 0;
    document.getElementById('producto-minimo').value = producto?.stockMinimo ?? STOCK_MINIMO_POR_DEFECTO;
    modalProducto.show();
}

async function guardarProducto(evento) {
    evento.preventDefault();
    const id = document.getElementById('producto-id').value;
    const categoriaId = document.getElementById('producto-categoria').value;
    const proveedorId = document.getElementById('producto-proveedor').value;
    // Se mandan siempre marca y proveedorId: core reemplaza los datos editables en el PUT (#16).
    const datos = {
        sku: document.getElementById('producto-sku').value,
        nombre: document.getElementById('producto-nombre').value,
        categoriaId: categoriaId ? Number(categoriaId) : null,
        marca: document.getElementById('producto-marca').value.trim() || null,
        proveedorId: proveedorId ? Number(proveedorId) : null,
        precio: Number(document.getElementById('producto-precio').value),
        costo: Number(document.getElementById('producto-costo').value),
        stockActual: Number(document.getElementById('producto-stock').value),
        stockMinimo: Number(document.getElementById('producto-minimo').value),
    };
    try {
        await coreApi(id ? `/productos/${id}` : '/productos', { method: id ? 'PUT' : 'POST', body: datos });
        modalProducto.hide();
        mostrarMensaje(id ? 'Producto actualizado' : 'Producto creado');
        await cargarProductos();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

async function eliminarProducto(producto) {
    if (!confirm(`¿Dar de baja "${producto.nombre}"? Su historial de ventas se conserva.`)) return;
    try {
        await coreApi(`/productos/${producto.id}`, { method: 'DELETE' });
        mostrarMensaje('Producto dado de baja');
        await cargarProductos();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

function abrirModalIngreso(producto) {
    document.getElementById('form-ingreso').reset();
    document.getElementById('ingreso-producto-id').value = producto.id;
    document.getElementById('ingreso-producto-nombre').textContent = `${producto.nombre} (stock actual: ${producto.stockActual})`;
    modalIngreso.show();
}

async function registrarIngreso(evento) {
    evento.preventDefault();
    const costo = document.getElementById('ingreso-costo').value;
    try {
        const actualizado = await coreApi('/compras', {
            method: 'POST',
            body: {
                productoId: Number(document.getElementById('ingreso-producto-id').value),
                cantidad: Number(document.getElementById('ingreso-cantidad').value),
                costoUnitario: costo ? Number(costo) : null,
                observacion: document.getElementById('ingreso-observacion').value || null,
            },
        });
        modalIngreso.hide();
        mostrarMensaje(`Ingreso registrado. ${actualizado.nombre}: stock ${actualizado.stockActual}`);
        await cargarProductos();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

if (sesion) iniciar();
