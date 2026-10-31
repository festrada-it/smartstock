// Datos de ejemplo de clientes mientras no se mergea #37 (#18).
// Respetan el contrato de docs/API-CLIENTES.md. Borrar este archivo y apagar
// CONFIG.DATOS_EJEMPLO.clientes cuando se mergee #37.

const ClientesEjemplo = (() => {
    // Copia del seed de 06_clientes.sql (#37).
    const clientes = [
        { id: 1, nombre: 'Día Express', direccion: 'Av. Corrientes 1800', telefono: '1145555000', calidad: 'BUENA', clasificacion: 5, activo: true },
        { id: 2, nombre: 'Carrefour Express', direccion: 'Av. Santa Fe 2500', telefono: '1145556000', calidad: 'BUENA', clasificacion: 4, activo: true },
        { id: 3, nombre: 'Chino Almagro', direccion: 'Av. Medrano 400', telefono: '1145557000', calidad: 'REGULAR', clasificacion: 3, activo: true },
        { id: 4, nombre: 'Jumbo Express', direccion: 'Av. Acoyte 600', telefono: '1145558000', calidad: 'BUENA', clasificacion: 4, activo: true },
        { id: 5, nombre: 'Consumidor final', direccion: null, telefono: null, calidad: 'REGULAR', clasificacion: 3, activo: true },
    ];
    let siguienteId = 6;

    const PRODUCTOS = [
        [1, 'Yerba mate 1kg', 'Playadito', 4500], [2, 'Fideos tirabuzón 500g', 'Matarazzo', 1200],
        [3, 'Arroz largo fino 1kg', 'Gallo', 1800], [5, 'Agua mineral 2L', 'Villavicencio', 900],
        [6, 'Gaseosa cola 1.5L', 'Coca-Cola', 2300], [7, 'Lavandina 1L', 'Ayudín', 950],
    ];

    // Historial determinístico: unas 18 ventas por cliente en los últimos 90 días, alguna anulada.
    const ventas = [];
    let ventaId = 1;
    for (const c of clientes) {
        for (let i = 0; i < 18; i++) {
            const diasAtras = (i * 5 + c.id) % 90;
            const fecha = new Date(Date.now() - diasAtras * 86400000);
            fecha.setHours(12, 0, 0, 0);
            const lineas = [PRODUCTOS[(i + c.id) % PRODUCTOS.length], PRODUCTOS[(i * 2 + c.id) % PRODUCTOS.length]]
                .map(([productoId, producto, marca, precio], k) => ({ productoId, producto, marca, precio, cantidad: 2 + ((i + k + c.id) % 5) }));
            ventas.push({
                id: ventaId++, clienteId: c.id, fecha: fecha.toISOString().slice(0, 19),
                total: lineas.reduce((s, l) => s + l.precio * l.cantidad, 0), anulada: i === 7, lineas,
            });
        }
    }

    const responder = valor => new Promise(resolver => setTimeout(() => resolver(structuredClone(valor)), 150));

    function buscar(id) {
        const cliente = clientes.find(c => c.id === Number(id) && c.activo);
        if (!cliente) throw new Error(`Cliente ${id} no encontrado`);
        return cliente;
    }

    function validar(datos) {
        if (!datos.nombre?.trim()) throw new Error('El nombre es obligatorio');
        if (!['BUENA', 'REGULAR', 'MALA'].includes(datos.calidad)) throw new Error('La calidad debe ser BUENA, REGULAR o MALA');
        if (!Number.isInteger(datos.clasificacion) || datos.clasificacion < 1 || datos.clasificacion > 5) {
            throw new Error('La clasificación debe ser de 1 a 5 estrellas');
        }
    }

    return {
        listar: () => responder(clientes.filter(c => c.activo).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))),
        crear: async datos => {
            validar(datos);
            const nuevo = { ...datos, nombre: datos.nombre.trim(), id: siguienteId++, activo: true };
            clientes.push(nuevo);
            return responder(nuevo);
        },
        actualizar: async (id, datos) => {
            validar(datos);
            Object.assign(buscar(id), { ...datos, nombre: datos.nombre.trim() });
            return responder(buscar(id));
        },
        eliminar: async id => { buscar(id).activo = false; return responder(null); },

        /** GET /clientes/{id}/pedidos?page=&size=: historial paginado, incluye anuladas, fecha desc. */
        pedidos: async (id, pagina = 0, tamanio = 50) => {
            buscar(id);
            const propias = ventas.filter(v => v.clienteId === Number(id))
                .sort((a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id);
            const contenido = propias.slice(pagina * tamanio, (pagina + 1) * tamanio)
                .map(({ id: vid, fecha, total, anulada }) => ({ id: vid, fecha, total, anulada }));
            return responder({ contenido, pagina, tamanio, totalElementos: propias.length, totalPaginas: Math.ceil(propias.length / tamanio) });
        },

        /** GET /clientes/{id}/productos: compras agregadas sin anuladas, unidades desc. */
        productos: async id => {
            buscar(id);
            const porProducto = new Map();
            for (const v of ventas.filter(x => x.clienteId === Number(id) && !x.anulada)) {
                for (const l of v.lineas) {
                    const actual = porProducto.get(l.productoId) ?? { productoId: l.productoId, producto: l.producto, marca: l.marca, unidadesCompradas: 0, ultimaCompra: v.fecha };
                    actual.unidadesCompradas += l.cantidad;
                    if (v.fecha > actual.ultimaCompra) actual.ultimaCompra = v.fecha;
                    porProducto.set(l.productoId, actual);
                }
            }
            return responder([...porProducto.values()].sort((a, b) => b.unidadesCompradas - a.unidadesCompradas || a.productoId - b.productoId));
        },
    };
})();
