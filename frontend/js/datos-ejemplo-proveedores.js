// Datos de ejemplo para la pantalla de Proveedores mientras no existe el endpoint de core-service (#14).
// Respeta el mismo contrato que /api/proveedores para que el cambio a la API real sea solo apagar
// CONFIG.DATOS_EJEMPLO.proveedores. Los cambios viven en memoria y se pierden al recargar.
// Borrar este archivo cuando se mergee #14.

const ProveedoresEjemplo = (() => {
    let proveedores = [
        { id: 1, nombre: 'Distribuidora Andina S.A.', direccion: 'Av. Juan B. Justo 4500, CABA', telefono: '11 4555-1200', leadTimeDias: 3, calidad: 'BUENA', clasificacion: 5, activo: true },
        { id: 2, nombre: 'Mayorista El Puente', direccion: 'Ruta 8 km 52, Pilar', telefono: '230 442-8800', leadTimeDias: 7, calidad: 'REGULAR', clasificacion: 3, activo: true },
        { id: 3, nombre: 'Bebidas del Oeste SRL', direccion: 'Av. Rivadavia 22100, Ituzaingó', telefono: '11 4623-0450', leadTimeDias: 4, calidad: 'BUENA', clasificacion: 4, activo: true },
        { id: 4, nombre: 'Química Sur', direccion: 'Hipólito Yrigoyen 3300, Lanús', telefono: '11 4241-7788', leadTimeDias: 12, calidad: 'MALA', clasificacion: 2, activo: true },
    ];
    let siguienteId = 5;

    const productosPorProveedor = {
        1: [
            { id: 1, sku: 'ALM-001', nombre: 'Yerba mate 1kg', marca: 'Playadito', categoriaNombre: 'Almacén', stockActual: 120, stockMinimo: 30 },
            { id: 3, sku: 'ALM-003', nombre: 'Arroz largo fino 1kg', marca: 'Gallo', categoriaNombre: 'Almacén', stockActual: 60, stockMinimo: 30 },
        ],
        2: [
            { id: 2, sku: 'ALM-002', nombre: 'Fideos tirabuzón 500g', marca: 'Matarazzo', categoriaNombre: 'Almacén', stockActual: 35, stockMinimo: 30 },
            { id: 4, sku: 'ALM-004', nombre: 'Harina 000 1kg', marca: 'Pureza', categoriaNombre: 'Almacén', stockActual: 18, stockMinimo: 30 },
        ],
        3: [
            { id: 5, sku: 'BEB-001', nombre: 'Agua mineral 2L', marca: 'Villavicencio', categoriaNombre: 'Bebidas', stockActual: 25, stockMinimo: 30 },
            { id: 6, sku: 'BEB-002', nombre: 'Gaseosa cola 1.5L', marca: 'Coca-Cola', categoriaNombre: 'Bebidas', stockActual: 80, stockMinimo: 30 },
        ],
        4: [
            { id: 7, sku: 'LIM-001', nombre: 'Lavandina 1L', marca: 'Ayudín', categoriaNombre: 'Limpieza', stockActual: 40, stockMinimo: 30 },
            { id: 8, sku: 'LIM-002', nombre: 'Detergente 750ml', marca: 'Magistral', categoriaNombre: 'Limpieza', stockActual: 12, stockMinimo: 30 },
        ],
    };

    // Simula la latencia de red y devuelve copias, como haría la API.
    const responder = valor => new Promise(resolver => setTimeout(() => resolver(structuredClone(valor)), 150));

    function buscar(id) {
        const proveedor = proveedores.find(p => p.id === Number(id) && p.activo);
        if (!proveedor) throw new Error(`Proveedor ${id} no encontrado`);
        return proveedor;
    }

    // Mismas validaciones que se esperan de core-service (400 con mensaje en español).
    function validar(datos) {
        if (!datos.nombre?.trim()) throw new Error('El nombre es obligatorio');
        if (!Number.isInteger(datos.leadTimeDias) || datos.leadTimeDias <= 0) throw new Error('Los días de entrega deben ser un entero mayor a 0');
        if (!['BUENA', 'REGULAR', 'MALA'].includes(datos.calidad)) throw new Error('La calidad debe ser BUENA, REGULAR o MALA');
        if (!Number.isInteger(datos.clasificacion) || datos.clasificacion < 1 || datos.clasificacion > 5) throw new Error('La clasificación debe ser de 1 a 5 estrellas');
    }

    return {
        listar: () => responder(proveedores.filter(p => p.activo)),
        productos: async id => { buscar(id); return responder(productosPorProveedor[id] ?? []); },
        crear: async datos => {
            validar(datos);
            const nuevo = { ...datos, id: siguienteId++, activo: true };
            proveedores.push(nuevo);
            return responder(nuevo);
        },
        actualizar: async (id, datos) => {
            validar(datos);
            Object.assign(buscar(id), datos);
            return responder(buscar(id));
        },
        eliminar: async id => { buscar(id).activo = false; return responder(null); },
    };
})();
