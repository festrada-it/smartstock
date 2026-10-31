// Datos de ejemplo para la pantalla de Proveedores mientras no existe el endpoint de core-service (#14).
// Respeta el mismo contrato que /api/proveedores para que el cambio a la API real sea solo apagar
// CONFIG.DATOS_EJEMPLO.proveedores. Los cambios viven en memoria y se pierden al recargar.
// Borrar este archivo cuando se mergee #14.

const ProveedoresEjemplo = (() => {
    // Copia del seed de database/init/04_proveedores.sql (#35).
    let proveedores = [
        { id: 1, nombre: 'Distribuidora del Sur', direccion: 'Av. San Martín 1200', telefono: '1145551000', leadTimeDias: 7, calidad: 'BUENA', clasificacion: 5, activo: true },
        { id: 2, nombre: 'Bebidas del Plata', direccion: 'Av. Rivadavia 5400', telefono: '1145552000', leadTimeDias: 3, calidad: 'BUENA', clasificacion: 4, activo: true },
        { id: 3, nombre: 'Limpieza Central', direccion: 'Av. Independencia 1800', telefono: '1145553000', leadTimeDias: 10, calidad: 'REGULAR', clasificacion: 2, activo: true },
        { id: 4, nombre: 'Alimentos Frescos Norte', direccion: 'Av. Cabildo 3200', telefono: '1145554000', leadTimeDias: 2, calidad: 'MALA', clasificacion: 1, activo: true },
    ];
    let siguienteId = 5;

    // Mismo contrato que GET /api/proveedores/{id}/productos: {id, sku, nombre, marca}.
    const productosPorProveedor = {
        1: [
            { id: 1, sku: 'ALM-001', nombre: 'Yerba mate 1kg', marca: 'Playadito' },
            { id: 2, sku: 'ALM-002', nombre: 'Fideos tirabuzón 500g', marca: 'Matarazzo' },
            { id: 3, sku: 'ALM-003', nombre: 'Arroz largo fino 1kg', marca: 'Gallo' },
            { id: 4, sku: 'ALM-004', nombre: 'Harina 000 1kg', marca: 'Pureza' },
        ],
        2: [
            { id: 5, sku: 'BEB-001', nombre: 'Agua mineral 2L', marca: 'Villavicencio' },
            { id: 6, sku: 'BEB-002', nombre: 'Gaseosa cola 1.5L', marca: 'Coca-Cola' },
        ],
        3: [
            { id: 7, sku: 'LIM-001', nombre: 'Lavandina 1L', marca: 'Ayudín' },
            { id: 8, sku: 'LIM-002', nombre: 'Detergente 750ml', marca: 'Magistral' },
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
